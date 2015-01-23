(ns wordbots.steambot
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.java.io :as io])
  (:import [java.io InputStreamReader]))

;; Helpers
(def increment (fnil inc 0))

(defn- word-count [text]
  (count (re-seq #"\w+" text)))

;; Indexing
(defonce indexed (atom {}))

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
  [text]
  (with-open [file (InputStreamReader. (.openStream (io/resource text)))]
    (swap! indexed index (slurp file)))
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

(defn init []
  (doseq [text texts]
    (index-resource text)))

(comment

(reset! indexed {})
(init)
(generate)

)
