(ns org-blog.pages.daily
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [nextjournal.clerk :as clerk]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.config :as config]
   [org-blog.publish :as publish]))

(def ^:dynamic *note*
  (-> (garden/daily-path 2) org-crud/path->nested-item))

(def this-ns *ns*)

(defn export
  [{:keys [note]}]
  (println "[EXPORT] exporting daily for: " (:org/short-path note))
  (with-bindings
    {#'org-blog.pages.daily/*note* note}
    (render/path+ns-sym->spit-static-html
      (str "public/" (publish/note->uri note))
      (symbol (str this-ns)))))

(defn note->daily-items [note]
  (some->> note :org/items (filter #(item/item-has-tags % config/allowed-tags))))

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
