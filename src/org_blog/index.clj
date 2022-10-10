(ns org-blog.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]

   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.db :as db]))

(def ^:dynamic *day-ids* (->> (garden/daily-paths 14)
                              (filter fs/exists?)
                              (map org-crud/path->nested-item)
                              (map :org/id)
                              (remove nil?)
                              (into #{})))

^{::clerk/no-cache true}
(def ^:dynamic *note-ids* (->> (garden/all-garden-notes-nested)
                               (remove (comp #(string/includes? % "/daily/") :org/source-file))
                               shuffle
                               (take 20)
                               (map :org/id)
                               (into #{})))

(comment
  (->> *note-ids*
       (map db/notes-by-id)
       (remove :org/name)))

(def ^:dynamic *id->link-uri*
  (fn [_]
    ;; NOTE do not publish with this val
    ;; this just helps dev on the notes page by including backlinks
    "some-link"))

(def this-ns *ns*)

(defn export-index
  [{:keys [id->link-uri day-ids note-ids]}]
  (println "[EXPORT] exporting index.")
  (with-bindings
    {#'org-blog.index/*id->link-uri* (or id->link-uri *id->link-uri*)
     #'org-blog.index/*day-ids*      day-ids
     #'org-blog.index/*note-ids*     note-ids}
    (render/path+ns-sym->spit-static-html
      (str "public/index.html")
      (symbol (str this-ns)))))

(defn daily-index
  "TODO: someday, display as weekly/calendar view.
  TODO gather tags from children
  "
  []
  (let [dailies (->> *day-ids* (map db/fetch-with-id) (sort-by :org/name) reverse)]
    (when dailies
      (concat
        ["---" "" "# Dailies" ""]
        (->> dailies
             (mapcat (fn [daily]
                       [(str "- [" (:org/name daily) "]("
                             (*id->link-uri* (:org/id daily)) ")")
                        (item/item->tag-line daily)])))))))

(defn note-index
  "TODO: someday, display as a graph
  TODO consider grouping by tags as a quick win"
  []
  (let [notes (->> *note-ids* (map db/fetch-with-id) (sort-by :org/name))]
    (when notes
      (concat
        ["---" "" "# Notes" ""]
        (->> notes
             (mapcat (fn [note]
                       [(str "- [" (:org/name note) "]("
                             (*id->link-uri* (:org/id note)) ")")
                        (item/item->tag-line note)])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# Index"))

^{::clerk/no-cache true}
(clerk/md (->> (daily-index) (string/join "\n")))
^{::clerk/no-cache true}
(clerk/md (->> (note-index) (string/join "\n")))
