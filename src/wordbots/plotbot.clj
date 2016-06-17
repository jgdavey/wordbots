(ns wordbots.plotbot
  (:require [clojure.java.io :as io]
            [clojure.data.generators :as gen]
            [wordbots.markov :as m]
            [wordbots.protocols :as p]
            [wordbots.util :refer [lines paragraphs]]))

(defn init-texts [index]
  (with-open [rdr (io/reader (io/resource "plotbot/plots.txt") :encoding "ISO-8859-1")]
    (doseq [line (line-seq rdr)]
      (when (= 1 (rand-int 2)) ; coinflip
        (swap! index m/index line))))
  (with-open [rdr (io/reader (io/resource "erowid.txt"))]
    (doseq [line (line-seq rdr)]
      (when (= 1 (rand-int 12))
        (swap! index m/index line)))))

(defn generate* [index]
  (m/generate @index {:target-length 45, :timeout-ms 100}))

(defrecord Markovbot [a]
  p/Bot
  (init [_]
    (init-texts a))
  (generate [_ _]
    (generate* a)))

(defn bot []
  (->Markovbot (atom (m/markov-index-factory 2))))

(comment

(def b (bot))
(p/init b)
(p/generate b nil)

)
