(ns com.joshuadavey.vecset
  (:import [clojure.lang ISeq IPersistentVector IPersistentSet Indexed IFn]))

(declare ->Vecset)

(deftype Vecset [^IPersistentVector v
                 ^IPersistentSet s]
  ISeq
  (seq [this] (seq v))
  (first [this] (.first v))
  (more [this] (.more v))
  (next [this] (.next v))
  (empty [this] (->Vecset [] #{}))
  (cons [this obj]
    (->Vecset (conj v obj) (conj s obj)))

  IFn
  (invoke [this obj] (.invoke s obj))
  (applyTo [this coll] (clojure.lang.AFn/applyToHelper this coll))

  Indexed
  (count [this] (.count v))
  (nth [this ^int n] (.nth v n))
  (nth [this ^int n not-found] (.nth v n not-found))

  IPersistentSet
  (disjoin [this obj] (throw (UnsupportedOperationException.)))
  (get [this obj] (.get s obj))
  (contains [this obj] (.contains s obj)))

(defn vecset
  "Given an ordered collection, returns an Vecset (collection
  usuable as both a vector and a set), as if by (set coll) and
  (vec coll).

  Collection behaves like a vector with regard to sequential
  operations, and like a set for set- and contains-like operations.

  The set serves as a simple equality index for the data in the
  vector. Duplicates are allowed in the vector."
  ([] (->Vecset [] #{}))
  ([coll]
   (->Vecset (vec coll) (set coll))))
