(ns wordbots.startupbot
  (:require [clj-http.client :as client]))

(def ^:static api "http://itsthisforthat.com/api.php?json")

(def ^:static responses ["My new startup is like a %s for %s."
                         "So, basically, it's like a %s for %s."
                         "Have you heard of a %s? It's like that, but for %s."
                         "Check this out. It's like a %s for %s."
                         "I really want to do something like a %s, but for %s."
                         "We're trying to fill the same space as a %s, only for %s instead."
                         "It's like a %s for %s."
                         "So it's like a %s for %s."
                         "The idea is simple. It's a %s for %s."])

(defn init []) ;; no-op

(defn fetch []
  (some-> (client/get api {:as :json})
          :body))

(defn generate []
  (let [resp (fetch)]
    (format (rand-nth responses) (:this resp) (:that resp))))

(comment

(generate)

)
