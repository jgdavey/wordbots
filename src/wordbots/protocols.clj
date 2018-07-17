(ns wordbots.protocols)

(defprotocol Bot
  (init [this])
  (generate [this req-body]))

(defprotocol Markov
  (-index [this ^java.io.Reader rdr tokenizer-fn])
  (-generate [this seed target-tuple-count]))
