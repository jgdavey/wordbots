(ns markov-texts.handler
  (:require [markov-texts.core :as core]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :as r :refer [response]]))

(defn init []
  (let [files ["aristotle.txt"
               "kafka.txt"
               "nietzsche.txt"
               "russell.txt"
               "steam.txt"]]
    (doseq [text files]
      (core/index-resource text))))

(defn generate []
  (core/generate core/indexed))

(defn text-handler [req]
  (response (generate)))

(def json-handler
  (wrap-json-response (fn [req]
                        (response {:text (generate)}))))

(defn app [req]
  (if (= :get (:request-method req))
    (text-handler req)
    (json-handler req)))
