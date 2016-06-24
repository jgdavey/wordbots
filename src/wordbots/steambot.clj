(ns wordbots.steambot
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io]
            [wordbots.markov :as m]
            [wordbots.protocols :as p])
  (:import [java.io InputStreamReader]))

(def texts ["aristotle.txt"
            "kafka.txt"
            "nietzsche.txt"
            "russell.txt"
            "steam.txt"])

(defn tokenize [^String data]
  (re-seq #"[a-zA-Z'][a-zA-Z-']*[,\.\?!:;']?(?=\s)" data))

(defn index-resource
  "Read and index a resource from classpath. Works in jar files as well."
  [index-atom text]
  (with-open [file (InputStreamReader. (.openStream (io/resource text)))]
    (swap! index-atom m/index (slurp file) tokenize))
  :ok)

;; Generation
(defn sentences-from
  "Given a bunch of text, return a reasonable sentence or two."
  [text]
  (let [sentences (drop 1 (str/split text #"(?<=[\.!\?]) +(?=[A-Z][^\.])"))]
    (str/join " " (if (< 2 (count sentences))
                    (butlast sentences)
                    sentences))))

(defn generate*
  "Using index idx, generate a sentence"
  [idx]
  (sentences-from
    (m/tuples->sentence
      (m/generate-forward idx (-> idx :forward-index keys rand-nth)))))

(defrecord Markovbot [a texts]
  p/Bot
  (init [_]
    (doseq [text texts]
      (index-resource a text)))
  (generate [_ _]
    (generate* @a)))

(defn bot []
  (->Markovbot (atom (m/markov-index-factory 2))
               (map (partial str "steambot/") texts)))

(comment

(def b (bot))
(p/init b)
(p/generate b [])

)
