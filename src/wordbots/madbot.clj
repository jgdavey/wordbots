(ns wordbots.madbot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [wordbots.util :refer [lines paragraphs]]))

(declare index-text)

(set! *warn-on-reflection* true)

(deftype Index [^clojure.lang.IPersistentSet lookup ^clojure.lang.IPersistentVector access]
  clojure.lang.IFn
  (invoke [this obj] (lookup obj))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args))
  clojure.lang.ILookup
  (valAt [this key] (.get lookup key))
  (valAt [this key not-found] (throw (UnsupportedOperationException.)))
  clojure.lang.Indexed
  (nth [this n] (.nth access n))
  (nth [this n not-found] (.nth access n not-found))
  (count [this] (.count access)))

(defrecord Token [word part]
  Object
  (toString [_] word))

(def index (atom {:adjective nil}))
(def proverbs (atom []))

(def mappings
  {:adjective  "adjectives.txt"
   :action     "actions.txt"
   :object     "objects.txt"
   :animal     "animals.txt"
   :body-part  "body_parts.txt"
   :comparator "comparators.txt"})

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
        (swap! index assoc part (->Index (set w) w)))))
  (let [proverb-texts (->> (io/resource "proverbs.txt") slurp paragraphs)]
    (reset! proverbs (mapv index-text proverb-texts))))

(defn generate []
  (madlib (rand-nth @proverbs)))

(comment

(init)
(generate)

(>pprint (repeatedly 20 generate))

)
