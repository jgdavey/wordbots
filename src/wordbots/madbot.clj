(ns wordbots.madbot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [com.joshuadavey.vecset :as v :refer [vecset]]
            [wordbots.util :refer [lines paragraphs]]))

(declare index-text)

(defrecord Token [word part]
  Object
  (toString [_] word))

(def index (atom {:adjective nil}))
(def proverbs (atom []))

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

(defn init []
  (let [words #(->> (io/resource %) slurp lines)]
    (doseq [[part file] mappings]
      (let [w (words file)]
        (swap! index assoc part (vecset w)))))
  (let [proverb-texts (->> (io/resource "madbot/proverbs.txt") slurp lines)]
    (reset! proverbs (mapv index-text proverb-texts))))

(defn generate []
  (madlib (rand-nth @proverbs)))

(comment

(init)
(generate)

(>pprint (repeatedly 20 generate))

)
