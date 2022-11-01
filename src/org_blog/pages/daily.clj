(ns org-blog.pages.daily
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [org-crud.core :as org-crud]
   [garden.core :as garden]

   [org-blog.item :as item]
   [org-blog.render :as render]
   [org-blog.uri :as uri]
   [org-blog.config :as config]))

^{::clerk/no-cache true}
(def ^:dynamic *note*
  (-> (garden/daily-path #_2) org-crud/path->nested-item))

(defn note->daily-items [note]
  (some->> note :org/items (filter item/item-has-any-tags)))

(defn page [note]
  [:div
   [:h1 [:code (:org/name note)]]

   (->> note note->daily-items
        (map item/item->hiccup-content)
        (into [:div]))

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
