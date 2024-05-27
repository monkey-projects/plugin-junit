(ns monkey.ci.ext.junit-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [monkey.ci.ext.junit :as sut]
            [monkey.ci.build.api :as api]
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
    (is (= 1.0
           (-> (sut/parse-xml
                "<testsuites>
                  <testsuite name=\"single suite\">
                    <testcase name=\"single-test\" classname=\"test.class\" time=\"1\"></testcase>
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
    
(deftest after-job
  (let [rt {:build {:sid ["test-cust" "test-repo" "test-build"]}
            :job {:junit {:artifact-id "test-results"
                          :path "junit.xml"}
                  :save-artifacts [{:id "test-results"
                                    :path "junit.xml"}]}}]
    (with-redefs [api/download-artifact (fn [_ id]
                                          (when (= id "test-results")
                                            (io/input-stream (io/resource "test-results.tgz"))))]
      (testing "sets parsed xml results in the job result"
        (is (not-empty (-> (ext/after-job :junit rt)
                           :job
                           :result
                           :monkey.ci/tests)))))))
