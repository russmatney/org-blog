(ns org-blog.pages.daily
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [org-crud.core :as org-crud]
   [garden.core :as garden]

   [org-blog.item :as item]))

^{::clerk/no-cache true}
(def ^:dynamic *note*
  (-> (garden/daily-path #_2) org-crud/path->nested-item))

(defn note->daily-items [note]
  (some->> note :org/items (filter item/item-has-any-tags)))

(defn daily-content
  [note]
  (->>
    (note->daily-items note)
    (mapcat item/item->md-content)
    (string/join "\n")))

(comment
  (daily-content *note*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# " (:org/name *note*)))
(clerk/md (daily-content *note*))

^{::clerk/no-cache true}
(clerk/md (->> (item/backlinks (:org/id *note*)) (string/join "\n")))
