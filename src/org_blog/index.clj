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

(def ^:dynamic *note-ids* (->> (garden/all-garden-notes-nested)
                               (remove (comp #(string/includes? % "/daily/") :org/source-file))
                               shuffle
                               (take 20)
                               (map :org/id)
                               (into #{})))

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

(defn id->link-header
  [id]
  (let [title (-> (db/all-flattened-notes-by-id id)
                  (item/item->title-content {:id->link-uri
                                             ;; drop header uris
                                             (fn [_] nil)}))]
    (str "## ["
         ;; may need to grab this without #s
         title "](" (*id->link-uri* id) ")" )))

(defn daily-index
  "TODO: someday, display as weekly/calendar view."
  []
  (let [dailies (->> *day-ids* (map db/fetch-with-id) (sort-by :org/name) reverse)]
    (when dailies
      (concat
        ["---" "" "# Dailies" ""]
        (->> dailies
             (mapcat (fn [daily]
                       [(str "- [" (:org/name daily) "]("
                             (*id->link-uri* (:org/id daily)) ")")])))))))

(defn note-index
  "TODO: someday, display as a graph."
  []
  (let [notes (->> *note-ids* (map db/fetch-with-id) (sort-by :org/name))]

    (when notes
      (concat
        ["---" "" "# Notes" ""]
        (->> notes
             (mapcat (fn [note]
                       [(str "- [" (:org/name note) "]("
                             (*id->link-uri* (:org/id note)) ")")])))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# Index"))

(clerk/md (->> (daily-index) (string/join "\n")))
(clerk/md (->> (note-index) (string/join "\n")))
