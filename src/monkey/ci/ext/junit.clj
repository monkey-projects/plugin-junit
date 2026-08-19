(ns monkey.ci.ext.junit
  "Main namespace for the junit extension.  It can be used to read the
   contents of the `junit.xml` test result file, and converts it to a
   format that can be used in MonkeyCI job results."
  (:require [babashka.fs :as fs]
            [clojure.data.xml :as xml]
            [clojure.tools.logging :as log]
            [medley.core :as mc]
            [monkey.ci
             [api :as api]
             [extensions :as e]]))

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

(defprotocol AsPattern
  (->re-pattern [x]))

(extend-protocol AsPattern
  java.lang.String
  (->re-pattern [x]
    (re-pattern x))

  java.util.regex.Pattern
  (->re-pattern [x]
    x))

(defn- read-files [dir pattern]
  (->> (fs/list-dir dir (fn [p]
                          (re-matches pattern (str (fs/relativize dir p)))))
       (map (comp slurp fs/file))))

(defn- junit-file
  "Archives can be a directory, where the files have been extracted to.  In that case, combine
   it with the archive path."
  [f path]
  (cond-> f
    (fs/directory? f)
    (fs/path path)))

(defmethod e/after-job :junit [_ rt]
  (let [{:keys [id artifact-id path pattern]} (e/get-config rt :junit)
        tmp (fs/create-temp-dir {:dir (api/job-work-dir rt)})
        xmls (when-let [arch (some-> (or id artifact-id)
                                     (api/artifact (str tmp))
                                     (as-> v (api/get-artifact rt v)))]
               (cond-> arch
                 path (some-> (junit-file path)
                              (fs/file)
                              (slurp)
                              (vector))
                 pattern (read-files (->re-pattern pattern))))]
    (when (empty? xmls)
      (log/warnf "Junit XML artifact '%s' not found or no matching files found, test results will not be added to build.  Path/pattern: %s" artifact-id (or path pattern)))
    (e/set-value rt :monkey.ci/tests (parse-xmls xmls))))

(defn artifact
  "Creates an artifact definition that can be configured on the job that outputs 
   junit results, but you can also use `monkey.ci.api/artifact`."
  [id path]
  {:artifact-id id
   :path path})

(defn junit
  "Gets or sets the junit artifact on the job"
  ([job]
   (:junit job))
  ([job art]
   (assoc job :junit art)))

(defn read-artifact
  "Convenience function that configures the junit extension to read the test results from
   the given artifact.  If no path is given, `junit.xml` is assumed."
  [job id & [path]]
  (junit job (artifact id (or path "junit.xml"))))
