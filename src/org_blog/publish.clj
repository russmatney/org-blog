(ns org-blog.publish
  (:require
   [clojure.string :as string]
   [org-blog.render :as render]
   [org-blog.notes :as notes]
   [org-blog.uri :as uri]
   [org-blog.pages.daily :as pages.daily]
   [org-blog.pages.note :as pages.note]
   [org-blog.pages.last-modified :as pages.last-modified]
   [org-blog.pages.index :as pages.index]
   [org-blog.pages.tags :as pages.tags]
   [org-blog.config :as config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "publish funcs"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-note [path-or-note]
  (let [note
        (if (and
              (map? path-or-note)
              (:org/source-file path-or-note)
              (= :level/root (:org/level path-or-note))) path-or-note
            (some->>
              (notes/published-notes)
              (filter (comp #{(str path-or-note)} :org/source-file))
              first))]
    (if-not note
      (println "[PUBLISH] could not find note" path-or-note)
      (do
        (println "[PUBLISH] exporting note: " (:org/short-path note))
        (if (-> note :org/source-file (string/includes? "/daily/"))
          (render/write-page
            {:path    (str (config/blog-content-public) (uri/note->uri note))
             :content (pages.daily/page note)
             :title   (:org/name note)})

          (render/write-page
            {:path    (str (config/blog-content-public) (uri/note->uri note))
             :content (pages.note/page note)
             :title   (:org/name note)}))))))

(defn publish-notes []
  (let [notes-to-publish (notes/published-notes)]
    (doseq [note notes-to-publish]
      (publish-note note))))

(defn publish-index-by-tag []
  (println "[PUBLISH] exporting index-by-tag.")
  (render/write-page
    {:path    (str (config/blog-content-public) "/tags.html")
     :content (pages.tags/page)
     :title   "Notes By Tag"}))

(defn publish-index-by-last-modified []
  (println "[PUBLISH] exporting index-by-last-modified.")
  (render/write-page
    {:path    (str (config/blog-content-public) "/last-modified.html")
     :content (pages.last-modified/page)
     :title   "Notes By Modified Date"}))

(defn publish-index []
  (println "[PUBLISH] exporting index.")
  (render/write-page
    {:path    (str (config/blog-content-public) "/index.html")
     :content (pages.index/page)
     :title   "Home"}))

(defn publish-indexes
  ;; TODO delete notes that aren't here?
  []
  (publish-index-by-tag)
  (publish-index-by-last-modified)
  (publish-index))

(defn publish-all
  ;; TODO delete notes that aren't here?
  []
  ;; TODO disable watchers before running this
  (publish-notes)
  (publish-indexes)
  (render/write-styles))

(comment
  (publish-all))
