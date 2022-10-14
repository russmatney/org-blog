(ns org-blog.notes
  (:require
   [babashka.fs :as fs]
   [clojure.string :as string]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.render :as render]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "published notes"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn published-notes []
  (->> (config/note-defs)
       (map (fn [{:keys [org/short-path]}]
              ((db/notes-by-short-path) short-path)))))

(defn published-ids []
  (->> (published-notes) (map :org/id) (into #{})))

(defn published-id? [id]
  ((published-ids) id))
