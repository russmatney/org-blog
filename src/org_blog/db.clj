(ns org-blog.db
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [util :refer [ensure-uuid]]
   [garden.core :as garden]))

(def all-notes
  (garden/all-garden-notes-nested))

(def notes-by-id
  (->>
    all-notes
    (map (fn [n] [(:org/id n) n]))
    (into {})))

(defn fetch-with-id [id]
  (notes-by-id (ensure-uuid id)))

(comment
  (count all-notes)
  (count notes-by-id))
