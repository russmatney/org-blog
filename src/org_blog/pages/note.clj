(ns org-blog.pages.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]

   [org-blog.item :as item]
   [org-blog.db :as db]
   [org-blog.uri :as uri]
   [org-blog.render :as render]
   [org-blog.config :as config]
   [org-blog.export :as export]
   [flames.core :as flames]))

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
  (tap> *note*)
  (render/write-page
    {:path    (str (config/blog-content-public) (uri/note->uri *note*))
     :content (page *note*)
     :title   (:org/name *note*)}))

(defn linked-to [note]
  (let [links       (item/item->all-links note)
        notes-by-id (db/notes-by-id)]
    (->> links
         (map :link/id)
         (map notes-by-id))))

(comment
  *note*

  (let [f (flames/start! {:port 5555 :host "localhost"})]
    (clerk/show! 'org-blog.pages.note)
    (spit "flames.svg" (slurp "http://localhost:5555/flames.svg"))
    (flames/stop! f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/viewer   export/note-publish-buttons
  ::clerk/width    :wide
  ::clerk/no-cache true}
(->>
  (concat
    [*note*]
    (linked-to *note*))
  (map export/decorate-note)
  (sort-by :published))


^{::clerk/no-cache true}
(clerk/html (page *note*))
