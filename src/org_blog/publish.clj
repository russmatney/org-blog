(ns org-blog.publish
  (:require [org-blog.db :as db]
            [org-blog.config :as config]
            [babashka.fs :as fs]
            [clojure.string :as string]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "published notes"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO consider a systemic system
(defn published-notes []
  (->> (config/note-defs)
       (map (fn [{:keys [org/short-path]}]
              (db/notes-by-short-path short-path)))))

(defn published-ids []
  (->> (published-notes) (map :org/id) (into #{})))

(defn published-id? [id]
  ((published-ids) id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "uri"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->uri [path]
  (-> path fs/file-name fs/strip-ext
      (#(str (if (string/includes? path "/daily/")
               "daily"
               "note")
             "/" % ".html"))))

(defn note->uri [note]
  (-> note :org/source-file path->uri))

(defn id->link-uri
  "Passed into org-crud to determine if a text link should be included or ignored."
  [id]
  (let [note (db/fetch-with-id id)]
    (if-not note
      (println "[WARN: bad data]: could not find org note with id:" id)
      (let [linked-id (:org/id note)]
        (if (published-id? linked-id)
          ;; TODO handle uris more explicitly (less '(str "/" blah)' everywhere)
          (str "/" (note->uri note))

          (do
            #_(println "[INFO: missing link]: skipping link to unpublished note: "
                       (:org/name note))
            ;; returning nil here to signal the link's removal
            nil))))))
