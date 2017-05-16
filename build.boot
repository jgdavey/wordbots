(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha16"]
                 [org.clojure/spec.alpha "0.1.108"]
                 [clj-http "3.5.0"]
                 [clj-tuple "0.2.2"]
                 [com.joshuadavey/vecset "0.2.0"]
                 [compojure "1.6.0" :exclusions [ring/* instaparse]]
                 [ring/ring-core "1.6.1"]
                 [image-resizer "0.1.9"]
                 [instaparse "1.4.5" :exclusions [org.clojure/clojure]]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [ring/ring-jetty-adapter "1.6.1"]
                 [ring/ring-json "0.4.0" :exclusions [ring/* cheshire]]
                 [cheshire "5.7.1"]
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

(deftask build []
  (comp
    (aot :all true)
    (uber)
    (pom)
    (jar :main 'wordbots.handler)
    (sift :include #{ #"^app\.jar$" })
    (target :dir #{"build"})))
