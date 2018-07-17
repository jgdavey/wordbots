(ns wordbots.markov.sql
  (:require [clojure.java.jdbc :as jdbc]
            [wordbots.protocols :as p]
            [wordbots.markov.utils :as mu]))

(extend-protocol jdbc/ISQLParameter
  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt i v)))))

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Array
  (result-set-read-column [v _ _]
    (into [] (.getArray v))))

(def connection-spec "postgresql://localhost:5432/markov")

(defn ensure-schema [conn]
  (jdbc/execute! conn "
create table if not exists chains(
  chain text not null,
  l text[] not null,
  r text not null,
  weight integer not null default 1,
  primary key (chain, l, r)
)"))

(def boundary "##END##")

(defn insert-tuple [conn chain tuple next]
  (jdbc/execute! conn
                 [(str "insert into chains as c(chain, l, r) values"
                       " (?, ?, ?)"
                       " on conflict (chain, l, r)"
                       " do update set weight = c.weight + 1")
                  chain
                  tuple
                  next]))

(defn init-texts [conn ^java.io.Reader rdr chain]
  (let [forward-index (atom {})
        reverse-index (atom {})
        forward-chain (str chain "_forward")
        reverse-chain (str chain "_reverse")]
    (doseq [line (line-seq rdr)]
      (let [tokens (mu/words line)]
        (swap! forward-index mu/index-tokens 2 tokens boundary)
        (swap! reverse-index mu/index-tokens 2 (reverse tokens) boundary)))
    (let [rowsets (->> (for [[prefix nexts] @forward-index
                             [next weight] nexts]
                         [forward-chain prefix (or next boundary) weight])
                       (partition-all 1000))]
      (doseq [rows rowsets]
        (jdbc/insert-multi! conn :chains [:chain :l :r :weight] rows)))
    (let [rowsets (->> (for [[prefix nexts] @reverse-index
                             [next weight] nexts]
                         [reverse-chain prefix (or next boundary) weight])
                       (partition-all 1000))]
      (doseq [rows rowsets]
        (jdbc/insert-multi! conn :chains [:chain :l :r :weight] rows)))))

(def select-sql
  (str
   "with recursive sentence as (
    select l || r as acc, r, array_length(l, 1) as i
    from (
      select *
      from chains
      where chain = ?
      and l[1] = ?::text
      order by random() * weight
      limit ?::integer
    ) c
    union all
    select s.acc || c.r, c.r, s.i + 1
    from sentence s
    left join lateral (
      select *
      from chains
      where chain = ?
      and l = acc[i:]
      order by random() * weight
      limit 1
    ) c on true
    where s.i < ?::integer
    and s.r <> '"boundary"'
  )
  select acc as tuples
  from sentence
  where r = '"boundary"'"))

(defn remove-boundaries [tuples]
  (remove #(or (nil? %) (= boundary %)) tuples))

(defn generate* [conn table start target-words tries]
  (map (fn [row] (update row :tuples remove-boundaries))
       (jdbc/query conn [select-sql
                         table
                         start
                         tries
                         table
                         (int (* target-words 2.5))])))

(defn generate [conn chain start target-words]
  (let [forwards (generate* conn (str chain "_forward") start (/ target-words 2) 20)
        backwards (if (= boundary start)
                    [nil]
                    (generate* conn (str chain "_reverse") start (/ target-words 2) 20))]
    (mu/best target-words
          (for [f forwards
                b backwards]
            {:tuples (into (->> b :tuples (drop 1) reverse vec)
                           (:tuples f))}))))


(defrecord MarkovSqlBot [connection-spec chain]
  p/Markov
  (-index [this rdr tokenizer-fn]
    (jdbc/with-db-connection [conn connection-spec]
      (init-texts conn chain rdr tokenizer-fn)))
  (-generate [this seed target-tuple-count]
    (jdbc/with-db-connection [conn connection-spec]
      (generate conn chain seed target-tuple-count))))

(comment
  (require '[clojure.java.io :as io])

  (def bot (->MarkovSqlBot connection-spec "plots"))

  (time
   (jdbc/with-db-connection [conn connection-spec]
     (ensure-schema conn)
     (with-open [rdr (io/reader (io/resource "plotbot/plots.txt") :encoding "ISO-8859-1")]
       (init-texts conn rdr "plots"))))

  (jdbc/with-db-connection [conn connection-spec]
    (time
     (generate conn "plots" boundary 24)))

  (jdbc/with-db-connection [conn connection-spec]
    (time
     (generate conn "plots" "email" 24)))

  )
