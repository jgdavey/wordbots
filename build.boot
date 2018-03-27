(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.9.0" :scope "provided"]
                 [clj-http "3.7.0"]
                 [com.joshuadavey/vecset "0.2.0"]
                 [compojure "1.6.0" :exclusions [ring/* instaparse]]
                 [ring/ring-core "1.6.3"]
                 [image-resizer "0.1.10"]
                 [instaparse "1.4.8" :exclusions [org.clojure/clojure]]
                 [org.clojure/core.async "0.3.465"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.slf4j/slf4j-simple "1.7.6"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-json "0.4.0" :exclusions [ring/* cheshire]]
                 [cheshire "5.8.0"]
                 [com.clojure-goes-fast/clj-memory-meter "0.1.0"]
                 [org.clojure/data.generators "0.1.2"]
                 [deraen/boot-ctn "0.1.0" :scope "test"]])

(require 'deraen.boot-ctn)
(deraen.boot-ctn/init-ctn!)

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

(deftask package []
  (comp
    (aot :all true)
    (uber)
    (pom)
    (jar :main 'wordbots.handler)
    (sift :include #{ #"^app\.jar$" })
    (target :dir #{"build"})))
