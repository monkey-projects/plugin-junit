(ns monkey.ci.ext.junit-test
  (:require [monkey.ci.ext.junit :as sut]
            [clojure.test :refer [deftest testing is]]))

(deftest parse-xml
  (testing "`nil` if no xml"
    (is (nil? (sut/parse-xml nil))))

  (testing "parses empty test suites"
    (is (empty? (sut/parse-xml "<testsuites></testsuites>"))))

  (testing "single test suite"
    (is (= [{:test-case "single-test"}]
           (sut/parse-xml
            "<testsuites>
              <testsuite name=\"single suite\">
                <testcase name=\"single-test\"></testcase>
              </testsuite>
            </testsuites>")))))
