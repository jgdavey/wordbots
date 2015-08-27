(ns wordbots.handler
  (:require [wordbots.steambot :as steambot]
            [wordbots.madbot :as madbot]
            [wordbots.fightbot :as fightbot]
            [wordbots.startupbot :as startupbot]
            [ring.middleware.json :refer [wrap-json-response]]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as r :refer [response]]))

(defn init []
  (madbot/init)
  (steambot/init)
  (startupbot/init)
  (fightbot/init))

(defn steam []
  (steambot/generate))

(defn mad []
  (madbot/generate))

(defn fight []
  (fightbot/generate))

(defn startup []
  (startupbot/generate))

(defn json [f]
  (wrap-json-response (fn [req] (response {:text (f)}))))

(defroutes app
  (GET "/" [] (steam))
  (POST "/" [] (json steam))
  (GET "/steambot" [] (steam))
  (POST "/steambot" [] (json steam))
  (GET "/madbot" [] (mad))
  (POST "/madbot" [] (json mad))
  (GET "/startup" [] (startup))
  (POST "/startup" [] (json startup))
  (GET "/fight" [] (fight))
  (POST "/fight" [] (json fight)))
