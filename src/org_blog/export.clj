(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]
   [org-crud.core :as org-crud]

   [garden.core :as garden]
   [ralphie.notify :as notify]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.publish :as publish]
   [org-blog.item :as item]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "recent daily notes"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def recent-file-count 14)

(defn recent-notes
  []
  (->> (garden/all-garden-paths)
       (sort-by (comp str fs/last-modified-time))
       (reverse)
       (take recent-file-count)
       (map org-crud/path->nested-item)
       (remove nil?)))

(comment
  (recent-notes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "linked ids"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collect-linked-ids
  "Collects ref (normal) links and backlinks for the notes in `published-notes`.
  Returns a set of uuids."
  ([] (collect-linked-ids nil))
  ([_opts]
   (let [all-items        (->> (publish/published-notes) (mapcat org-crud/nested-item->flattened-items))
         all-link-ids     (->> all-items (mapcat :org/links-to) (map :link/id) (into #{}))
         all-item-ids     (->> all-items (map :org/id) (remove nil?) (into #{}))
         all-backlink-ids (->> all-item-ids (mapcat db/ids-linked-from) (into #{}))]
     (->> (concat all-backlink-ids all-link-ids) (into #{})))))

^{::clerk/no-cache true}
(def linked-items (->> (collect-linked-ids)
                       (map (db/notes-by-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ actions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish-note! [short-path]
  (notify/notify "publishing note" short-path)
  (let [note ((db/notes-by-short-path) short-path)
        def  {:org/short-path (:org/short-path note)}]
    (config/persist-note-def def)))

(defn unpublish-note! [short-path]
  (notify/notify "unpublishing note" short-path)
  (let [note       ((db/notes-by-short-path) short-path)
        short-path (:org/short-path note)]
    (config/drop-note-def short-path)))

#_(defn open-in-emacs! [_note-id]
    (notify/notify "open in emacs!" "not impled")
    nil)

(def note-publish-buttons
  {:name         :publishing-notes
   :transform-fn clerk/mark-presented
   :render-fn
   '(fn [notes]
      (v/html
        [:div
         (for [[i note] (->> notes (map-indexed vector))]
           ^{:key i}
           [:div
            {:class ["flex" "flex-row" "space-x-4" "justify-between" "align-center"]}

            ;; label
            [:div
             (-> note :org/name)]

            [:div
             (-> note :file/last-modified)]

            [:div
             (->> note :all-tags (clojure.string/join ":"))]

            ;; actions
            [:div
             (when (not (:published note))
               [:button
                {:class    ["bg-green-700" "hover:bg-green-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/publish-note!
                                        ~(-> note :org/short-path))))}
                (str "publish: " (:org/name note))])

             (when (:published note)
               [:button
                {:class    ["bg-blue-700" "hover:bg-blue-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/unpublish-note!
                                        ~(-> note :org/short-path))))}
                (str "unpublish: " (:org/name note))])]])]))})

(defn select-org-keys [note]
  (select-keys note
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

;; ### recently modified

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons}
(->> (recent-notes)
     (map (fn [note]
            (-> note
                (assoc :published (publish/published-id? (:org/id note)))
                (assoc :all-tags (item/item->all-tags note))
                (assoc :last-modified
                       (:file/last-modified note)))))
     (sort-by :published)
     (into []))

;; ### linked items

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons}
(->> linked-items
     (map (fn [note] (assoc note :published (publish/published-id? (:org/id note)))))
     (sort-by :published)
     (into []))


;; ### published items
^{::clerk/no-cache true}
(clerk/table
  {::clerk/width :full}
  (or
    (->> (publish/published-notes)
         (map select-org-keys)
         seq)
    [{:no-data nil}]))

;; ### unpublished, linked items
^{::clerk/no-cache true}
(clerk/table
  {::clerk/width :full}
  (or
    (->> linked-items
         (remove (fn [{:keys [org/id]}] (publish/published-id? id)))
         (map select-org-keys)
         seq)
    [{:no-data nil}]))
