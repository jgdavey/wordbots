(ns wordbots.handler
  (:require [wordbots.steambot :as steambot]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :as r :refer [response]]))

(defn- init-steambot []
  (let [files ["aristotle.txt"
               "kafka.txt"
               "nietzsche.txt"
               "russell.txt"
               "steam.txt"]]
    (doseq [text files]
      (steambot/index-resource text))))

(defn init []
  (init-steambot))

(defn generate []
  (steambot/generate steambot/indexed))

(defn text-handler [req]
  (response (generate)))

(def json-handler
  (wrap-json-response (fn [req]
                        (response {:text (generate)}))))

(defn app [req]
  (if (= :get (:request-method req))
    (text-handler req)
    (json-handler req)))
