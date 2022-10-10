(ns org-blog.db
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [util :refer [ensure-uuid]]
   [garden.core :as garden]
   [org-crud.core :as org-crud]))

(println "parsing all notes")
(def all-notes
  (garden/all-garden-notes-nested))

(def notes-by-id
  (->>
    all-notes
    (map (fn [n] [(:org/id n) n]))
    (into {})))

(def root-note-by-child-id
  (->>
    notes-by-id
    (mapcat (fn [[p-id note]]
              (concat
                [[p-id note]]
                (->> note
                     org-crud/nested-item->flattened-items
                     (map :org/id)
                     (remove nil?)
                     (map (fn [c-id]
                            [c-id p-id]))))))
    (into {})))

(defn root-note-for-any-id [id]
  (let [note (root-note-by-child-id id)]
    (if (map? note)
      note
      (notes-by-id note))))

(defn fetch-with-id [id]
  (root-note-for-any-id (ensure-uuid id)))

(comment
  (count all-notes)
  (count notes-by-id))
