(ns monkey.ci.ext.junit-test
  (:require [clojure.test :refer [deftest testing is]]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [monkey.ci.ext.junit :as sut]
            [monkey.ci.api :as m]
            [monkey.ci.extensions :as ext])
  (:import [java.io ByteArrayInputStream]))

(deftest parse-xml
  (testing "`nil` if no xml"
    (is (nil? (sut/parse-xml nil))))

  (testing "parses empty test suites"
    (is (empty? (sut/parse-xml "<testsuites></testsuites>"))))

  (testing "single test suite"
    (is (= [{:name "single suite"
             :test-cases
             [{:test-case "single-test"
               :class-name "test.class"}]}]
           (sut/parse-xml
            "<testsuites>
              <testsuite name=\"single suite\">
                <testcase name=\"single-test\" classname=\"test.class\"></testcase>
              </testsuite>
            </testsuites>"))))

  (testing "parses elapsed time"
    (is (= 0.045
           (-> (sut/parse-xml
                "<testsuites>
                  <testsuite name=\"single suite\">
                    <testcase name=\"single-test\" classname=\"test.class\" time=\"0.045\"></testcase>
                  </testsuite>
                </testsuites>")
               first
               :test-cases
               first
               :time))))

  (testing "ignores unsupported tags"
    (is (= [{:test-case "single-test"}]
           (-> (sut/parse-xml
                "<testsuites>
                  <testsuite name=\"single suite\">
                    <testcase name=\"single-test\">
                      <properties>
                      </properties>
                    </testcase>
                  </testsuite>
                </testsuites>")
               first
               :test-cases))))

  (testing "test suite with failing case"
    (is (= [{:test-case "single-test"
             :failures [{:message "test failure"
                         :type "TestType"
                         :description "Short test error description"}]}]
           (-> (sut/parse-xml
                "<testsuites>
                  <testsuite name=\"single suite\">
                    <testcase name=\"single-test\">
                      <failure message=\"test failure\" type=\"TestType\">
                        <![CDATA[Short test error description]]>
                      </failure>
                    </testcase>
                  </testsuite>
                </testsuites>")
               first
               :test-cases))))

  (testing "test suite with error case"
    (is (= [{:test-case "single-test"
             :errors [{:message "test error"
                       :type "TestType"
                       :description "Short test error description"}]}]
           (-> (sut/parse-xml
                "<testsuites>
                  <testsuite name=\"single suite\">
                    <testcase name=\"single-test\">
                      <error message=\"test error\" type=\"TestType\">
                        <![CDATA[Short test error description]]>
                      </error>
                    </testcase>
                  </testsuite>
                </testsuites>")
               first
               :test-cases)))))

(defn- gen-single-suite-xml [suite cases]
  (letfn [(gen-case [c]
            (format "<testcase name=\"%s\" classname=\"%s\"></testcase>" c c))]
    (format
     "<testsuite name=\"%s\">
        %s
      </testsuite>"
     suite
     (->> cases
          (map gen-case)
          (cs/join "\n")))))

(defn- gen-multi-suite-xml [suite cases]
  (str "<testsuites>" (gen-single-suite-xml suite cases) "</testsuites>"))

(defn- gen-results
  "Writes generated junit files into `dest` dir."
  [dest files]
  (doseq [[path contents] files]
    (spit (fs/file (fs/path dest path)) contents))
  dest)

(deftest after-job
  (testing "for single file"
    (fs/with-temp-dir [dir]
      (let [rt {:build {:sid ["test-org" "test-repo" "test-build"]}
                :job {:junit {:artifact-id "test-results"
                              :path "junit.xml"}
                      :save-artifacts [{:id "test-results"
                                        :path "junit.xml"}]
                      :work-dir (str dir)}}]
        (with-redefs [m/get-artifact (fn [_ {:keys [id]}]
                                       (when (= id "test-results")
                                         (str (fs/path (io/resource "junit.xml")))))]
          (testing "sets parsed xml results in the job result"
            (is (not-empty (-> (ext/after-job :junit rt)
                               :job
                               :result
                               :monkey.ci/tests))))))))

  (testing "for file restored to dir"
    (fs/with-temp-dir [dir]
      (let [rt {:build {:sid ["test-org" "test-repo" "test-build"]}
                :job {:junit {:artifact-id "test-results"
                              :path "junit.xml"}
                      :save-artifacts [{:id "test-results"
                                        :path "junit.xml"}]}}]
        (with-redefs [m/get-artifact (fn [_ {:keys [id]}]
                                       (when (= id "test-results")
                                         (io/copy (fs/file (io/resource "junit.xml")) (fs/file dir "junit.xml"))
                                         (str dir)))]
          (testing "sets parsed xml results in the job result"
            (is (not-empty (-> (ext/after-job :junit rt)
                               :job
                               :result
                               :monkey.ci/tests))))))))

  (testing "for multiple files"
    (testing "that each contain multiple suites"
      (fs/with-temp-dir [dir]
        (let [rt {:build {:sid ["test-org" "test-repo" "test-build"]}
                  :job {:junit {:artifact-id "test-results"
                                :pattern #"file-.*\.xml"}
                        :save-artifacts [{:id "test-results"
                                          :path "target/"}]
                        :work-dir dir}}
              arch (gen-results
                    (fs/create-dirs (fs/path dir "tests"))
                    [["file-1.xml" (gen-multi-suite-xml "test-suite-1" ["case-1" "case-2"])]
                     ["file-2.xml" (gen-multi-suite-xml "test-suite-2" ["case-3" "case-4"])]])]
          (is (fs/exists? arch))
          (with-redefs [m/get-artifact (fn [_ {:keys [id]}]
                                         (when (= id "test-results")
                                           (fs/file arch)))]
            (testing "sets parsed xml results in the job result"
              (let [r (-> (ext/after-job :junit rt)
                          :job
                          :result
                          :monkey.ci/tests)]
                (is (not-empty r))
                (is (= 2 (count r)))
                (is (= 2 (-> r
                             first
                             :test-cases
                             count)))))))))

    (testing "that each contain a single suite"
      (fs/with-temp-dir [dir]
        (let [rt {:build {:sid ["test-org" "test-repo" "test-build"]}
                  :job {:junit {:artifact-id "test-results"
                                :pattern #"file-.*\.xml"}
                        :save-artifacts [{:id "test-results"
                                          :path "target/"}]}}
              arch (gen-results
                    (fs/create-dirs (fs/path dir "tests"))
                    [["file-1.xml" (gen-single-suite-xml "test-suite-1" ["case-1" "case-2"])]
                     ["file-2.xml" (gen-single-suite-xml "test-suite-2" ["case-3" "case-4"])]])]
          (with-redefs [m/get-artifact (fn [_ {:keys [id]}]
                                         (when (= id "test-results")
                                           (fs/file arch)))]
            (testing "sets parsed xml results in the job result"
              (let [r (-> (ext/after-job :junit rt)
                          :job
                          :result
                          :monkey.ci/tests)]
                (is (not-empty r))
                (is (= 2 (count r)))
                (is (= 2 (-> r
                             first
                             :test-cases
                             count)))))))))))

(deftest artifact
  (testing "creates artifact structure for junit"
    (is (= {:artifact-id "test-art"
            :path "test/path"}
           (sut/artifact "test-art" "test/path")))))

(deftest junit
  (testing "sets and gets junit config on the job"
    (let [job (m/action-job "test-job" (constantly nil))
          art (sut/artifact "test-art" "junit.xml")]
      (is (= art (-> job
                     (sut/junit art)
                     (sut/junit)))))))

(deftest read-artifact
  (testing "configures junit with given artifact"
    (is (= (sut/artifact "test-id" "junit.xml")
           (-> (m/container-job "test-job")
               (sut/read-artifact "test-id")
               (sut/junit))))))
