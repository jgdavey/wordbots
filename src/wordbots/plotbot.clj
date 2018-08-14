(ns wordbots.plotbot
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.generators :as gen]
            [clojure.tools.logging :as log]
            [wordbots.markov :as m]
            [wordbots.protocols :as p]
            [wordbots.util :refer [lines paragraphs presence]]))

;; The following fn approximates this:
;; 200M 16
;; 400M 8
;; 800M 4
;; 1600M 2
;; 3200M 1
(defn curve [x]
  (/ 3200000000 x))

(defn clamp [lower n upper]
  (min upper (max lower n)))

(defn mem-factor []
  (let [x (.maxMemory (Runtime/getRuntime))]
    (int (clamp 1 (curve x) 32))))

(defn init-texts [index]
  (let [factor (mem-factor)]
    (with-open [rdr (io/reader (io/resource "plotbot/plots.txt") :encoding "ISO-8859-1")]
      (doseq [line (line-seq rdr)]
        (when (= 0 (rand-int factor)) ; coinflip
          (swap! index m/index line))))
    (with-open [rdr (io/reader (io/resource "erowid.txt"))]
      (doseq [line (line-seq rdr)]
        (when (= 0 (rand-int (* 50 factor))) ; very infrequent
          (swap! index m/index line))))))

(defn params->opts [{:strs [text trigger_word words] :as params}]
  (let [pattern (re-pattern (str "^" (or trigger_word "plot(bot)?:?") " *"))]
    {:seed (some-> text
                   (str/replace pattern "")
                   presence)
     :timeout-ms 80
     :target-length (Integer/parseInt (or words "24"))}))

(defn generate* [index params]
  (let [opts (params->opts params)]
    (log/info "Generating plot" opts)
    (m/generate @index opts)))

(defrecord Markovbot [a]
  p/Bot
  (init [_]
    (init-texts a))
  (generate [_ req]
    (generate* a (:params req))))

(defn bot []
  (->Markovbot (atom (m/markov-index-factory 2))))

(comment
  (def b (bot))
  (p/init b)
  (p/generate b nil)
  (-> b :a deref :forward-index count)

)
