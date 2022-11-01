(ns org-blog.watcher
  (:require
   [juxt.dirwatch :as dirwatch]
   [systemic.core :as sys :refer [defsys]]
   [babashka.fs :as fs]
   [nextjournal.clerk :as clerk]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.publish :as publish]
   [ralphie.browser :as browser])
  (:import [java.util Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; debounce
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debounce
  "https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a"
  ([f] (debounce f 1000))
  ([f timeout]
   (let [timer (Timer.)
         task  (atom nil)]
     (with-meta
       (fn [& args]
         (when-let [t ^TimerTask @task]
           (.cancel t))
         (let [new-task (proxy [TimerTask] []
                          (run []
                            (apply f args)
                            (reset! task nil)
                            (.purge timer)))]
           (reset! task new-task)
           (.schedule timer new-task timeout)))
       {:task-atom task}))))

(comment
  (def say-hello (debounce #(println "Hello" %1) 2000))

  (def say-hello (debounce #(println "Hello" %1) 2000))

  (say-hello "is it me you're looking for?")
  (say-hello "Lionel"))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; org watcher
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def on-org-file-change
  (debounce
    (fn [event]
      ;; reparse org files
      (db/refresh-notes)

      ;; re-eval open clerk clients
      (clerk/recompute!)

      (when (config/export-mode?)
        (publish/publish-note (:file event))
        (publish/publish-indexes)))
    1000))

;; TODO debounce this system
(defsys *org-watcher*
  :start
  (println "Starting *org-watcher*")
  (dirwatch/watch-dir
    (fn [event]
      (println (:action event) "event" event)
      (when (and (not (#{:delete} (:action event)))
                 (should-sync-file? (:file event)))
        (println "[WATCH]: org file changed:" (fs/file-name (:file event)))
        (on-org-file-change event)))
    (org-dir-path))

  :stop
  ;; (log/debug "Closing *org-watcher*")
  (dirwatch/close-watcher *org-watcher*))

(comment
  (sys/start! `*org-watcher*)
  *org-watcher*
  (config/toggle-export-mode))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public/ watcher
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn export-dir-path []
  (fs/file (config/blog-content-public)))

(def on-export-file-change
  (debounce
    (fn [_event]
      (println "[WATCH]: reloading export browser tabs")
      (browser/reload-tabs {:url-match "localhost:9999"}))
    1000))

(defsys *export-watcher*
  :start
  (println "Starting *export-watcher*")
  (dirwatch/watch-dir
    (fn [event]
      (println (:action event) "event" event)
      (when (not (#{:delete} (:action event)))
        (println "[WATCH]: export file changed:" (fs/file-name (:file event)))
        (on-export-file-change event)))
    (export-dir-path))

  :stop
  (dirwatch/close-watcher *export-watcher*))

(comment
  (sys/start! `*export-watcher*)
  *export-watcher*

  (publish/publish-index-by-last-modified))
