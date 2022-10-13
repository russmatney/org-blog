(ns org-blog.pages.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]
   [garden.core :as garden]

   [org-crud.core :as org-crud]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.pages.daily :as daily]
   [org-blog.publish :as publish]))

(defn some-dailies [] (->> (garden/daily-paths 14)
                           (map org-crud/path->nested-item)
                           (filter (comp seq daily/note->daily-items))))

^{::clerk/no-cache true}
(def ^:dynamic *notes*
  (->> (garden/all-garden-notes-nested)
       (remove (comp #(string/includes? % "/daily/") :org/source-file))
       shuffle
       (take 20)
       (concat (some-dailies))
       (into #{})))

(def this-ns *ns*)

(defn export
  [{:keys [notes]}]
  (println "[EXPORT] exporting index.")
  (with-bindings
    {#'org-blog.pages.index/*notes* notes}
    (render/path+ns-sym->spit-static-html
      (str "public/index.html")
      (symbol (str this-ns)))))

(defn note-index
  "TODO: someday, display as a graph
  TODO consider grouping by tags as a quick win"
  []
  (let [notes (->> *notes* (sort-by :file/last-modified))]
    (when notes
      (concat
        ["# Notes" ""]
        (->> notes
             (mapcat (fn [note]
                       [(str "- [" (:org/name note) "]("
                             (publish/id->link-uri (:org/id note)) ")")
                        (item/item->tag-line
                          {:include-child-tags true}
                          note)
                        ""
                        (when-let [created (:org.prop/created-at note)]
                          (when-let [parsed (dates/parse-time-string created)]
                            (str "created: " (t/format (t/formatter "MMMM dd") parsed))))
                        (when-let [modified (:file/last-modified note)]
                          (when-let [parsed (dates/parse-time-string modified)]
                            (str "modified: " (t/format (t/formatter "MMMM dd") parsed))))])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# russmatney blog"))

^{::clerk/no-cache true}
(clerk/md (->> (note-index) (string/join "\n")))
