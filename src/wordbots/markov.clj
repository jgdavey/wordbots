(ns wordbots.markov
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clj-tuple :refer [tuple]]
            [clojure.java.io :as io]))

(def increment (fnil inc 0))

(defn- word-count [text]
  (count (re-seq #"\w+" text)))

(defn update-in!
  [m [k & ks] f & args]
   (if ks
     (assoc! m k (apply update-in (get m k) ks f args))
     (assoc! m k (apply f (get m k) args))))

(defn- index-path
  "Given a map and pair, updates the \"path\" to
  increment the count of first followed by second occurences"
  [m path]
  (update-in! m path increment))

(defn words [string]
  (re-seq #"[^\s\"]+(?=[\s\"]*)" string))

(defn add-markers [pairs]
  (-> [[:start (vec (ffirst pairs))]]
      (into pairs)
      (conj [[(peek (peek pairs)) :end]])))

(defn partition-tuples [n coll]
  (mapv (partial apply tuple) (partition n coll)))

(defn partitionv [n coll]
  (mapv vec (partition n coll)))

(defn index
  "Index String data into index map m, using tuples of size tuple-size"
  ([m data]
   (index m data 2))
  ([m ^String data tuple-size]
    (let [pairs (->> data
                     words
                     (partition (* 2 tuple-size) 1)
                     (mapv (partial partition-tuples tuple-size))
                     add-markers)]
      (persistent! (reduce index-path (transient m) pairs)))))

(defn ensure-trailing-punctuation [^String string]
  (if (or (.endsWith string ".")
          (.endsWith string "!")
          (.endsWith string "?"))
    string
    (str string ".")))

(defn generate*
  "Using index idx, (optionally starting at key k), generate a sentence"
  ([idx]
   (generate* idx (key (rand-nth (seq idx)))))
  ([idx k]
   (loop [acc [k]]
     (let [d (get idx (peek acc))
           nextword (when (pos? (count d))
                      (gen/weighted d))]
       (if (and d nextword (< (count acc) 90))
         (recur (conj acc nextword))
         (->> acc flatten (str/join " ") ensure-trailing-punctuation))))))
