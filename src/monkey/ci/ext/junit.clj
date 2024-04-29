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

(defn- select-content [tag content]
  (filter (comp (partial = tag) :tag) content))

(defn- assoc-not-empty [m k v]
  (cond-> m
    (not-empty v) (assoc k v)))

(defmulti handle-tag :tag)

(defmethod handle-tag :testsuites [el]
  (->> (map handle-tag (:content el))
       (remove nil?)))

(defmethod handle-tag :testsuite [el]
  (-> (:attrs el)
      (assoc :test-cases (->> (map handle-tag (:content el))
                              (remove nil?)))))

(defmethod handle-tag :testcase [el]
  (-> (select-attrs el {:name :test-case
                        :classname :class-name})
      (assoc-not-empty :failures
                       (->> (:content el)
                            (select-content :failure)
                            (map handle-tag)))
      (assoc-not-empty :errors
                       (->> (:content el)
                            (select-content :error)
                            (map handle-tag)))))

(defn- handle-error [el]
  (-> (select-attrs el {:message :message
                        :type :type})
      (assoc :description (some-> (apply str (:content el))
                                  (.trim)))))

(defmethod handle-tag :failure [el]
  (handle-error el))

(defmethod handle-tag :error [el]
  (handle-error el))

(defmethod handle-tag :default [_]
  nil)

(defn parse-xml [xml]
  (when xml
    (with-open [is (java.io.ByteArrayInputStream. (.getBytes xml))]
      (-> (xml/parse is)
          (handle-tag)))))

(defmethod e/after-job :junit [_ rt]
  (parse-xml rt))
