(ns wordbots.fightbot
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [com.joshuadavey.vecset :as v :refer [vecset]]
            [wordbots.protocols :as p]
            [wordbots.util :refer [lines paragraphs]]))

(def characters (atom (vecset)))
(def attributes (atom (vecset)))

(defn init* []
  (let [file->lines #(->> (io/resource %) slurp lines)]
    (reset! characters (-> "superfight/characters.txt" file->lines vecset))
    (reset! attributes (-> "superfight/attributes.txt" file->lines vecset))
    :ok))

(defn random-attribute []
  (let [attr (rand-nth @attributes)]
    (str/replace attr #"\b_\b" (rand-nth @characters))))

(defn random-attribute-unlike [attr]
  (let [a (random-attribute)]
    (if (= a attr)
      (recur attr)
      a)))

(defn generate-character []
  (let [c (rand-nth @characters)
        a1 (random-attribute)
        a2 (random-attribute-unlike a1)]
    [c a1 a2]))

(defn ^String capitalize [^String s]
  (str (.toUpperCase (subs s 0 1))
       (subs s 1)))

(defn generate* []
  (capitalize (str (str/join ", " (generate-character))
                   " *VS* "
                   (str/join ", " (generate-character)))))

(defrecord Fightbot []
  p/Bot
  (init [_] (init*))
  (generate [_ req] (generate*)))
(def fb (->Fightbot))
(def bot (constantly fb))

(comment
(p/init fb)
(p/generate fb {})
)
