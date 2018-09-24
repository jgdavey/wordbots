(ns wordbots.template
  (:require [instaparse.core :as insta :refer [defparser]]
            [clojure.string :as str]))

;; Although technically correct, this grammar is a bit sluggish on a
;; whole document (because of the character-wise negative lookahead in
;; the word directive). Rather than apply the parser to a whole
;; document, use the template-invert function to get the same
;; structure at a cheaper price.
(def grammar
  "
  doc         := (tag | literal)*
  literal     := wordish+
  <wordish>   := (word | ws*)*
  <word>      := #'((?!\\{\\{)[^\\s])*'
  tag         := <'{{'> <ws>* directive <ws>* <'}}'>
  <directive> := pos modifiers
  <pos>       := symbol
  <modifiers> := (<ws>* <'|'> <ws>* modifier (<ws>* <','> <ws>* modifier)*)?
  modifier    := symbol (<':'> <ws>* args)?
  args        := arg (<ws>+ arg)*
  <arg>       := number | symbol | string | keyword
  <identifier> := #'[a-zA-Z][a-zA-Z0-9-]*'
  number      := #'[1-9][0-9]*'
  string      := <'\"'> #'[^\"]*' <'\"'>
  symbol      := identifier
  keyword     := <':'> identifier
  <ws>        := #'\\s+'
  ")

(defparser parser grammar)

(defn matches
  "Returns a lazy sequence of successive matches (a la re-groups), with
  start/end locations"
  [re string]
  (let [m (re-matcher re string)]
    ((fn step []
       (when (. m (find))
         (cons #:match{:start (.start m)
                       :end (.end m)
                       :match (re-groups m)}
               (lazy-seq (step))))))))

(def tag-pattern #"\{\{[^\}\}]+?\}\}")

(defn invert-template [template]
  (let [tags (matches tag-pattern template)
        default [0 (count template)]
        literals (reduce
                  (fn [acc {:match/keys [start end match]}]
                    (let [{[ls le] :loc} (peek acc)]
                      (cond-> (pop acc)
                        (< ls start) (conj {:type :literal
                                            :string (subs template ls start)
                                            :loc [ls start]})
                        :always      (conj {:type :tag
                                            :loc [start end]
                                            :tag match})
                        (> le end)   (conj {:type :literal
                                            :string (subs template end le)
                                            :loc [end le]}))))
                  [{:type :literal
                    :string template
                    :loc default}]
                  tags)]
    literals))

(defn parse [template]
  (into [:doc]
        (map (fn [{:keys [type string tag]}]
               (case type
                 :literal [:literal string]
                 :tag (insta/parse parser tag :start :tag)))
             (invert-template template))))

(defn transform* [parsed-template transform-map-overrides]
  (insta/transform (merge {:doc str
                           :symbol symbol
                           :string str
                           :keyword keyword
                           :number #(Long/parseLong %)
                           :literal str
                           :args vector
                           :modifier (fn [name & [args]]
                                       {:modifier/name name
                                        :modifier/args args})}
                          transform-map-overrides)
                   parsed-template))

(defn evaluate
  "Given a template and a tag-fn, return the resulting string. tag-fn is
  variadic function of the form (fn [tag & modifiers])"
  [template tag-fn]
  (transform* (parse template) {:tag tag-fn}))
