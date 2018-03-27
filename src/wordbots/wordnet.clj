(ns wordbots.wordnet
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def parts-of-speech
  {"n" ::noun
   "a" ::adjective
   "s" ::sense
   "v" ::verb
   "r" ::adverb})

(derive ::sense ::adjective)

(def mapping {::adjective "adj"
              ::adverb "adv"
              ::noun "noun"
              ::verb "verb"})

(def files (reduce-kv (fn [m k v]
                        (assoc m k {:data (io/resource (str "dict/data." v))
                                    :index (io/resource (str "dict/index." v))}))
                      {} mapping))

(defn clean-word [word]
  (str/replace word #"_" " "))

(defn map-entry [k v]
  (clojure.lang.MapEntry/create k v))

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
                    pos)))
            word-pairs))))

(defn parse-file-into [resource m]
  (with-open [f (io/reader resource)]
    (let [lines (line-seq f)]
      (into m
            (comp (keep parse-data-line)
                  cat)
            lines))))

(defn group-by-fn [keyfn coll f]
  (persistent!
   (reduce
    (fn [ret x]
      (let [k (keyfn x)]
        (assoc! ret k (conj (get ret k []) (f x)))))
    (transient {}) coll)))

(defn make-dictionary []
  (let [words
        (reduce (fn [acc [_ {:keys [data]}]]
                  (parse-file-into data acc))
                {}
                files)]
    {:word->pos words
     :pos->words (group-by-fn val words key)}))

(def dict (make-dictionary))

(defn word->part-of-speech [word]
  (get-in dict [:words->pos word]))

(defn random-words [n part-of-speech]
  (let [gen #(rand-nth (get-in dict [:pos->words part-of-speech]))]
    (take n (repeatedly gen))))
