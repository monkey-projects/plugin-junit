(ns monkey.ci.ext.junit
  "Main namespace for the junit extension.  It can be used to read the
   contents of the `junit.xml` test result file, and converts it to a
   format that can be used in MonkeyCI job results."
  (:require [clojure.xml :as xml]
            [monkey.ci.extensions :as e]))

(defn- select-attrs [el attr-map]
  (reduce-kv (fn [r k v]
               (let [p (get-in el [:attrs k])]
                 (cond-> r
                   p (assoc v p))))
             {}
             attr-map))

(defmulti handle-tag :tag)

(defmethod handle-tag :testsuites [el]
  (->> (mapcat handle-tag (:content el))
       (remove nil?)))

(defmethod handle-tag :testsuite [el]
  (->> (map handle-tag (:content el))
       (remove nil?)))

(defmethod handle-tag :testcase [el]
  (select-attrs el {:name :test-case
                    :classname :class-name}))

(defmethod handle-tag :default [_]
  nil)

(defn parse-xml [xml]
  (when xml
    (with-open [is (java.io.ByteArrayInputStream. (.getBytes xml))]
      (-> (xml/parse is)
          (handle-tag)))))

(defmethod e/after-job :junit [_ rt]
  (parse-xml rt))
