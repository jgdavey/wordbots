(defproject wordbots "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.generators "0.1.2"]
                 [com.joshuadavey/vecset "0.2.0"]
                 [compojure "1.5.0"]
                 [instaparse "1.4.1" :exclusions [org.clojure/clojure]]
                 [image-resizer  "0.1.9"]
                 [clj-http "2.1.0"]
                 [clj-tuple "0.2.2"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]]
  :plugins [[lein-ring "0.9.7" :exclusions [org.clojure/clojure]]]
  :ring {:init wordbots.handler/init
         :handler wordbots.handler/app}
  :profiles {:uberjar {:aot :all}}
)
