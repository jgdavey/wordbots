(ns wordbots.rhymebot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [wordbots.wordnet :as wordnet]
            [wordbots.protocols :as p]
            [wordbots.util :refer [clean-word lines]]))

(defn read-words-lines
  [readable]
  (with-open [rdr (io/reader readable)]
    (doall
     (for [^String line (line-seq rdr)
           :when (not (str/starts-with? line ";;;"))]
       (str/split line #"\s+")))))

(def word-to-rhyme
  (reduce (fn [m [word & rhyme]]
            (assoc m
                   (clean-word (str/lower-case word))
              (mapv keyword (reverse rhyme))))
          {}
          (read-words-lines (io/resource "cmudict.txt"))))

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
                 (if-let [rhymes (seq (deepest-rhymes word))]
                   (rand-nth rhymes)
                   word))))

(defn random-rhymable-word
  ([valid?]
   (let [word (rand-nth (keys word-to-rhyme))]
     (if (valid? word)
       word
       (recur valid?))))
  ([part-of-speech valid?]
   (loop []
     (let [word (first (wordnet/random-words 1 part-of-speech))]
       (if (valid? word)
         word
         (recur))))))

(def has-similar-length-rhymes? #(seq (deepest-rhymes-similar-length %)))

(defn rhyme-abab [text]
  (let [matched (atom -1)
        make-pair #(let [w1 (random-rhymable-word ::wordnet/adjective has-similar-length-rhymes?)
                         w2 (random-rhymable-word ::wordnet/adjective has-similar-length-rhymes?)]
                     [w1
                      w2
                      (rand-nth (deepest-rhymes-similar-length w1))
                      (rand-nth (deepest-rhymes-similar-length w2))])
        rhyme-seq (mapcat identity (repeatedly make-pair))]
    (str/replace text placeholder-pattern
                 (fn [_]
                   (swap! matched inc)
                   (nth rhyme-seq @matched)))))


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
  (rhyme-abab
   (add-placeholders
    "Roses are red\nViolets are blue\nSugar is sweet\nAnd you are too"))
  )
