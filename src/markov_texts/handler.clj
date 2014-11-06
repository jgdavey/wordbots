(ns markov-texts.handler
  (:require [markov-texts.core :as core]
            [ring.util.response :as response]))

(defn init []
  (let [files ["aristotle.txt"
               "kafka.txt"
               "nietzsche.txt"
               "russell.txt"
               "steam.txt"]]
    (doseq [text files]
      (core/index-resource text))))

(defn app [req]
  (response/response (core/generate core/indexed)))
