(ns wordbots.handler
  (:require [wordbots.protocols :as p]
            [wordbots.steambot :as steambot]
            [wordbots.madbot :as madbot]
            [wordbots.fightbot :as fightbot]
            [wordbots.startupbot :as startupbot]
            [wordbots.memebot :as memebot]
            [ring.middleware.json :refer [wrap-json-response]]
            [clout.core :as clout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as r :refer [response]]))

(def image-root "/tmp/images")
(.mkdirs (java.io.File. image-root))

(def bots
  {(steambot/bot)   ["/" "/steam" "/steambot"]
   (madbot/bot)     ["/madbot" "/wisdom"]
   (startupbot/bot) ["/startup" "/startupbot"]
   (fightbot/bot)   ["/fight" "/fightbot"]
   (memebot/highbot image-root) ["/high" "/highbot"]
   })

(def ^:private routes
  (reduce-kv (fn [all bot paths]
            (into all (map vector
                         (map clout/route-compile paths)
                         (repeat bot))))
          [] bots))

(defn init []
  (doseq [bot (keys bots)]
    (p/init bot)))

(defn- find-bot [req]
  (some (fn [[route bot]]
          (when (clout/route-matches route req)
            bot))
        routes))

(defn wrap-response [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (and (= (:status resp 200))
               (= :post (:request-method req)))
        (response {:text (:body resp)})
        resp))))

(defn generate [req]
  (if-let [bot (find-bot req)]
    (response (p/generate bot req))
    (or (r/file-response (:uri req) {:root image-root})
        (r/not-found "No bot"))))

(def app
  (-> generate
      wrap-response
      wrap-json-response))

(comment

(clojure.pprint/pprint routes)
(init)

; start server
(require 'ring.adapter.jetty)
(def server
  (ring.adapter.jetty/run-jetty #'app {:port 8000 :join? false}))

; stop server
(.stop server)

)
