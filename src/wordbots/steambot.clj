(ns wordbots.steambot
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io]
            [wordbots.markov :as m]
            [wordbots.protocols :as p])
  (:import [java.io InputStreamReader]))

;; Helpers
(def increment (fnil inc 0))

(defn- word-count [text]
  (count (re-seq #"\w+" text)))

(def texts ["aristotle.txt"
            "kafka.txt"
            "nietzsche.txt"
            "russell.txt"
            "steam.txt"])

(defn- index-path
  "Given an index and pair, updates the index to
  increment the count of first followed by second occurences"
  [idx pair]
  (update-in idx (mapv vec pair) increment))

(defn index
  "Index String data into index map m"
  [m ^String data]
  (->> (re-seq #"[a-zA-Z'][a-zA-Z-']*[,\.\?!:;']?(?=\s)" data)
               (partition 4 1)
               (map (partial partition 2))
    (reduce index-path m)))

(defn index-resource
  "Read and index a resource from classpath. Works in jar files as well."
  [index-atom text]
  (with-open [file (InputStreamReader. (.openStream (io/resource text)))]
    (swap! index-atom index (slurp file)))
  :ok)

;; Generation
(defn sentences-from
  "Given a bunch of text, return a reasonable sentence or two."
  [text]
  (let [n (gen/weighted {20 4, 30 5, 45 1})
        sentences (drop 1 (str/split text #"[\.!\?] +(?=[A-Z])"))]
    (loop [acc [(first sentences)]
           more (next sentences)]
      (if (and more (> n (apply + (map word-count (conj acc (first more))))))
        (recur (conj acc (first more)) (next more))
        (str (str/join ". " acc) ".")))))

(defn generate*
  "Using index idx, generate a sentence"
  [idx]
  (sentences-from
    (m/generate idx {:target-length (rand-nth [20 80])})))

(defrecord Markovbot [a texts]
  p/Bot
  (init [_]
    (doseq [text texts]
      (index-resource a text)))
  (generate [_ _]
    (generate* @a)))

(defn bot []
  (->Markovbot (atom {})
               (map (partial str "steambot/") texts)))

(comment

(def b (bot))
(p/init b)
(p/generate b [])

)
