# MonkeyCI JUnit Plugin

This is a Clojure library that provides plugin functionality for [MonkeyCI](https://monkeyci.com)
to allow build jobs to extract information from JUnit test results so they can be added to the
build results.  The plugin reads a `junit.xml` compatible file from a configured artifact and
parses the information.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/com.monkeyci/plugin-clj.svg)](https://clojars.org/com.monkeyci/plugin-clj)

First include the library in your build `deps.edn`:

```clojure
{:deps {com.monkeyci/plugin-junit {:mvn/version "VERSION"}}}
```

Then make sure you `require` it in your build script.  It registers itself on a tag
named `junit`, which should contain the necessary configuration.  For example:

```clojure
(ns build
  (:require [monkey.ci.ext.junit :as j]
            [monkey.ci.api :as m]))

;; Some build job
(def test-job
  (-> (m/container-job "run-tests")
      (m/image "docker.io/clojure")
      (m/script ["lein test-junit"])
      (m/save-artifacts [(m/artifact "test-results" "junit.xml")])
      ;; Configuration for the plugin
      ;; Alternatively, you can use {:artifact-id "test-results" :path "junit.xml"}
      (j/junit (m/artifact "test-results" "junit.xml"))))

;; Jobs in your build script
[test-job]
```

The plugin will read the artifact with id `test-results` and extract the `junit.xml` file
from it, parsing it as xml.  The information is added to the build job results under the
key `monkey.ci/tests`.

### Multiple Files

Many test tools, such as [Apache Maven](https://maven.apache.org/) will generate multiple
test result files in a directory.  It's possible to specify a `pattern` instead of a `path`
for these situations.  The extension will then look up all files matching the pattern in the
archive, and consolidate all extracted test results.

```clojure
;; Some build job
(def test-job
  (-> (m/container-job "run-tests")
      (m/image "docker.io/maven:latest")
      (m/script ["mvn verify"])
      (m/save-artifacts [(m/artifact "test-results" "target/surefire-reports")])
      ;; Configuration for the plugin
      (j/junit {:artifact-id "test-results"
                ;; Use regex pattern instead of path
                :pattern #"surefire-reports/.*.xml"})))
```

## License

Copyright (c) 2024-2025 by [Monkey Projects](https://www.monkey-projects.be).

[MIT License](LICENSE)
