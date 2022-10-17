(ns org-blog.uri
  (:require
   [babashka.fs :as fs]
   [clojure.string :as string]

   [org-blog.db :as db]
   [org-blog.notes :as notes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "uri"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->uri [path]
  (-> path fs/file-name fs/strip-ext
      (#(str (if (string/includes? path "/daily/")
               "/daily"
               "/note")
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
        (if (notes/published-id? linked-id)
          (note->uri note)

          (do
            #_(println "[INFO: missing link]: skipping link to unpublished note: "
                       (:org/name note))
            ;; returning nil here to signal the link's removal
            nil))))))
