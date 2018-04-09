(defproject sca "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-http "3.8.0"]
                 [reaver "0.1.2"]]
  :main ^:skip-aot sca.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
