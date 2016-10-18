(defproject de.otto/machroput "1.0.1"
  :description "A simple marathon and chronos api written in clojure"
  :url "https://github.com/otto-de/machroput.git"
  :dependencies [[http-kit "2.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojure "1.8.0"]]
  :plugins [[lein-ancient "0.6.10"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
