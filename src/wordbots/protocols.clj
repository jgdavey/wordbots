(ns wordbots.protocols)

(defprotocol Bot
  (init [this])
  (generate [this req-body]))
