(ns wordbots.util
  (:require [clojure.string :as str]))

(def lines str/split-lines)

(def presence not-empty)

(defn clean-word "return only chars"
  [^String s]
  (str/replace s #"[^a-zA-Z-]" ""))

(defn paragraphs [^String text]
  (str/split text #"\r?\n\r?\n"))

(defn map-entry [k v]
  (clojure.lang.MapEntry/create k v))
