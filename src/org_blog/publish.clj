(ns org-blog.publish
  (:require
   [babashka.fs :as fs]
   [clojure.string :as string]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.render :as render]
   [org-blog.pages.daily :as daily]
   [org-blog.pages.note :as note]))

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
        (if (published-id? linked-id)
          (note->uri note)

          (do
            (println "[INFO: missing link]: skipping link to unpublished note: "
                     (:org/name note))
            ;; returning nil here to signal the link's removal
            nil))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "publish funcs"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-notes []
  (let [notes-to-publish (published-notes)]
    (doseq [note notes-to-publish]
      (println "[EXPORT] exporting note: " (:org/short-path note))
      (if (-> note :org/source-file (string/includes? "/daily/"))
        (with-bindings {daily/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (note->uri note))
            'org-blog.pages.daily))

        (with-bindings {note/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (note->uri note))
            'org-blog.pages.note))))))

(defn publish-index []
  (println "[EXPORT] exporting index.")
  (render/path+ns-sym->spit-static-html
    (str "public/index.html") 'org-blog.pages.index))

(defn publish-all
  ;; TODO delete notes that aren't here?
  []
  (publish-notes)
  (publish-index))

(comment
  (publish-notes)
  (publish-index)
  (publish-all))
