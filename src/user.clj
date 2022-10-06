(ns user
  (:require
   [wing.repl :as repl]
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]
   [clojure.string :as string]))

;; go ahead and start this whenever the repl starts up
(clerk/serve! {:port 8888})


(comment
  (clerk/show!
    (-> *file*
        (string/replace (fs/file-name *file*) "org_blog/daily.clj")
        (doto (println "is clerk-show!ing"))))

  (repl/sync-libs!))
