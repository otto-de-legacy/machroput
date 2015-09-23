(defproject de.otto/machroput "0.0.2"
  :description "A simple marathon and chronos api written in clojure"
  :url "https://github.com/otto-de/machroput.git"
  :dependencies [[http-kit "2.1.19"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojure "1.7.0"]]
  :plugins [[lein-ancient "0.6.7"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
