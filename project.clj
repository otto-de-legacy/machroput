(defproject de.otto/machroput "1.0.4"
  :description "A simple marathon and chronos api written in clojure"
  :license {:name "Apache License 2.0" :url  "http://www.apache.org/license/LICENSE-2.0.html"}

  :url "https://github.com/otto-de/machroput.git"
  :dependencies [[http-kit "2.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojure "1.8.0"]]
  :lein-release {:deploy-via :clojars}
  :plugins [[lein-ancient "0.6.10"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-release/lein-release "1.0.9"]]}})
