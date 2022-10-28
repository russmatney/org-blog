(ns org-blog.publish
  (:require
   [clojure.string :as string]
   [org-blog.render :as render]
   [org-blog.notes :as notes]
   [org-blog.uri :as uri]
   [org-blog.pages.daily :as pages.daily]
   [org-blog.pages.note :as pages.note]
   [org-blog.pages.last-modified :as pages.last-modified]

   [ralphie.browser :as browser]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "publish funcs"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-notes []
  (let [notes-to-publish (notes/published-notes)]
    (doseq [note notes-to-publish]
      (println "[EXPORT] exporting note: " (:org/short-path note))
      (if (-> note :org/source-file (string/includes? "/daily/"))
        (with-bindings
          {#'pages.daily/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (uri/note->uri note))
            'org-blog.pages.daily))

        (with-bindings
          {#'pages.note/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (uri/note->uri note))
            'org-blog.pages.note))))))

(defn publish-index-by-tag []
  (println "[EXPORT] exporting index-by-tag.")
  (render/path+ns-sym->spit-static-html
    "public/tags.html" 'org-blog.pages.tags))

(defn publish-index-by-last-modified []
  (println "[EXPORT] exporting index-by-last-modified.")
  (render/write-page
    {:path    "public/last-modified.html"
     :content (pages.last-modified/page)
     :title   "By Modified Date"}))

(defn publish-index []
  (println "[EXPORT] exporting index.")
  (render/path+ns-sym->spit-static-html
    "public/index.html" 'org-blog.pages.index))

(defn publish-all
  ;; TODO delete notes that aren't here?
  []
  (publish-notes)
  (publish-index-by-tag)
  (publish-index-by-last-modified)
  (publish-index))

(comment
  (publish-all))


(defn refresh-browser []
  (browser/tabs)
  )
