# MonkeyCI JUnit Plugin

This is a Clojure library that provides plugin functionality for [MonkeyCI](https://monkeyci.com)
to allow build jobs to extract information from JUnit test results so they can be added to the
build results.  The plugin reads a `junit.xml` compatible file from a configured artifact and
parses the information.

## Usage

First include the library in your build `deps.edn`:

```clojure
{:deps {com.monkeyci/plugin-junit {:mvn/version "VERSION"}}}
```

Then make sure you `require` it in your build script.  It registers itself on a tag
named `junit`, which should contain the necessary configuration.  For example:

```clojure
(require '[monkey.ci.ext.junit])
(require '[monkey.ci.build.core :as bc])

;; Some build job
(def test-job
  (bc/action-job
    "test-job"
    (fn [ctx]
      ;; Test functionality goes here
      )
    {:save-artifacts [{:id "test-results"
                       :path "junit.xml"}]
     ;; Configuration for the plugin
     :junit {:artifact-id "test-results"
             :path "junit.xml"}}))

;; Jobs in your build script
[test-job]
```

The plugin will read the artifact with id `test-results` and extract the `junit.xml` file
from it, parsing it as xml.  The information is added to the build job results under the
key `monkey.ci/tests`.

## License

Copyright (c) 2024 by [Monkey Projects](https://www.monkey-projects.be).
[MIT License](LICENSE)
