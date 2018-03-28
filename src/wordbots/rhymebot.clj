(ns wordbots.rhymebot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [wordbots.wordnet :as wordnet]
            [wordbots.protocols :as p]
            [wordbots.util :refer [clean-word lines map-entry]]))

(defn read-to-word-rhymes [readable]
  (with-open [rdr (io/reader readable)]
    (into {}
          (comp (remove #(str/starts-with? % ";;;"))
                (map #(str/split % #"\s+"))
                (map (fn [[word & rhyme]]
                       (map-entry
                        (clean-word (str/lower-case word))
                        (mapv keyword (reverse rhyme))))))
          (line-seq rdr))))

(def word-to-rhyme (read-to-word-rhymes (io/resource "cmudict.txt")))

(def rhyme-to-word
  (reduce
    (fn [m [word rhyme]]
      (assoc-in m (conj rhyme :***) word))
    {}
    word-to-rhyme))

(defn get-deepest-values [k]
  (if (string? k)
    [k]
    (mapcat get-deepest-values (vals k))))

(defn at-least
  "stateful transducer"
  {:added "1.2"
   :static true}
  [^long n]
  (fn [rf]
    (let [a (java.util.ArrayList. n)
          hit? (volatile! false)
          rrf (#'clojure.core/preserving-reduced rf)]
      (fn
        ([] (rf))
        ([result]
         (rf result))
        ([result input]
         (if @hit?
           (rf result input)
           (do
             (.add a input)
             (if (= (.size a) n)
               (let [v (vec (.toArray a))]
                 (.clear a)
                 (vreset! hit? true)
                 (reduce rrf result v))
               result))))))))

(defn deepest-rhymes*
  [word xform]
  (when-let [entry (word-to-rhyme word)]
    (let [max-phonemes (max (dec (count entry)) 3)]
      (->> (range max-phonemes 1 -1)
           (map (fn [i]
                  (->> (take i entry)
                       (get-in rhyme-to-word)
                       get-deepest-values
                       (into [] (comp (remove #{word})
                                      xform)))))
           (filter not-empty)
           first))))

(defn deepest-rhymes
  [word]
  (deepest-rhymes* word (comp
                         (at-least 2))))

(defn deepest-rhymes-similar-length
  [word]
  (let [len (count (word-to-rhyme word))]
    (deepest-rhymes* word
                     (filter
                      (fn [candidate]
                        (< (Math/abs (- len
                                        (count (word-to-rhyme candidate))))
                           2))))))

(def placeholder-pattern #"\{\{(.*?)\}\}")

(defn add-placeholders
  "Idempotent"
  [^String text]
  (if (re-find placeholder-pattern text)
    text
    (str/replace text #"\b([a-zA-Z-]*[a-zA-Z])([^a-zA-Z]*)(\n|$)" "{{$1}}$2$3")))

(defn re-rhyme [text]
  (str/replace text placeholder-pattern
               (fn [[_ word]]
                 (let [pos (wordnet/word->part-of-speech word)
                       all-rhymes (seq (deepest-rhymes word))
                       rhymes (filter #(= pos (wordnet/word->part-of-speech %)) all-rhymes)]
                   (if (seq rhymes)
                     (rand-nth rhymes)
                     word)))))

(defn random-rhymable-words [gen rhymes min]
  (loop []
    (let [word (gen)
          words (shuffle (cons word (rhymes word)))]
      (if (>= (count words) min)
        words
        (recur)))))


(defn pattern->positions [pattern]
  (vals
   (reduce (fn [acc [i c]]
             (update acc c (fnil conj []) i))
           {}
           (map-indexed vector pattern))))

(defn generate-rhymeset [pattern gen rhymes]
  (->> pattern
       pattern->positions
       (mapcat (fn [indices]
                 (map vector indices
                      (random-rhymable-words gen rhymes (count indices)))))
       (sort-by first)
       (map last)))

(defn gen-word [pos]
  (first (wordnet/random-words 1 pos)))

(defn rhyme-pattern [template pattern]
  (let [gen-rhymes #(generate-rhymeset pattern
                                       (partial gen-word ::wordnet/adjective)
                                       deepest-rhymes-similar-length)
        rhyme-seq (mapcat identity (repeatedly gen-rhymes))
        placeholders (count (re-seq placeholder-pattern template))]
    (reduce (fn [poem word]
              (str/replace-first poem placeholder-pattern word))
            template
            (take placeholders rhyme-seq))))


(defrecord Rhymebot [text]
  p/Bot
  (init [_])
  (generate [_ _]
    (re-rhyme text)))

(defn bot []
  (->Rhymebot (-> (io/resource "rhymebot/ttnbc.txt")
                  slurp
                  add-placeholders)))

(comment
  (time
   (rhyme-pattern
    (add-placeholders
     "Roses are red\nViolets are blue\nSugar is sweet\nAnd you are too")
    "ABAB"))


  (time
   (re-rhyme (-> (io/resource "rhymebot/ttnbc.txt")
                  slurp
                  add-placeholders)))

  )
