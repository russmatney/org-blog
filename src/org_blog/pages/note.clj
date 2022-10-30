(ns org-blog.pages.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]

   [org-blog.item :as item]
   [org-blog.db :as db]
   [org-blog.uri :as uri]
   [org-blog.render :as render]))

^{::clerk/no-cache true}
(def ^:dynamic *note*
  (->> (db/all-notes)
       ;; (remove (comp #(string/includes? % "/daily/") :org/source-file))
       (sort-by :file/last-modified)
       last))

(defn note->content
  [note]
  ;; TODO opt-in/out of note children?
  (->> note item/item->md-content (string/join "\n")))

(comment
  (item/item->md-content *note*)
  (note->content *note*))

(defn page [note]
  [:div
   (item/item->hiccup-content note)
   ])

(comment
  (render/write-page
    {:path    (str "public" (uri/note->uri *note*))
     :content (page *note*)
     :title   (:org/name *note*)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/no-cache true}
(clerk/html (page *note*))

^{::clerk/no-cache true}
(clerk/md (note->content *note*))

^{::clerk/no-cache true}
(clerk/md (->> (item/backlinks (:org/id *note*)) (string/join "\n")))
