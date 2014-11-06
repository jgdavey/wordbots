(ns markov-texts.core
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io])
  (:import [java.io InputStreamReader]))

(defonce indexed (atom {}))

(def xform
  )

(defn index
  "Index String data into index map m"
  [m ^String data]
  (let [s (->> (re-seq #"[a-zA-Z'][a-zA-Z-_']*[,\.\?!:;]?(?=\s)" data)
               (partition 4 1)
               (map (partial partition 2)))
        index (or m {})]
    (reduce (fn [all v]
              (let [path (mapv vec v)]
                (-> all
                    (update-in [(peek path)] (fnil identity {}))
                    (update-in path (fnil inc 0))))) m s)))

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
         (let [st (->> acc
                       flatten
                       (str/join " "))
               sentences (take (gen/weighted {1 2, 2 5, 3 1}) (drop 1 (str/split st #"[\.!\?] +(?=[A-Z])")))]
           (str (str/join ". " sentences) ".")))))))

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
