(ns wordbots.wordnet
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [wordbots.util :refer [map-entry]]))

(def parts-of-speech
  {"n" ::noun
   "a" ::adjective
   "s" ::sense
   "v" ::verb
   "r" ::adverb})

(derive ::sense ::adjective)

(def mapping {::adverb "adv"
              ::noun "noun"
              ::verb "verb"
              ::adjective "adj"})

(def files (reduce-kv (fn [m k v]
                        (assoc m k {:data (io/resource (str "dict/data." v))
                                    :index (io/resource (str "dict/index." v))}))
                      {} mapping))

(defn clean-word [word]
  (str/replace word #"_" " "))

(defn parse-data-line [line]
  (when (re-find #"^\d{8}" line)
    (let [tokens (str/split line #" +")
          [offset filenum pos wcount & tokens] tokens
          pos (get parts-of-speech pos ::unknown)
          word-count (Long/parseLong wcount 16)
          word-pairs (partition 2 (take (* 2 word-count) tokens))]
      (into []
            (map (fn [[word id]]
                   (map-entry
                    (clean-word word)
                    #{pos})))
            word-pairs))))

(defn parse-file-into [resource m]
  (with-open [f (io/reader resource)]
    (let [lines (line-seq f)]
      (merge-with into m
                  (into {}
                        (comp (keep parse-data-line)
                              cat)
                        lines)))))

;; k - 1 word
;; v - n pos
;; v1 - 1 pos
(defn invert-relation [m]
  (with-meta
    (persistent! (reduce-kv (fn [m k v]
                              (reduce
                               (fn [acc k1]
                                 (if (contains? acc k1)
                                   (assoc! acc k1 (conj (get acc k1) k))
                                   (assoc! acc k1 [k]))) m v))
                            (transient {})
                            m))
    (meta m)))

(defn make-dictionary []
  (let [words
        (reduce (fn [acc [_ {:keys [data]}]]
                  (parse-file-into data acc))
                {}
                files)]
    {:word->pos words
     :pos->words (invert-relation words)}))

(def dict (make-dictionary))

(defn word->parts-of-speech [word]
  (get-in dict [:word->pos word]))

(defn random-words [n part-of-speech]
  (let [gen #(rand-nth (get-in dict [:pos->words part-of-speech]))]
    (take n (repeatedly gen))))


(comment

  (keys (:pos->words dict))

  (random-words 10 ::sense)

  (->> (:word->pos dict)
       (filter (fn [e]
                 (< 3 (count (val e)))))
       (sort-by key))

  )
