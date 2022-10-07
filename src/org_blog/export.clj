(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [org-blog.daily :as daily]
   [dates.tick :as dates.tick]
   [nextjournal.clerk :as clerk]

   [util :refer [ensure-uuid]]
   [garden.core :as garden]))

(def all-notes
  (garden/all-garden-notes-nested))

(def notes-by-id
  (->>
    all-notes
    (map (fn [n] [(:org/id n) n]))
    (into {})))

(comment
  (count all-notes)
  (count notes-by-id))

(defonce linked-ids (atom #{}))
(defonce linked-items (atom #{}))
(defn reset-linked-items []
  (reset! linked-items #{}))

(defn export-dailies
  ([] (export-dailies nil))
  ([opts]
   (let [days-ago (:days-ago opts 7)
         days     (dates.tick/days days-ago)]
     (reset-linked-items)
     (doseq [day days]
       (daily/export-for-day
         {:day day
          :id->link-uri
          (fn [id]
            (let [item (notes-by-id (ensure-uuid id))]
              (when-not item
                (println "could not find org item with id:" id))
              (when item
                (swap! linked-items conj item))
              (swap! linked-ids conj id)
              nil))}))

     (println "linked ids" @linked-ids))))

(comment
  (export-dailies {:days-ago 7}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; # export

;; ### 'missing' links
(clerk/table
  {::clerk/width :full}
  (->>
    @linked-items
    seq
    #_(map (fn [id] {:id id}))))
