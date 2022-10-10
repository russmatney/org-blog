(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [clojure.string :as string]
   [clojure.set :as set]
   [org-crud.core :as org-crud]
   [dates.tick :as dates.tick]

   [util :as util]
   [org-blog.daily :as daily]
   [org-blog.note :as note]
   [org-blog.db :as db]
   [ralphie.notify :as notify]))


(defonce ^:dynamic
  ;; TODO improve how days are collected (maybe an 'earliest-date' makes sense)
  *days-ago* 7)
(defonce ^:dynamic *days* (dates.tick/days *days-ago*))

(defonce published-ids (atom #{}))
(comment (reset! published-ids #{}))
(defonce linked-items (atom #{}))

(defn collect-linked-ids
  "Assumes published-ids is already set correctly"
  ([] (collect-linked-ids nil))
  ([opts]
   (let [days                  (:days opts *days*)
         published-daily-items (->> days (mapcat daily/items-for-day))
         published-notes       (->> @published-ids (map db/fetch-with-id))
         published             (concat published-daily-items published-notes)
         all-items             (->> published (mapcat org-crud/nested-item->flattened-items))
         all-links             (->> all-items (mapcat :org/links-to))]
     all-links)))

(defn reset-linked-items
  "Resets the linked-items lists, which is made up of linked (i.e. published) and missing items."
  []
  (let [linked-ids (->> (collect-linked-ids) (map :link/id) (into #{}))]
    (reset! linked-items (->> linked-ids (map db/fetch-with-id)))))

(defn note->uri [item]
  ;; TODO dry up uri creation (maybe in config?)
  (-> item :org/source-file note/->uri))

(defn days-with-prev+next
  "Builds a list of `{:prev <date> :day <date> :next <date>}` day groups.

  Useful for associating daily files so that prev/next daily links
  jump to the next day with content, skipping empty days."
  [days]
  (let [first-group [nil (first days) (second days)]
        groups
        (->>
          days
          (filter daily/items-for-day)
          (partition-all 3 1)
          (drop-last)
          (map #(into [] %)))]
    (->> (concat [first-group] groups)
         (map (fn [[prev day & next]]
                (cond->
                    {:day day}
                  prev       (assoc :prev prev)
                  (seq next) (assoc :next (first next))))))))

(comment
  (days-with-prev+next *days*)
  (->>
    *days*
    (filter daily/items-for-day)
    (partition-all 3 1)
    (filter (comp #(> % 1) count))))

(defn id->link-uri [id]
  (let [item (db/fetch-with-id id)]
    (if-not item
      (println "[WARN: bad data]: could not find org item with id:" id)
      (let [linked-id (:org/id item)]
        (if (@published-ids linked-id)
          ;; TODO to prefix or not to prefix - used as fs path or as href?
          (str "/" (note->uri item))

          ;; returning nil here to signal the link's removal
          (println "[INFO: missing link]: removing link to unpublished note: "
                   (:org/name item)))))))

(defn publish-dailies
  ([] (publish-dailies nil))
  ([opts]
   (let [days (->> (:days opts *days*) sort days-with-prev+next)]
     (doseq [{:keys [day prev next]} days]
       (daily/export-for-day
         {:day          day
          :previous-day prev
          :next-day     next
          :id->link-uri id->link-uri})))))

(comment
  (publish-dailies))

(defn publish-notes []
  (let [notes-to-publish (->> @published-ids (map db/fetch-with-id))]
    (doseq [note notes-to-publish]
      (note/export-note
        {:path         (:org/source-file note)
         :id->link-uri id->link-uri}))))

(comment
  (publish-notes))

(reset-linked-items)

(comment
  (def proc-gen-item
    (->>
      @linked-items
      (filter (comp #(string/includes? % "proc") :org/name))
      first))
  (swap! published-ids conj (:org/id proc-gen-item)))

(defn publish! [note-id]
  (notify/notify "publishing note" note-id)
  (swap! published-ids conj (util/ensure-uuid note-id)))

(defn unpublish! [note-id]
  (notify/notify "unpublishing note" note-id)
  (swap! published-ids disj (util/ensure-uuid note-id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; # export

^{::clerk/no-cache true
  ::clerk/viewer
  {:name         :publishing-notes
   :transform-fn clerk/mark-presented
   :render-fn
   '(fn [notes]
      (v/html
        [:div
         (for [[i note] (->> notes (map-indexed vector))]
           ^{:key i}
           [:div
            {:class ["flex" "flex-row" "space-x-4" "justify-between"]}

            ;; label
            [:div
             (-> note :org/name)]

            ;; actions
            [:div
             (when (not (:published note))
               [:button
                {:class    ["bg-green-700" "hover:bg-green-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/publish!
                                        ~(-> note :org/id :nextjournal/value :value))))}
                (str "publish: " (:org/name note))])

             (when (:published note)
               [:button
                {:class    ["bg-blue-700" "hover:bg-blue-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/unpublish!
                                        ~(-> note :org/id :nextjournal/value :value))))}
                (str "unpublish: " (:org/name note))])]])]))}}
(->> @linked-items
     (map (fn [item] (assoc item :published (@published-ids (:org/id item)))))
     (sort-by :published)
     (into []))



;; ### published items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @published-ids (map db/fetch-with-id) seq)
    [{:no-data nil}]))

;; ### missing items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @linked-items (map :org/id) (into #{}) (#(set/difference % @published-ids))
         (map db/fetch-with-id) seq)
    [{:no-data nil}]))

;; ### all linked items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @linked-items seq)
    [{:no-data nil}]))
