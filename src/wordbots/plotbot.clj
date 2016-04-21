(ns wordbots.plotbot
  (:require [clojure.java.io :as io]
            [clojure.data.generators :as gen]
            [wordbots.markov :as m]
            [wordbots.protocols :as p]
            [wordbots.util :refer [lines paragraphs]]))

 ;; The smaller the number, the more nonsensical
(def tuple-size 2)

(defn init-texts [index]
  (with-open [rdr (io/reader (io/resource "plotbot/plots.txt") :encoding "ISO-8859-1")]
    (doseq [e (line-seq rdr)]
      (swap! index m/index e tuple-size)))
  (with-open [rdr (io/reader (io/resource "erowid.txt"))]
    (doseq [e (line-seq rdr)]
      (swap! index m/index e tuple-size))))

(defn generate* [index]
  (m/generate* @index (key (rand-nth (seq (@index :start))))))

(defrecord Markovbot [a]
  p/Bot
  (init [_]
    (init-texts a))
  (generate [_ _]
    (generate* a)))

(defn bot []
  (->Markovbot (atom {})))

(comment

(def b (bot))
(p/init b)
(p/generate b nil)

)
