(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [clojure.string :as string]
   [clojure.set :as set]
   [org-crud.core :as org-crud]
   [dates.tick :as dates.tick]

   [org-blog.config :as config]
   [org-blog.daily :as daily]
   [org-blog.note :as note]
   [org-blog.db :as db]))


(defonce ^:dynamic
  ;; TODO improve how days are collected (maybe an 'earliest-date' makes sense)
  *days-ago* 7)
(defonce ^:dynamic *days* (dates.tick/days *days-ago*))
(defonce days-with-content (->> *days* (filter daily/items-for-day)))


(defonce published-ids (atom #{}))
(defonce missing-ids (atom #{}))
(defonce missing-items (atom #{}))

(defonce linked-items (atom #{}))
(defn reset-linked-items [] (reset! linked-items #{}))

(defn collect-linked-ids
  ([] (collect-linked-ids nil))
  ([opts]
   (let [days                  (:days opts *days*)
         published-daily-items (->> days (mapcat daily/items-for-day))
         published-notes       (->> @published-ids (map db/fetch-with-id))
         published             (concat published-daily-items published-notes)
         all-items             (->> published
                                    (mapcat org-crud/nested-item->flattened-items))
         all-links             (->> all-items
                                    (mapcat :org/links-to))]
     all-links)))

(defn set-linked-items []
  (let [linked-ids (->> (collect-linked-ids) (map :link/id) (into #{}))
        missing    (set/difference linked-ids @published-ids)]
    (reset! missing-ids missing)
    (reset! missing-items (->> missing (map db/notes-by-id)))
    (reset! linked-items (->> linked-ids (map db/notes-by-id)))))

(defn note->uri [item]
  ;; TODO dry up uri creation (maybe in config?)
  (-> item :org/source-file note/->uri))


(defn days-with-prev+next [days]
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
          (do
            (println "[INFO: missing link]: removing link to unpublished note: "
                     (:org/name item))
            (swap! missing-ids conj id)
            nil))))))

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

(set-linked-items)

(comment
  (def proc-gen-item
    (->>
      @linked-items
      (filter (comp #(string/includes? % "proc") :org/name))
      first))

  (def level-design-item
    (->>
      @linked-items
      (filter (comp #(string/includes? % "level design") string/lower-case :org/name))
      first))

  (def game-feel-item
    (->>
      @linked-items
      (filter (comp #(string/includes? % "game feel") string/lower-case :org/name))
      first))

  (swap! published-ids conj (:org/id game-feel-item))
  (swap! published-ids conj (:org/id proc-gen-item))
  (swap! published-ids conj (:org/id level-design-item)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; # export

;; ### published items
(clerk/table
  {::clerk/width :full}
  (or
    (->>
      @published-ids
      (map db/notes-by-id)
      seq)
    [{:no-data nil}]))

;; ### missing items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @missing-items seq)
    [{:no-data nil}]))

;; ### all linked items
(clerk/table
  {::clerk/width :full}
  (or
    (->> @linked-items seq)
    [{:no-data nil}]))
