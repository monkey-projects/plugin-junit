(ns monkey.ci.ext.junit
  "Main namespace for the junit extension.  It can be used to read the
   contents of the `junit.xml` test result file, and converts it to a
   format that can be used in MonkeyCI job results."
  (:require [clojure.tools.logging :as log]
            [clojure.xml :as xml]
            [diehard.core :as dh]
            [medley.core :as mc]
            [monkey.ci.build
             [api :as api]
             [archive :as arch]]
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
      (mc/update-existing :time parse-double)
      (assoc :test-cases (->> (map handle-tag (:content el))
                              (remove nil?)))))

(defmethod handle-tag :testcase [el]
  (-> (select-attrs el {:name :test-case
                        :classname :class-name
                        :time :time})
      (mc/update-existing :time parse-double)
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
  (letfn [(normalize [r]
            (cond-> r
              (map? r) vector))]
    (when xml
      (with-open [is (java.io.ByteArrayInputStream. (.getBytes xml))]
        (-> (xml/parse is)
            (handle-tag)
            (normalize))))))

(defn parse-xmls
  "Parses and consolidates multiple xml files"
  [xmls]
  (mapcat parse-xml xmls))

(defn- download-artifact [artifact-id rt]
  ;; It may occur that the artifact in question is not available yet.  So
  ;; retry a few times.
  (dh/with-retry {:retry-on Exception
                  :max-retries 5
                  :delay-ms 1000
                  :on-retry (fn [_ ex]
                              (log/warnf "Unable to download artifact (%s), retrying..." (ex-message ex)))}
    (api/download-artifact rt artifact-id)))

(defprotocol AsPattern
  (->re-pattern [x]))

(extend-protocol AsPattern
  java.lang.String
  (->re-pattern [x]
    (re-pattern x))

  java.util.regex.Pattern
  (->re-pattern [x]
    x))

(defmethod e/after-job :junit [_ rt]
  (let [{:keys [id artifact-id path pattern]} (e/get-config rt :junit)
        xmls (when-let [arch (some-> (or id artifact-id)
                                     (download-artifact rt))]
               (cond-> arch
                 path (some-> (arch/extract+read path)
                              (vector))
                 pattern (arch/extract+read-all (->re-pattern pattern))))]
    (when (empty? xmls)
      (log/warnf "Junit XML artifact '%s' not found or no matching files found, test results will not be added to build.  Path/pattern: %s" artifact-id (or path pattern)))
    (e/set-value rt :monkey.ci/tests (parse-xmls xmls))))

(defn artifact
  "Creates an artifact definition that can be configured on the job that outputs junit results."
  [id path]
  {:artifact-id id
   :path path})

(defn junit
  "Gets or sets the junit artifact on the job"
  ([job]
   (:junit job))
  ([job art]
   (assoc job :junit art)))
