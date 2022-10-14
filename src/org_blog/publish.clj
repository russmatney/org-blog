(ns org-blog.publish
  (:require
   [clojure.string :as string]
   [org-blog.render :as render]
   [org-blog.notes :as notes]
   [org-blog.uri :as uri]
   [org-blog.pages.daily :as daily]
   [org-blog.pages.note :as note]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "publish funcs"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-notes []
  (let [notes-to-publish (notes/published-notes)]
    (doseq [note notes-to-publish]
      (println "[EXPORT] exporting note: " (:org/short-path note))
      (if (-> note :org/source-file (string/includes? "/daily/"))
        (with-bindings
          {#'daily/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (uri/note->uri note))
            'org-blog.pages.daily))

        (with-bindings
          {#'note/*note* note}
          (render/path+ns-sym->spit-static-html
            (str "public" (uri/note->uri note))
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
