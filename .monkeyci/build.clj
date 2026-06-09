(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             #_[github :as gh]]))

[(p/deps-library)
 #_(gh/release-job {:dependencies ["publish"]})]
