(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]
   [org-crud.core :as org-crud]

   [garden.core :as garden]
   [ralphie.notify :as notify]
   [ralphie.emacs :as emacs]

   [org-blog.db :as db]
   [org-blog.config :as config]
   [org-blog.item :as item]
   [org-blog.notes :as notes]
   [tick.core :as t]
   [dates.tick :as dates]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "recent daily notes"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def recent-file-count 14)

(defn recent-notes
  "A list of recently edited files that have not necessarily been marked 'published'."
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
   (let [all-items        (->> (notes/published-notes) (mapcat org-crud/nested-item->flattened-items))
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

(defn open-in-emacs! [short-path]
  (notify/notify "open in emacs!")
  (let [note       ((db/notes-by-short-path) short-path)]
    (emacs/open-in-emacs
      {:emacs/file-path (:org/source-file note)
       :emacs/frame-name "journal"})))

(def note-publish-buttons
  {:name         :publishing-notes
   :transform-fn clerk/mark-presented
   :render-fn
   '(fn [notes]
      (v/html
        [:div {:class ["w-full"]}
           (for [[i note]
                 (->> notes
                      (sort-by (comp count :org/items) >)
                      (map-indexed vector))]
             ^{:key i}
             (reagent.core/with-let [show-items (reagent.core/atom {})
                                     show-links (reagent.core/atom {})]
          [:div
           {:class ["my-1"
                    "flex" "flex-col" "space-x-4" "justify-center" "w-full"
                    "px-4" "py-2"
                    "border" "border-2" "border-emerald-600"
                    "rounded"]}

           ;; header counts date
           [:div
            {:class ["flex" "flex-row" "space-x-4" "justify-between" "w-full"]}

            [:h4
             [:button
              {:class    ["text-emerald-500"
                          "hover:text-emerald-300"]
               :on-click (fn [_] (v/clerk-eval
                                   `(org-blog.export/open-in-emacs!
                                      ~(-> note :org/short-path))))}
              (-> note :org/short-path)]]
            [:span
             {:class ["font-mono"]}
             (str (-> note :org/items count) " items")]

            [:span
             {:class ["font-mono"]}
             (str (->> note :all-tags count) " tags")]

            [:span
             {:class ["font-mono"]}
             (str (->> note :all-links count) " links")]

            [:h4
             {:class ["text-slate-600" "font-mono" "ml-auto"]}
             (-> note :last-modified
                 str)]]

           [:div
            {:class ["flex" "flex-row" "space-x-4" "justify-between" "align-center"]}

            ;; all-tags
            [:div
             [:span
              {:class ["font-mono"]}
              (->> note :all-tags
                   (take 5)
                   (clojure.string/join ":"))]]

            ;; actions
            [:div
             (when (-> note :org/items seq)
               [:button
                {:class    ["bg-amber-700" "hover:bg-amber-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (swap! show-items update i not))}
                (if (@show-items i) "hide items" "show items")])

             (when (-> note :all-links seq)
               [:button
                {:class    ["bg-green-700" "hover:bg-green-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (swap! show-links update i not))}
                (if (@show-links i) "hide links" "show links")])

             (when (not (:published note))
               [:button
                {:class    ["bg-blue-700" "hover:bg-blue-600"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/publish-note!
                                        ~(-> note :org/short-path))))}
                "publish"])

             (when (:published note)
               [:button
                {:class    ["bg-red-800" "hover:bg-red-500"
                            "text-slate-300" "font-bold"
                            "py-2" "px-4" "m-1"
                            "rounded"]
                 :on-click (fn [_] (v/clerk-eval
                                     `(org-blog.export/unpublish-note!
                                        ~(-> note :org/short-path))))}
                "unpublish"])]]

           ;; links
           (when (@show-links i)
             [:div
              {:class ["border" "border-green-800"]}
              (for [link (->> note :all-links)]
                [:h4
                 {:class ["font-mono"]}
                 (:link/text link)
                 " -> "
                 (:org/name link)])])

           ;; items
           (when (@show-items i)
             [:div
              {:class ["border" "border-amber-800"]}
              (for [item (->> note :org/items)]
                [:h4
                 {:class ["font-mono"]}
                 (:org/name item)])])]))]))})

(defn select-org-keys [note]
  (select-keys note [:org/name :org/tags #_ :org/id #_ :org/short-path
                     #_ :org/links-to :org/level :org/body-string]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; ### recently modified

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons
  ::clerk/width :wide}
(->> (recent-notes)
     (map (fn [note]
            (-> note
                (assoc :published (notes/published-id? (:org/id note)))
                (assoc :all-tags (item/item->all-tags note))
                (assoc :all-links (->> (item/item->all-links note)
                                       (map (fn [{:keys [link/id] :as link}]
                                              (merge link ((db/notes-by-id) id))))))
                (assoc :last-modified
                       (->>
                         note
                         :file/last-modified
                         str
                         (dates/parse-time-string)
                         (t/format
                           (t/formatter "MMM d YY" )))))))
     (sort-by :published)
     (into []))

;; ### linked items

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons}
(->> linked-items
     (map (fn [note] (assoc note :published (notes/published-id? (:org/id note)))))
     (sort-by :published)
     (into []))


;; ### published items
^{::clerk/no-cache true}
(clerk/table
  {::clerk/width :full}
  (or
    (->> (notes/published-notes)
         (map select-org-keys)
         seq)
    [{:no-data nil}]))

;; ### unpublished, linked items
^{::clerk/no-cache true}
(clerk/table
  {::clerk/width :full}
  (or
    (->> linked-items
         (remove (fn [{:keys [org/id]}] (notes/published-id? id)))
         (map select-org-keys)
         seq)
    [{:no-data nil}]))
