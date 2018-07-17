(ns wordbots.markov.memory
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [chan <! >! go timeout close!]]
            [clojure.data.generators :as gen]
            [wordbots.markov.utils :as mu]
            [wordbots.protocols :as p]))

(defn index
  "Index String data into index MarkovIndex idx, optionally using tokenizer"
  ([idx ^String data]
   (index idx data mu/words))
  ([idx ^String data tokenizer]
   (let [tokens (tokenizer data)
         ts (:tuple-size idx)]
     (-> idx
         (update :forward-index mu/index-tokens ts tokens nil)
         (update :backward-index mu/index-tokens ts (reverse tokens) nil)
         (update :entries mu/index-entries ts tokens)))))

(defn generate-sequence
  "Using (forward or backward) index m, tuple-size t, starting at key k, generate sequence of tuples.
  Stops at a nil, or after max tuples, whatever comes first."
  [m t k max-words]
  (loop [acc (if (coll? k) k [k])]
    (let [token (subvec acc (max (- (count acc) t) 0))
          d (get m token)
          nextword (when (pos? (count d))
                     (gen/weighted d))]
      (if (and d nextword (< (count acc) max-words))
        (recur (conj acc nextword))
        (filter string? acc)))))

(defn generate-forward [idx start-at]
  (let [{:keys [tuple-size forward-index]} idx
        start (or start-at
                  (vec (repeat tuple-size nil)))]
    (generate-sequence (:forward-index idx) (:tuple-size idx) start 100)))

(defn generate-with-seed [idx seed]
  (when-let [starts (get-in idx [:entries seed])]
    (let [start (gen/weighted starts)
          front-half (generate-sequence (:backward-index idx) (:tuple-size idx) (vec (reverse start)) 50)
          back-half (generate-sequence (:forward-index idx) (:tuple-size idx) start 50)]
      (concat (reverse (drop (:tuple-size idx) front-half)) back-half))))

(defn generate-1 [idx {:keys [start-at seed]}]
  (mu/tuples->sentence (if seed
                         (generate-with-seed idx seed)
                         (generate-forward idx start-at))))

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
        results (chan 1 (map mu/as-rel))]
    (mu/produce-while-values results gen {:max 100 :timeout-ms timeout-ms})
    (mu/best target-length (async/<!! (async/into [] results)))))

(defrecord MarkovIndex [idx]
  p/Markov
  (-index [this rdr tokenizer-fn]
    (doseq [line (line-seq rdr)]
      (swap! idx index line)))
  (-generate [this seed target-tuple-count]
    (let [gen (if seed
                #(mu/as-rel (generate-with-seed @idx seed))
                #(mu/as-rel (generate-forward @idx nil)))]
      (mu/best target-tuple-count (repeatedly 10 gen)))))

(defn markov-index-factory
  ([] (markov-index-factory 2))
  ([tuple-size] (->MarkovIndex (atom {:tuple-size tuple-size
                                      :forward-index {}
                                      :backward-index {}
                                      :entries {}}))))

(comment

(require '[clojure.java.io :as io])

(def idx (markov-index-factory 2))

(with-open [rdr (io/reader (io/resource "plotbot/plots.txt"))]
  (p/-index idx rdr mu/words))

(take 10 (keys (-> idx :idx deref :forward-index)))

(time
 (p/-generate idx "email" 25))

(time
 (p/-generate idx nil 25))
)
