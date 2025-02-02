(defproject ai-demo "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]]
  :main ^:skip-aot ai-demo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
