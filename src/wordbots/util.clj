(ns wordbots.util
  (:require [clojure.string :as str]))

(defn lines [text]
  (str/split text #"\n"))

(defn paragraphs [text]
  (str/split text #"\n\n"))

(defn presence [thing]
  (when (seq thing)
    thing))
