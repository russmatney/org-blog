(ns org-blog.pages.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]

   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.publish :as publish]))

(def ^:dynamic *note*
  (->> (garden/flat-garden-paths)
       (filter (comp #(string/includes? % "async_mario")))
       first org-crud/path->nested-item))

(def this-ns *ns*)

(defn export
  [{:keys [note]}]
  (println "[EXPORT] exporting note: " (:org/short-path note))
  (with-bindings {#'org-blog.pages.note/*note* note}
    (render/path+ns-sym->spit-static-html
      (str "public/" (publish/note->uri note))
      (symbol (str this-ns)))))

(defn note->content
  [note]
  ;; TODO opt-in/out of note children?
  (->> note item/item->md-content (string/join "\n")))

(comment
  (item/item->md-content *note*)
  (note->content *note*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/no-cache true}
(clerk/md (str "# " (:org/name *note*)))

^{::clerk/no-cache true}
(clerk/md (note->content *note*))

^{::clerk/no-cache true}
(clerk/md (->> (item/backlinks (:org/id *note*)) (string/join "\n")))
