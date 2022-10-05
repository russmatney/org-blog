(ns user
  (:require
   [wing.repl :as repl]
   [nextjournal.clerk :as clerk]))

(comment
  (repl/sync-libs!)
  (clerk/serve! {:port 8888}))
