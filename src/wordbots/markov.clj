(ns wordbots.markov
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [chan <! >! go timeout close!]]
            [clojure.data.generators :as gen]
            [clj-tuple :as tuple :refer [tuple]]
            [clojure.java.io :as io]))

(def increment (fnil inc 0))

(defrecord MarkovIndex [tuple-size forward-index backward-index entries])

(defn markov-index-factory
  ([] (markov-index-factory 2))
  ([tuple-size] (->MarkovIndex tuple-size {} {} {})))

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

(defn add-markers [n tokens]
  (concat (repeat n ::boundary) tokens [::boundary]))

(defn prefix-pair [coll]
  (conj [(apply tuple/vector (butlast coll))] (last coll)))

(defn tokens->prefix-pairs [tuple-size tokens]
  (->> tokens
       (add-markers tuple-size)
       (partition (+ 1 tuple-size) 1)
       (map prefix-pair)))

(defn index-tokens [m tuple-size tokens]
  (let [pairs (tokens->prefix-pairs tuple-size tokens)]
    (persistent! (reduce index-path (transient m) pairs))))

(defn index-entries [m tuple-size tokens]
  (let [pairs (mapv (juxt first (partial apply tuple/vector)) (partition tuple-size 1 tokens))]
    (persistent! (reduce index-path (transient m) pairs))))

(defn index
  "Index String data into index MarkovIndex idx, using tuples of size tuple-size"
  ([idx ^String data]
   (let [tokens (words data)
         forward (index-tokens (:forward-index idx) (:tuple-size idx) tokens)
         backward (index-tokens (:backward-index idx) (:tuple-size idx) (reverse tokens))
         entries (index-entries (:entries idx) (:tuple-size idx) tokens)]
     (->MarkovIndex (:tuple-size idx) forward backward entries))))

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

(defn tuples->sentence [tuples]
  (when (seq tuples)
    (->> tuples flatten (str/join " ") ensure-trailing-punctuation)))

(defn weight-score [idx nextword]
  (->> nextword (get idx) vals (reduce + 0)))

(defn average-score [nodes]
  (/ (reduce + 0 (map :score nodes)) (count nodes)))

(defn generate-sequence
  "Using (forward or backward) index m, tuple-size t, starting at key k, generate sequence of tuples.
  Stops at a ::boundary, or after max tuples, whatever comes first."
  [m t k max-words]
  (loop [acc (if (coll? k) k [k])]
    (let [token (apply tuple/vector (subvec acc (max (- (count acc) t) 0)))
          d (get m token)
          nextword (when (pos? (count d))
                     (gen/weighted d))]
      (if (and d nextword (< (count acc) max-words))
        (recur (conj acc nextword))
        (filter string? acc)))))

(defn generate-forward [idx start-at]
  (let [{:keys [tuple-size forward-index]} idx
        start (or start-at
                  (apply tuple/vector (repeat tuple-size ::boundary)))]
    (generate-sequence (:forward-index idx) (:tuple-size idx) start 100)))

(defn generate-with-seed [idx seed]
  (when-let [starts (get-in idx [:entries seed])]
    (let [start (gen/weighted starts)
          front-half (generate-sequence (:backward-index idx) (:tuple-size idx) (apply tuple/vector (reverse start)) 50)
          back-half (generate-sequence (:forward-index idx) (:tuple-size idx) start 50)]
      (concat (reverse (drop (:tuple-size idx) front-half)) back-half))))

(defn generate-1 [idx {:keys [start-at seed]}]
  (tuples->sentence (if seed
                      (generate-with-seed idx seed)
                      (generate-forward idx start-at))))

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

(defn as-rel [tuple-sequence]
  {:tuples tuple-sequence})

(defn add-distance [target-words]
  (fn [rel]
    (assoc rel
           :distance (Math/abs (- (* (-> rel :tuples count)
                                     (-> rel :tuples first count)) target-words)))))

(defn generate [idx {:keys [start-at
                            seed
                            target-length
                            timeout-ms]
                     :or {timeout-ms 100
                          target-length 50}}]
  (let [{:keys [tuple-size forward-index]} idx
        gen (if seed
              (partial generate-with-seed idx seed)
              (partial generate-forward idx start-at))
        results (chan 1 (comp (map as-rel)
                              (map (add-distance target-length))))]
    (produce-while-values results gen {:max 100 :timeout-ms timeout-ms})
    (->> (async/<!! (async/into [] results))
         (sort-by :distance)
         first
         :tuples
         tuples->sentence)))

(comment

(def idx (atom (markov-index-factory 2)))

(with-open [rdr (io/reader (io/resource "erowid.txt"))]
  (doseq [e (line-seq rdr)]
    (swap! idx index e)))

(take 10 (keys (:forward-index @idx)))

(generate @idx {:seed "email"})

)
