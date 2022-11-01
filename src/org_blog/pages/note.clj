(ns org-blog.pages.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]

   [org-blog.item :as item]
   [org-blog.db :as db]
   [org-blog.uri :as uri]
   [org-blog.render :as render]
   [org-blog.config :as config]))

^{::clerk/no-cache true}
(def ^:dynamic *note*
  (->> (db/all-notes)
       ;; (remove (comp #(string/includes? % "/daily/") :org/source-file))
       (sort-by :file/last-modified)
       last))

(defn page [note]
  [:div
   (item/item->hiccup-content note)
   (item/id->backlink-hiccup (:org/id *note*))])


(comment
  (render/write-page
    {:path    (str (config/blog-content-public) (uri/note->uri *note*))
     :content (page *note*)
     :title   (:org/name *note*)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/no-cache true}
(clerk/html (page *note*))
