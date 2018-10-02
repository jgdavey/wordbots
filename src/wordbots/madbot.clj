(ns wordbots.madbot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [wordbots.protocols :as p]
            [wordbots.template :as template]
            [wordbots.util :refer [lines paragraphs]]))

(def index (atom {:adjective nil}))

(def mappings
  {:transitive-action "madbot/actions_transitive.txt"
   :intransitive-action "madbot/actions_intransitive.txt"
   :adjective  "madbot/adjectives.txt"
   :animal     "madbot/animals.txt"
   :number     "madbot/numbers.txt"
   :body-part  "madbot/body_parts.txt"
   :comparator "madbot/comparators.txt"})

(defn lines-from-resource [filename]
  (with-open [rdr (-> filename io/resource io/reader)]
    (into [] (line-seq rdr))))

(defn init* []
  (doseq [[part file] mappings]
    (let [w (lines-from-resource file)]
      (swap! index assoc part w))))

(defn modify-dispatch [text modifier]
  (some-> modifier
          :modifier/name
          keyword))

(defmulti modify modify-dispatch :default ::pass-through)

(defmethod modify ::pass-through [text _]
  text)

(defn capitalize [^String text]
  (str
   (.toUpperCase (subs text 0 1))
   (subs text 1)))

(defn pluralize [^String text]
  (if (.endsWith text "s")
    text
    (str text "s")))

(defmethod modify :capitalize [text _]
  (capitalize text))

(defmethod modify :plural [text _]
  (pluralize text))

(defn replace? []
  (> (rand) 0.1))

(defn maybe-random-entry [part-of-speech]
  (when (replace?)
    (when-let [idx (get @index (keyword part-of-speech))]
      (rand-nth idx))))

(defn extract-default [args]
  (some #(when (= 'default (:modifier/name %))
           (first (:modifier/args %)))
        args))

(defn madlib-word [sym & args]
  (if-let [replacement (maybe-random-entry sym)]
    (reduce modify replacement args)
    (extract-default args)))

(defn generate* [proverb-template]
  (template/evaluate proverb-template madlib-word))

(defrecord Madbot [proverbs]
  p/Bot
  (init [_] (init*))
  (generate [_ req] (generate* (or (get-in req [:params :proverb])
                                   (rand-nth proverbs)))))

(defn bot []
  (->Madbot
   (lines-from-resource "madbot/parsed.txt")))

(comment
  (def b (bot))
  (p/init b)
  (p/generate b))
