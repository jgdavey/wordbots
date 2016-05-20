(ns wordbots.markov
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [chan <! >! go timeout close!]]
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
          (.endsWith string ",")
          (.endsWith string "?"))
    string
    (str string ".")))

(defn tuples->sentence [tuples]
  (->> tuples flatten (str/join " ") ensure-trailing-punctuation))

(defn weight-score [idx nextword]
  (->> nextword (get idx) vals (reduce + 0)))

(defn average-score [nodes]
  (/ (reduce + 0 (map :score nodes)) (count nodes)))

(defn generate-sequence
  "Using index idx, starting at key k, generate sequence of tuples.
  Stops at an :end, or after max tuples, whatever comes first."
  [idx k max]
  (loop [acc [k]]
    (let [d (get idx (peek acc))
          nextword (when (pos? (count d))
                     (gen/weighted d))]
      (if (and d nextword (< (count acc) max))
        (recur (conj acc nextword))
        acc))))

(defn generate-1 [idx k]
  (-> (generate-sequence idx k 90)
      tuples->sentence))

(defn produce-while-values
  "Calls (f), adding to results channel, until :max number is reached or
  :ms has elapsed. Closes results channel."
  [results-chan f {:keys [max timeout-ms] :or {max 10 timeout-ms 100}}]
  (go (loop [t (timeout timeout-ms)
             n (async/to-chan (range max))]
          (let [[v c] (async/alts! [t n])]
            (if v
              (do
                (>! results-chan (f))
                (recur t n))
              (close! results-chan))))))

(defn as-rel [tuple-sequence]
  {:tuples tuple-sequence})

(defn add-distance [target-words]
  (fn [rel]
    (assoc rel
           :distance (Math/abs (- (* (-> rel :tuples count)
                                     (-> rel :tuples first count)) target-words)))))

(defn generate [idx {:keys [start-at
                            target-length
                            timeout-ms]
                     :or {timeout-ms 100
                          target-length 50}}]
  (let [start-key (or start-at
                      (rand-nth (keys (:start idx)))
                      (rand-nth (keys idx)))
        results (chan 1 (comp (map as-rel)
                              (map (add-distance target-length))))]
   (produce-while-values results
                         (partial generate-sequence idx start-key (* target-length 2))
                         {:max 100 :timeout-ms timeout-ms})
    (let [r (async/<!! (async/into [] results))
          sentence (->> r
                        (sort-by :distance)
                        first
                        :tuples
                        tuples->sentence)]
      sentence)))
