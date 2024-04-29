(ns monkey.ci.ext.junit-test
  (:require [monkey.ci.ext.junit :as sut]
            [clojure.test :refer [deftest testing is]]))

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

  (testing "test suite with failing case"
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
    
