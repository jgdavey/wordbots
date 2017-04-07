(ns wordbots.madbot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [wordbots.protocols :as p]
            [com.joshuadavey.vecset :as v :refer [vecset]]
            [wordbots.util :refer [lines paragraphs]]))

(declare index-text)

(defrecord Token [word part]
  Object
  (toString [_] word))

(def index (atom {:adjective nil}))

(def mappings
  {:adjective  "madbot/adjectives.txt"
   :action     "madbot/actions.txt"
   :object     "madbot/objects.txt"
   :animal     "madbot/animals.txt"
   :body-part  "madbot/body_parts.txt"
   :comparator "madbot/comparators.txt"})

(defn categorize [w]
  (let [word (first (re-seq #"\w+" w))]
    (some (fn [[part words]]
            (when (words word) part))
          @index)))

(defn tokenize [word]
  (->Token word (categorize word)))

(defn index-text [text]
  (let [words (str/split text #"\b")]
    (mapv tokenize words)))

(defn possibly-replace [token]
  (let [idx (get @index (:part token))]
    (if (and idx (> (rand) 0.2))
      (rand-nth idx)
      token)))

(defn madlib [tokens]
  (loop [t tokens
         i 5]
    (when (zero? i)
      (println (str/join t)))
    (if (and (not (zero? i)) (= t tokens))
      (recur (map possibly-replace tokens) (dec i))
      (str/join t))))

(defn lines-from-resource [filename]
  (->> (io/resource filename) slurp lines))

(defn init* []
  (doseq [[part file] mappings]
    (let [w (lines-from-resource file)]
      (swap! index assoc part (vecset w)))))

(defn generate* [proverbs]
  (madlib (index-text (rand-nth proverbs))))

(defrecord Madbot [proverbs]
  p/Bot
  (init [_] (init*))
  (generate [_ req] (generate* proverbs)))

(defn bot []
  (->Madbot
   (lines-from-resource "madbot/proverbs.txt")))

(comment

  (def mb (->Madbot ["The apple doesn't fall far from the tree."]))
  (def mb (bot))
  (p/init mb)
  (p/generate mb {})

  (init*)
  (generate* ["Nothing is certain but death and taxes."
              "Rome wasn't built in a day."])

  )
