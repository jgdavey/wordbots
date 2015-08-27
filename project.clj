(defproject wordbots "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.generators "0.1.2"]
                 [com.joshuadavey/vecset "0.2.0"]
                 [compojure  "1.3.1"]
                 [ring/ring-json "0.3.1"]]
  :plugins [[lein-ring "0.8.13" :exclusions [org.clojure/clojure]]]
  :ring {:init wordbots.handler/init
         :handler wordbots.handler/app}
  :profiles {:uberjar {:aot :all}}
)
