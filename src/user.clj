(ns user
  (:require
   [wing.repl :as repl]
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]
   [ralphie.notify :as notify]
   [clojure.string :as string]))

;; go ahead and start this whenever the repl starts up
(clerk/serve! {:port 8888})
(notify/notify "started clerk server on port 8888"
               "unconfirmed")

(comment
  (clerk/show!
    (-> *file*
        (string/replace (fs/file-name *file*) "org_blog/daily.clj")
        (doto (println "is clerk-show!ing"))))

  (repl/sync-libs!))
