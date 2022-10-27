(ns user
  (:require
   [wing.repl :as repl]
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]
   [ralphie.notify :as notify]
   [clojure.string :as string]
   [org-blog.watcher :as watcher]
   [systemic.core :as sys]
   [org-blog.publish :as publish]))

(comment
  ;; TODO consider running this after clawe's suggested neil dep add
  ;; would need a cider/fire or cider/eval namespace that can connect to the running repl
  ;; from the one-off babashka process, that is
  (repl/sync-libs!))

;; go ahead and start this whenever the repl starts up
(clerk/serve! {:port 8888})
;; note that clerk/serve! clients do not reconnect, so evaling this breaks the sockets
(notify/notify "started clerk server on port 8888")

(sys/start! `watcher/*org-watcher*)
(notify/notify "started clerk org-watcher")

;; maybe this works
#_(clerk/show!
    (-> *file*
        (string/replace (fs/file-name *file*) "org_blog/export.clj")))


(comment
  (clerk/show!
    (-> *file*
        (string/replace (fs/file-name *file*) "org_blog/daily.clj")
        (doto (println "is clerk-show!ing"))))


  (publish/publish-all)

  )
