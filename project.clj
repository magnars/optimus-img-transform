(defproject optimus-img-transform "0.3.0"
  :description "An Optimus image transformation middleware."
  :url "http://github.com/magnars/optimus-img-transform"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [fivetonine/collage "0.2.0"]
                 [optimus "0.14.1"]]
  :jvm-opts ["-Djava.awt.headless=true"]
  :profiles {:dev {:dependencies [[midje "1.6.0"]
                                  [test-with-files "0.1.0"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :resource-paths ["test/resources"]}})
