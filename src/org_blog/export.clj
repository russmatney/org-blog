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

(defn recent-unpublished-notes
  "A list of recently edited files that have not necessarily been marked 'published'."
  []
  (->> (garden/all-garden-paths)
       (sort-by (comp str fs/last-modified-time))
       (reverse)
       (map org-crud/path->nested-item)
       (remove nil?)
       (remove (fn [note] (notes/published-id? (:org/id note))))
       (take recent-file-count)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_ "linked ids"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collect-linked-ids
  "Collects ids that the published notes link to."
  ([] (collect-linked-ids nil))
  ([_opts]
   (let [all-items (->> (notes/published-notes) (mapcat org-crud/nested-item->flattened-items))]
     (->> all-items (mapcat :org/links-to) (map :link/id)))))

;; TODO figure out what we want here
(defn collect-backlinked-ids
  ([] (collect-linked-ids nil))
  ([_opts]
   (let [all-item-ids (->> (db/all-flattened-notes-by-id) keys
                           ;; we might be dropping inner org item links here
                           (remove nil?))]
     (->> all-item-ids (mapcat db/ids-linked-from)))))

(comment
  (->>
    (collect-linked-ids)
    frequencies
    (sort-by second >))
  (->>
    (collect-backlinked-ids)
    frequencies))

^{::clerk/no-cache true}
(def linked-items (->> (collect-linked-ids)
                       frequencies
                       (sort-by second >)
                       (map (fn [[id ct]]
                              (assoc ((db/notes-by-id) id) :linked-count ct)))))

^{::clerk/no-cache true}
(def backlinked-items (->> (collect-backlinked-ids)
                           frequencies
                           (sort-by second >)
                           (map (fn [[id ct]]
                                  (assoc ((db/notes-by-id) id)
                                         :backlinked-count ct)))))

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
         (for [[i note] (->> notes
                             (sort-by (comp count :org/items) >)
                             (map-indexed vector))]
           ^{:key i}
           (reagent.core/with-let [show-items (reagent.core/atom {} #_{0 true})
                                   show-links (reagent.core/atom {})]
             [:div
              {:class ["flex" "flex-col" "space-x-4" "justify-center" "w-full"
                       "px-4" "py-2" "my-1"
                       "border" "border-2" (if (:published note)
                                             "border-emerald-600"
                                             "border-slate-600")
                       "rounded"]}

              ;; header counts date
              [:div
               {:class ["flex" "flex-row" "space-x-4" "justify-between" "w-full"]}

               [:h4
                [:button
                 {:class    ["text-emerald-500" "hover:text-emerald-300"]
                  :on-click (fn [_] (v/clerk-eval
                                      `(org-blog.export/open-in-emacs!
                                         ~(-> note :org/short-path))))}
                 (-> note :org/short-path)]]

               (when (:linked-count note)
                 [:span
                  {:class ["font-mono"]}
                  (str (->> note :linked-count) " linked-to")])

               (when (:backlinked-count note)
                 [:span
                  {:class ["font-mono"]}
                  (str (->> note :backlinked-count) " backlinked-to")])

               [:span
                {:class ["font-mono"]}
                (str
                  (->> note :org/items (filter (comp seq :org/tags)) count)
                  "/" (-> note :org/items count) " items")]

               [:span
                {:class ["font-mono"]}
                (str (->> note :all-tags count) " tags")]

               [:span
                {:class ["font-mono"]}
                (str
                  (->> note :all-links (filter :published) count)
                  "/" (->> note :all-links count) " links")]

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
                 (str
                   (when (->> note :all-tags seq) "#")
                   (->> note :all-tags (take 5) (clojure.string/join " #")))]]

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
                 (for [item (->> note :org/items
                                 (sort-by (comp boolean seq :org/tags))
                                 reverse)]
                   [:div
                    [:div
                     {:class ["font-mono"]}

                     (when (:org/status item)
                       [:span
                        {:class ["pr-2"]}
                        (case (:org/status item)
                          :status/done        "[X]"
                          :status/not-started "[ ]"
                          :status/in-progress "[-]"
                          :status/skipped     "SKIP"
                          :status/cancelled   "CANCELLED"
                          :else
                          (str (:org/status item)))])

                     [:span
                      {:class ["text-lg"
                               (if (->> item :org/tags seq)
                                 "text-emerald-400"
                                 "text-slate-400")]}
                      (:name-str item)]

                     [:span
                      {:class [(if (->> item :org/tags seq)
                                 "text-slate-400"
                                 "text-red-400")]}
                      (if (->> item :org/tags seq)
                        (str " #" (->> item :org/tags (take 5) (clojure.string/join " #")))
                        " No tags")]]

                    (when (-> item :org/links-to seq)
                      [:div
                       {:class ["flex" "flex-col"]}
                       (for [link (-> item :org/links-to)]
                         [:div
                          [:span
                           {:class ["font-mono"
                                    (if (:published item)
                                      "text-emerald-400"
                                      "text-red-400")]}
                           (str
                             (:link/text link) " -> " (:org/name link)
                             (when-not (:published link) " (unpublished)"))]
                          (when-not (:published link)
                            [:button
                             {:class    ["bg-blue-700" "hover:bg-blue-600"
                                         "text-slate-300" "font-bold"
                                         "py-2" "px-4" "m-1"
                                         "rounded"]
                              :on-click (fn [_] (v/clerk-eval
                                                  `(org-blog.export/publish-note!
                                                     ~(-> link :org/short-path))))}
                             "publish"])
                          (when (:published link)
                            [:button
                             {:class    ["bg-red-800" "hover:bg-red-500"
                                         "text-slate-300" "font-bold"
                                         "py-2" "px-4" "m-1"
                                         "rounded"]
                              :on-click (fn [_] (v/clerk-eval
                                                  `(org-blog.export/unpublish-note!
                                                     ~(-> link :org/short-path))))}
                             "unpublish"])
                          ])])

                    (when (-> item :org/urls seq)
                      [:div
                       {:class ["flex" "flex-col"]}
                       (for [url (-> item :org/urls)]
                         [:h4
                          [:a {:href url :_target "blank" :class ["font-mono"]}
                           url]])])

                    [:div
                     {:class ["border" "border-slate-600" "w-full"
                              "mt-3" "mb-2"]}]])])]))]))})

(defn select-org-keys [note]
  (select-keys note [:org/name :org/tags #_ :org/id #_ :org/short-path
                     #_ :org/links-to :org/level :org/body-string]))

(defn merge-item-into-link [{:keys [link/id] :as link}]
  (merge link ((db/notes-by-id) id)))

(defn decorate-note [note]
  (-> note
      (assoc :published (notes/published-id? (:org/id note)))
      (assoc :all-tags (item/item->all-tags note))
      (assoc :all-links (->> (item/item->all-links note)
                             (map merge-item-into-link)))
      (assoc :last-modified
             (some->> note :file/last-modified str
                      (dates/parse-time-string)
                      (t/format (t/formatter "MMM d YY" ))))
      (assoc :name-str (item/item->name-str note))
      (update :org/links-to (fn [items]
                              (->>
                                items
                                (map merge-item-into-link)
                                ;; (map decorate-note)
                                )))
      (update :org/items (fn [items] (map decorate-note items)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; ### recently modified

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons
  ::clerk/width    :wide}
(->>
  (recent-notes)
  (take 5)
  (map decorate-note)
  (sort-by :published)
  (into []))

;; ### recently modified (unpublished only)

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons
  ::clerk/width    :wide}
(->>
  (recent-unpublished-notes)
  (map decorate-note)
  (into []))


;; ### unpublished, linked items

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons
  ::clerk/width    :wide}
(->> linked-items
     (remove (fn [note] (notes/published-id? (:org/id note))))
     (take 5)
     (map decorate-note)
     (into []))

;; ### unpublished, backlinked items

^{::clerk/no-cache true
  ::clerk/viewer   note-publish-buttons
  ::clerk/width    :wide}
(->> backlinked-items
     (remove (fn [note] (notes/published-id? (:org/id note))))
     (take 5)
     (map decorate-note)
     (into []))


;; ### frequent tags
(clerk/vl
  {:$schema     "https//vega.github.io/schema/vega-lite/v5.json"
   :width       650
   :height      500
   :description "A simple donut chart with embedded data."
   :data        {:values (->> (notes/published-notes)
                              (mapcat item/item->all-tags)
                              frequencies
                              (sort-by second >)
                              (map (fn [[v ct]]
                                     {:category (str v (when (> ct 2) (str " (" ct ")")))
                                      :value    ct}))
                              (into []))}
   ;; :layer       [{:mark {:type "arc" :outerRadius 180}}
   ;;               {:mark     {:type "text" :radius 210}
   ;;                :encoding {:text {:field "category" :type "nominal"}}}]
   :mark        {:type "arc" :innerRadius 50 :tooltip true}
   :encoding    {:theta {:field "value" :type "quantitative" :stack "normalize"}
                 :color {:field "category" :type "nominal"}}})


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
