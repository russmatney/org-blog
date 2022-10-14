(ns org-blog.watcher
  (:require
   [juxt.dirwatch :as dirwatch]
   [systemic.core :as sys :refer [defsys]]
   [babashka.fs :as fs]
   [nextjournal.clerk :as clerk]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.export :as export]))

(defn org-dir-path []
  (fs/file (str (fs/home) "/todo")))

(def should-sync-match-strs
  {:daily     #"/todo/daily/"
   :workspace #"/todo/garden/workspaces/"
   :garden    #"/todo/garden/"})

(defn should-sync-file? [file]
  (let [path (str file)]
    (and (#{"org"} (fs/extension file))
         (->> should-sync-match-strs
              (filter (fn [[k reg]]
                        (when (seq (re-seq reg path))
                          ;; (println "File matches pattern" k)
                          k)))
              first))))

(defsys *org-watcher*
  :start
  (println "Starting *org-watcher*")
  (dirwatch/watch-dir
    (fn [event]
      (println (:action event) "event" event)
      (when (and (not (#{:delete} (:action event)))
                 (should-sync-file? (:file event)))
        (println "[WATCH]: org file changed:" (fs/file-name (:file event)))

        ;; reparse org files
        (db/refresh-notes)

        ;; re-eval open clerk clients
        (clerk/recompute!)

        (when (config/export-mode?)
          (export/publish-all))))
    (org-dir-path))

  :stop
  ;; (log/debug "Closing *org-watcher*")
  (dirwatch/close-watcher *org-watcher*))

(comment
  (sys/start! `*org-watcher*)
  *org-watcher*
  (config/toggle-export-mode))
