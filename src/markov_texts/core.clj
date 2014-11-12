(ns markov-texts.core
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io])
  (:import [java.io InputStreamReader]))

(defonce indexed (atom {}))

(defn index
  "Index String data into index map m"
  [m ^String data]
  (let [s (->> (re-seq #"[a-zA-Z'][a-zA-Z-']*[,\.\?!:;']?(?=\s)" data)
               (partition 4 1)
               (map (partial partition 2)))
        index (or m {})]
    (reduce (fn [all v]
              (let [path (mapv vec v)]
                (-> all
                    (update-in [(peek path)] (fnil identity {}))
                    (update-in path (fnil inc 0))))) m s)))

(defn- word-count [text]
  (count (re-seq #"\w+" text)))

(defn sentences-from [text]
  (let [n (gen/weighted {20 4, 30 5, 45 1})
        sentences (drop 1 (str/split text #"[\.!\?] +(?=[A-Z])"))]
    (loop [acc [(first sentences)]
           more (next sentences)]
      (if (and more (> n (apply + (map word-count (conj acc (first more))))))
        (recur (conj acc (first more)) (next more))
        (str (str/join ". " acc) ".")))))

(defn generate
  "Using atom a, generate a sentence"
  ([] (generate indexed))
  ([a]
   (loop [acc [(key (rand-nth (seq @a)))]]
     (let [d (get @a (peek acc))
           nextword (when (pos? (count d))
                      (gen/weighted d))]
       (if (and d nextword (< (count acc) 120))
         (recur (conj acc nextword))
         (sentences-from (->> acc flatten (str/join " "))))))))

(defn index-resource [text]
  (with-open [file (InputStreamReader. (.openStream (io/resource text)))]
    (swap! indexed index (slurp file)))
  :ok)

(comment

(defonce kjv (slurp "http://www.gutenberg.org/cache/epub/10/pg10.txt"))
(def i (atom {}))
(swap! i index kjv)

(reset! indexed {})
(index-resource "texts")

(generate indexed)

(markov-texts.handler/init)

(io/file (io/resource "aristotle.txt"))

)
