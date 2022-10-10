(ns org-blog.db
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [util :refer [ensure-uuid]]
   [garden.core :as garden]
   [org-crud.core :as org-crud]))

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

(def root-ids-by-link-id
  (->>
    all-notes
    (mapcat org-crud/nested-item->flattened-items)
    (reduce
      (fn [agg item]
        (if (:org/id item)
          (let [link-ids (->> item :org/links-to (map :link/id))]
            (reduce (fn [agg link-id]
                      (if (get agg link-id)
                        (update agg link-id conj (:org/id item))
                        (assoc agg link-id #{(:org/id item)})))
                    agg
                    link-ids))
          agg))
      {})))

(defn ids-linked-from
  "Returns a list of items that link to the passed id."
  [id]
  (root-ids-by-link-id id))

(defn notes-linked-from
  "Returns a list of items that link to the passed id."
  [id]
  (->> (ids-linked-from id)
       (map fetch-with-id)))

(comment
  (fetch-with-id #uuid "8b22b22a-c442-4859-9927-641f8405ec8d")
  (notes-linked-from #uuid "8b22b22a-c442-4859-9927-641f8405ec8d")

  (reduce (fn [agg next]
            (println "agg" agg)
            (+ agg next))
          0
          [1 3 5]))
