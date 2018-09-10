(ns wordbots.template
  (:require [instaparse.core :as insta :refer [defparser]]
            [clojure.string :as str]))

(def grammar
  "
  tag         := <'{{'> <ws>* directive <ws>* <'}}'>
  <directive> := pos modifiers
  <pos>       := symbol
  <modifiers> := (<ws>* <'|'> <ws>* modifier (<ws>* <','> <ws>* modifier)*)?
  modifier    := symbol (<':'> <ws>* args)?
  args        := arg (<ws>+ arg)*
  <arg>       := number | symbol | string | keyword
  number      := #'[1-9][0-9]*'
  string      := <'\"'> #'[^\"]*' <'\"'>
  symbol      := #'[a-zA-Z][a-zA-Z0-9-]*'
  keyword     := <':'> #'[a-zA-Z][a-zA-Z0-9-]*'
  <ws>        := ' ' | '\n'
  ")

(defparser parser grammar)

(def tag-pattern #"\{\{[^\}\}]+?\}\}")

(defn matches
  "Returns a lazy sequence of successive matches, with start/end locations"
  [re string]
  (let [m (re-matcher re string)]
    ((fn step []
       (when (. m (find))
         (cons #:match{:start (.start m)
                       :end (.end m)
                       :match (re-groups m)}
               (lazy-seq (step))))))))

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
  (map (fn [{:keys [type string tag]}]
         (case type
           :literal [:literal string]
           :tag (insta/parse parser tag)))
       (invert-template template)))

(defn transform* [parsed-template transform-map-overrides]
  (insta/transform (merge {:symbol symbol
                           :string str
                           :keyword keyword
                           :number #(Long/parseLong %)
                           :literal identity
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
  (apply str
         (transform* (parse template) {:tag tag-fn})))
