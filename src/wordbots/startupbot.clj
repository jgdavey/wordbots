(ns wordbots.startupbot
  (:require [clojure.java.io :as io]
            [wordbots.protocols :as p]
            [wordbots.util :refer [lines paragraphs]]))

(def ^:static responses ["My new startup is like %s for %s."
                         "So, basically, it's like %s for %s."
                         "Have you heard of %s? It's like that, but for %s."
                         "Check this out. It's like %s for %s."
                         "I really want to do something like %s, but for %s."
                         "We're trying to fill the same space as %s, only for %s instead."
                         "It's like %s for %s."
                         "So it's like %s for %s."
                         "The idea is simple. It's %s for %s."])

(defn index [a]
  (let [file->lines #(->> (io/resource %) slurp lines)]
    (swap! a assoc :this (file->lines "startup/this.txt"))
    (swap! a assoc :that (file->lines "startup/that.txt"))
    :ok))

(defn generate* [index]
  (format (rand-nth responses)
          (rand-nth (:this index))
          (rand-nth (:that index))))

(defn bot []
  (let [idx (atom {:this [] :that []})]
    (reify
      p/Bot
      (init [_]
        (index idx))
      (generate [_ _]
        (generate* @idx)))))

(comment

(def sb (bot))
(p/init sb)
(p/generate sb {})

)
