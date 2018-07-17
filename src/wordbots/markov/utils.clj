(ns wordbots.markov.utils
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [chan <! >! go timeout close!]]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io]))

(defn update-in!
  [m [k & ks] f & args]
   (if ks
     (assoc! m k (apply update-in (get m k) ks f args))
     (assoc! m k (apply f (get m k) args))))

(defn increment [num]
  (if (nil? num)
    1
    (inc ^long num)))

(defn index-path
  "Given a map and pair, updates the \"path\" to
  increment the count of first followed by second occurences"
  [m path]
  (update-in! m path increment))

(defn words [string]
  (re-seq #"[^\s\"]+(?=[\s\"]*)" string))

(defn add-markers [n boundary tokens]
  (concat (repeat n boundary) tokens [boundary]))

(defn prefix-pair [coll]
  (conj [(vec (butlast coll))] (last coll)))

(defn tokens->prefix-pairs [tuple-size tokens boundary]
  (->> tokens
       (add-markers tuple-size boundary)
       (partition (+ 1 tuple-size) 1)
       (map prefix-pair)))

(defn index-tokens [m tuple-size tokens boundary]
  (let [pairs (tokens->prefix-pairs tuple-size tokens boundary)]
    (persistent! (reduce index-path (transient m) pairs))))

(defn index-entries [m tuple-size tokens]
  (let [pairs (mapv (juxt first vec) (partition tuple-size 1 tokens))]
    (persistent! (reduce index-path (transient m) pairs))))

(defn ensure-trailing-punctuation [^String string]
  (let [c (last string)]
    (case c
      \. string
      \! string
      \? string
      \, (str (butlast string) ".")
      \: (str (butlast string) ".")
      \; (str (butlast string) ".")
      (str string "."))))

(defn as-rel [tuple-sequence]
  {:tuples tuple-sequence})

(defn add-distance [target-words]
  (fn [rel]
    (assoc rel
           :distance (Math/abs (- (-> rel :tuples count)
                                  target-words)))))

(defn tuples->sentence [tuples]
  (when (seq tuples)
    (->> tuples flatten (str/join " ") ensure-trailing-punctuation)))

(defn best [target-words candidates]
  (->> candidates
       (map (comp
             (fn [row] (assoc row :sentence (tuples->sentence (:tuples row))))
             (add-distance target-words)))
       (sort-by (juxt :distance (comp count :sentence)))
       first
       :sentence))

(defn produce-while-values
  "Calls (f), adding to results channel, until :max number is reached or
  :ms has elapsed. Closes results channel."
  [results-chan f {:keys [max timeout-ms] :or {max 10 timeout-ms 100}}]
  (go (loop [t (timeout timeout-ms)
             n (async/to-chan (range max))]
          (let [[v c] (async/alts! [t n])
                result (when v (f))]
            (if result
              (do
                (>! results-chan result)
                (recur t n))
              (close! results-chan))))))
