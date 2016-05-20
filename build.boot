(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [org.clojure/data.generators "0.1.2"]
                  [org.clojure/core.async "0.2.374"]
                  [org.clojure/tools.logging "0.3.1"]
                  [org.slf4j/slf4j-simple "1.7.21"]
                  [com.joshuadavey/vecset "0.2.0"]
                  [compojure "1.5.0"]
                  [instaparse "1.4.2" :exclusions [org.clojure/clojure]]
                  [image-resizer  "0.1.9"]
                  [clj-http "2.1.0"]
                  [clj-tuple "0.2.2"]
                  [ring/ring-jetty-adapter "1.4.0"]
                  [ring/ring-json "0.4.0"]])

(task-options!
  pom {:project 'wordbots
       :version "0.0.1"
       :description "Just some wordbots"}
  aot {:all true}
  jar {:main 'wordbots.handler
       :file "app.jar"}
  uber {:exclude #{#"(?i)^META-INF/INDEX.LIST$"
                   #"(?i)^META-INF/[^/]*\.(MF|SF|RSA|DSA)$"
                   #"(?i)^META-INF/LICENSE$"
                   #"(?i)^LICENSE$"}})

(deftask build []
  (comp
    (aot :all true)
    (uber)
    (pom)
    (jar :main 'wordbots.handler)
    (target :dir #{"build"})))
