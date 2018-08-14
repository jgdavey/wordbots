(ns wordbots.markov
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [chan <! >! go timeout close!]]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io]))

(defprotocol PathEncoder
  (-encode-prefix-path [_ prefixes]))

(defn default-encoder []
  (reify PathEncoder
    (-encode-prefix-path [_ prefixes] [(vec prefixes)])))

(defn nested-encoder []
  (reify PathEncoder
    (-encode-prefix-path [_ prefixes] (vec prefixes))))

(defn string-encoder [separator]
  (reify PathEncoder
    (-encode-prefix-path [_ prefixes] [(str/join separator prefixes)])))

(defrecord MarkovIndex [tuple-size forward-index backward-index entries encoder])

(defn encode-prefix [{:keys [encoder] :as idx} prefix]
  (-encode-prefix-path encoder prefix))

(defn markov-index-factory
  ([] (markov-index-factory 2))
  ([tuple-size] (->MarkovIndex tuple-size {} {} {} (nested-encoder))))

(defn update-in!
  [m [k & ks] f & args]
   (if ks
     (assoc! m k (apply update-in (get m k) ks f args))
     (assoc! m k (apply f (get m k) args))))

(defn increment [num]
  (if (nil? num)
    1
    (inc ^long num)))

(defn- index-path
  "Given a map and pair, updates the \"path\" to
  increment the count of first followed by second occurences"
  [m path]
  (update-in! m path increment))

(defn words [string]
  (re-seq #"[^\s\"]+(?=[\s\"]*)" string))

(defn add-markers [n tokens]
  (concat (repeat n ::boundary) tokens [::boundary]))

(defn index-tokens [{:keys [tuple-size] :as idx} dir tokens]
  (let [m (get idx dir)
        pairs (->> tokens
                   (add-markers tuple-size)
                   (partition (+ 1 tuple-size) 1)
                   (map #(conj (encode-prefix idx (butlast %)) (last %))))]
    (persistent! (reduce index-path (transient m) pairs))))

(defn index-entries [{:keys [entries tuple-size]} tokens]
  (let [pairs (mapv (juxt first vec) (partition tuple-size 1 tokens))]
    (persistent! (reduce index-path (transient entries) pairs))))

(defn index
  "Index String data into index MarkovIndex idx, optionally using tokenizer"
  ([idx ^String data]
   (index idx data words))
  ([idx ^String data tokenizer]
   (let [tokens (tokenizer data)
         forward (index-tokens idx :forward-index tokens)
         backward (index-tokens idx :backward-index (reverse tokens))
         entries (index-entries idx tokens)]
     (->MarkovIndex (:tuple-size idx) forward backward entries (:encoder idx)))))

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
  "Using index m, dir :forward-index or :backward-index, starting at key k, generate sequence of tuples.
  Stops at a ::boundary, or after max words, whatever comes first."
  [{:keys [tuple-size] :as idx} dir k max-words]
  (let [m (get idx dir)]
    (loop [acc (if (coll? k) k [k])]
      (let [prefix (subvec acc (max (- (count acc) tuple-size) 0))
            d (get-in m (encode-prefix idx prefix))
            nextword (when (pos? (count d))
                       (gen/weighted d))]
        (if (and d nextword (< (count acc) max-words))
          (recur (conj acc nextword))
          (filter string? acc))))))

(defn generate-forward [idx start-at]
  (let [{:keys [tuple-size forward-index]} idx
        start (or start-at
                  (vec (repeat tuple-size ::boundary)))]
    (generate-sequence idx :forward-index start 100)))

(defn generate-with-seed [idx seed]
  (when-let [starts (get-in idx [:entries seed])]
    (let [start (gen/weighted starts)
          front-half (generate-sequence idx :backward-index (vec (reverse start)) 50)
          back-half (generate-sequence idx :forward-index start 50)]
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
    (assoc rel :distance (Math/abs (- (-> rel :tuples count) target-words)))))

(defn generate [idx {:keys [start-at
                            seed
                            target-length
                            timeout-ms]
                     :or {timeout-ms 100
                          target-length 30}}]
  (let [{:keys [tuple-size forward-index]} idx
        gen (if seed
              (partial generate-with-seed idx seed)
              (partial generate-forward idx start-at))
        results (chan 1 (comp (map as-rel)
                              (map (add-distance target-length))))]
    (produce-while-values results gen {:max (min (max 20 timeout-ms) 200) :timeout-ms timeout-ms})
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


  (require '[criterium.core :as crit])

  (def test-lines (->> "test.txt"
                       io/resource
                       io/reader
                       line-seq
                       (take 1000)
                       vec))

  ;; 951 ms
  (crit/bench
   (let [idx (atom (->MarkovIndex 2 {} {} {} (default-encoder)))]
     (doseq [e test-lines]
       (swap! idx index e))))

  ;; 800 ms
  (crit/bench
   (let [idx (atom (->MarkovIndex 2 {} {} {} (nested-encoder)))]
     (doseq [e test-lines]
       (swap! idx index e))))

  ;; 846 ms
  (crit/bench
   (let [idx (atom (->MarkovIndex 2 {} {} {} (string-encoder ":")))]
     (doseq [e test-lines]
       (swap! idx index e))))

  (require '[clj-memory-meter.core :as cmm])
  (do
    (def default-idx (atom (->MarkovIndex 2 {} {} {} (default-encoder))))
    (def nested-idx (atom (->MarkovIndex 2 {} {} {} (nested-encoder))))
    (def string-idx (atom (->MarkovIndex 2 {} {} {} (string-encoder ":"))))
    (with-open [rdr (io/reader (io/resource "plotbot/plots.txt"))]
      (doseq [e (take 1000 (line-seq rdr))]
        (swap! default-idx index e)
        (swap! nested-idx index e)
        (swap! string-idx index e)))
    {:default (cmm/measure @default-idx)
     :nested (cmm/measure @nested-idx)
     :string (cmm/measure @string-idx)})

  (crit/bench
   (generate-with-seed @default-idx "vicious"))

  ;; string 1.91 ms
  ;; nested 1.96 ms

  (-> default-idx deref :entries first)

  ;; plots.txt
  ;; => {:default "392.0 MB", :nested "272.8 MB", :string "391.3 MB"}

  )
