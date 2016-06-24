(ns wordbots.plotbot
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
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

(defn presence [thing]
  (when (seq thing)
    thing))

(defn parse-query [{:strs [text trigger_word]}]
  (let [pattern (re-pattern (str "^" (or trigger_word "plot(bot)?:?") " *"))]
    (some-> text
            (str/replace pattern "")
            presence)))

(defn generate* [index params]
  (m/generate @index {:target-length 45
                      :timeout-ms 100
                      :seed (parse-query params)}))

(defrecord Markovbot [a]
  p/Bot
  (init [_]
    (init-texts a))
  (generate [_ params]
    (generate* a params)))

(defn bot []
  (->Markovbot (atom (m/markov-index-factory 2))))

(comment

(def b (bot))
(p/init b)
(p/generate b nil)

)
