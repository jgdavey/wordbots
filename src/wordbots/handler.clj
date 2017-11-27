(ns wordbots.handler
  (:gen-class)
  (:require [wordbots.protocols :as p]
            [wordbots.steambot :as steambot]
            [wordbots.madbot :as madbot]
            [wordbots.fightbot :as fightbot]
            [wordbots.startupbot :as startupbot]
            [wordbots.memebot :as memebot]
            [wordbots.plotbot :as plotbot]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.tools.logging :as log]
            [clout.core :as clout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as r :refer [response]]
            [clojure.string :as str]))

(def image-root "/tmp/images")
(.mkdirs (java.io.File. image-root))

(def bots
  [{:id :steambot
    :bot (steambot/bot)
    :paths ["/steam" "/steambot"]}
   {:id :madbot
    :bot (madbot/bot)
    :paths ["/madbot" "/wisdom"]}
   {:id :startupbot
    :bot (startupbot/bot)
    :paths["/startup" "/startupbot"]}
   {:id :fightbot
    :bot (fightbot/bot)
    :paths ["/fight" "/fightbot"]}
   {:id :memebot
    :bot (memebot/startup-image-bot image-root)
    :paths ["/startup-image" "/killer-idea"]}
   {:id :plotbot
    :bot (plotbot/bot)
    :paths ["/plot" "/movie-idea"]}])

(def ^:private routes
  (reduce (fn [all {:keys [bot paths]}]
            (into all (map vector
                         (map clout/route-compile paths)
                         (repeat bot))))
          [] bots))

(defn init []
  (doseq [bot bots]
    (log/info "Initializing" (:id bot))
    (p/init (:bot bot))))

(defn- find-bot [req]
  (some (fn [[route bot]]
          (when (clout/route-matches route req)
            bot))
        routes))

(defn html-response [resp]
  (try
    (let [uri (java.net.URI. (:body resp))]
      (if (memebot/image? uri)
        (-> resp
          (assoc-in [:headers "Content-Type"] "text/html; charset=utf-8")
          (assoc :body (str "<img src='" uri "' />")))
        resp))
    (catch Throwable t
      resp)))

(defn wrap-response [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (and (= (:status resp 200))
               (= :post (:request-method req)))
        (response {:text (:body resp)})
        (html-response resp)))))

(defn generate [req]
  (if (= "/" (:uri req))
    (response (str "Bots: " (str/join ", " (->> bots (map (comp first :paths))))))
    (if-let [bot (find-bot req)]
      (response (p/generate bot req))
      (or (r/file-response (:uri req) {:root image-root})
          (r/not-found "No bot")))))

(def app
  (-> generate
      wrap-params
      wrap-response
      wrap-json-response))

(defn -main []
  (init)
  (log/info "Starting jetty on port 4000")
  (run-jetty #'app {:port 4000}))

(comment

(init)
(def ^:once server (atom nil))
(reset! server (run-jetty #'app {:port 4000 :join? false}))
(.stop @server)

)
