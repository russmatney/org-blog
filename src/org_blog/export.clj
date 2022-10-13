(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [clojure.set :as set]
   [org-crud.core :as org-crud]
   [dates.tick :as dates.tick]

   [util :as util]
   [org-blog.daily :as daily]
   [org-blog.note :as note]
   [org-blog.db :as db]
   [ralphie.notify :as notify]
   [org-blog.index :as index]
   [tick.core :as t]
   [garden.core :as garden]
   [org-blog.config :as config]
   [org-blog.item :as item]))


;; TODO drop completely, add ui for publishing dailies as regular notes
(def ^:dynamic *days-ago* 14)
(def ^:dynamic *days* (dates.tick/days *days-ago*))

;; TODO could this just be a def? not sure we need the atom
(defonce linked-items (atom #{}))

(defn published-notes []
  (->> (config/note-defs)
       (map (fn [{:keys [org/short-path]}]
              (db/notes-by-short-path short-path)))))

(defn published-ids []
  (->> (published-notes) (map :org/id) (into #{})))

(defn published-id? [id]
  ((published-ids) id))

(defn days->ids
  ([] (days->ids *days*))
  ([days]
   (->> days
        (filter daily/items-for-day)
        (map (comp :org/id org-crud/path->nested-item garden/daily-path))
        (remove nil?)
        (into #{}))))

(def day-ids (days->ids))

(defn collect-linked-ids
  "Collects ref (normal) links and backlinks for the notes in `published-notes`.
  Returns a set of uuids."
  ([] (collect-linked-ids nil))
  ([_opts]
   (let [all-items        (->> (published-notes) (mapcat org-crud/nested-item->flattened-items))
         all-link-ids     (->> all-items (mapcat :org/links-to) (map :link/id) (into #{}))
         all-item-ids     (->> all-items (map :org/id) (remove nil?) (into #{}))
         all-backlink-ids (->> all-item-ids (mapcat db/ids-linked-from) (into #{}))]
     (->> (concat all-backlink-ids all-link-ids) (into #{})))))

(defn reset-linked-items
  "Resets the linked-items lists, which is made up of both published and missing items."
  []
  (reset! linked-items (->> (collect-linked-ids) (map db/fetch-with-id))))

;; TODO refactor into one note timeline (not traversing dailies, traversing notes by created-at)
(defn days-with-prev+next
  "Builds a list of `{:prev <date> :day <date> :next <date>}` day groups.

  Useful for associating daily files so that prev/next daily links
  jump to the next day with content, skipping empty days."
  [days]
  (let [days         (->> days (filter daily/items-for-day))
        first-group  [nil (first days) (second days)]
        second-group [(first days) (second days) (nth days 2)]
        groups
        (->>
          days
          (partition-all 3 1)
          (drop-last)
          (map #(into [] %)))]
    (->> (concat [first-group
                  second-group]
                 groups)
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

(defn id->link-uri
  "Passed into org-crud to determine if a text link should be included or ignored.

  Depends on `db/fetch-with-id` (plus others), `item/item->uri`, and `config/published-notes`.

  This is passed into the pages... should move this elsewhere now that the state doesn't live
  in this namespace, so the pages can call this directly.
  "
  [id]
  (let [item (db/fetch-with-id id)]
    (if-not item
      (println "[WARN: bad data]: could not find org item with id:" id)
      (let [linked-id (:org/id item)]
        (if (published-id? linked-id)
          ;; TODO handle uris more explicitly (less '(str "/" blah)' everywhere)
          (str "/" (item/item->uri item))

          (do
            (println "[INFO: missing link]: removing link to unpublished note: "
                     (:org/name item))
            ;; returning nil here to signal the link's removal
            nil))))))

;; TODO probably drop/combine with publish-notes
(defn publish-dailies
  ([] (publish-dailies nil))
  ([opts]
   (let [days (->> (:days opts *days*) sort days-with-prev+next)]
     ;; TODO the daily render should not depend on export - should depend directly on notes from config
     (doseq [{:keys [day prev next]} days]
       (daily/export-for-day
         {:day          day
          :previous-day prev
          :next-day     next
          :id->link-uri id->link-uri})))))

(defn publish-notes
  ([] (publish-notes nil))
  ([_]
   (let [notes-to-publish (published-notes)]
     ;; TODO the note render should not depend on export - should depend directly on notes from config
     (doseq [note notes-to-publish]
       (note/export-note
         {:path         (:org/source-file note)
          :id->link-uri id->link-uri})))))

(defn publish-index
  ([] (publish-index nil))
  ([_opts]
   ;; TODO the index should not depend on export - should depend directly on notes from config
   (index/export-index
     {:id->link-uri id->link-uri
      :day-ids      day-ids
      :note-ids     @published-ids})))

(defn publish-all
  ([] (publish-all nil))
  ([opts]
   (publish-dailies opts)
   (publish-notes opts)
   (publish-index opts)))

(comment
  (publish-dailies)
  (publish-notes)
  (publish-index)

  (publish-all))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ actions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish! [note-id]
  (notify/notify "publishing note" note-id)
  (let [note (-> note-id db/notes-by-id)
        def  {:org/short-path (:org/short-path note)}]
    (config/persist-note-def def)))

(defn unpublish! [note-id]
  (notify/notify "unpublishing note" note-id)
  (let [note       (-> note-id db/notes-by-id)
        short-path (:org/short-path note)]
    (config/drop-note-def short-path)))

(defn open-in-emacs! [note-id]
  (notify/notify "open in emacs!" "not impled")
  nil)


(defn select-org-keys [item]
  (select-keys item
               [:org/name
                ;; :org/short-path
                :org/tags
                ;; :org/links-to
                :org/level
                ;; :org/id
                :org/body-string
                ]))

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
     (map (fn [item] (assoc item :published (published-id? (:org/id item)))))
     (sort-by :published)
     (into []))


;; ### published items
(clerk/table
  {::clerk/width :full}
  (or
    (->> (published-notes)
         (map select-org-keys)
         seq)
    [{:no-data nil}]))

;; ### unpublished, linked items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @linked-items
         (remove (fn [{:keys [org/id]}] (published-id? id)))
         (map select-org-keys)
         seq)
    [{:no-data nil}]))
