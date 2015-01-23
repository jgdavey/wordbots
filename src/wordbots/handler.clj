(ns wordbots.handler
  (:require [wordbots.steambot :as steambot]
            [ring.middleware.json :refer [wrap-json-response]]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as r :refer [response]]))

(defn init []
  (steambot/init))

(defn steam []
  (steambot/generate))

(defn json [f]
  (wrap-json-response (fn [req] (response {:text (f)}))))

(defroutes app
  (GET "/" [] (steam))
  (POST "/" [] (json steam))
  (GET "/steam" [] (steam))
  (POST "/steam" [] (json steam)))
