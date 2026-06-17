(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             #_[github :as gh]]))

#_[(gh/release-job {:dependencies ["publish"]})]
(p/deps-library)
