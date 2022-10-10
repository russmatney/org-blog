(ns org-blog.export
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [dates.tick :as dates.tick]
   [nextjournal.clerk :as clerk]

   [org-blog.config :as config]
   [org-blog.daily :as daily]
   [org-blog.db :as db]
   [org-crud.core :as org-crud]
   [babashka.fs :as fs]
   [clojure.string :as string]
   [clojure.set :as set]))


(defonce ^:dynamic *days-ago* 7)
;; TODO improve how days are collected (maybe an 'earliest-date' makes sense)
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
  (let [linked-ids (->> (collect-linked-ids)
                        (map :link/id)
                        (into #{}))
        missing    (set/difference linked-ids @published-ids)]
    (swap! missing-ids #(apply conj % missing))
    (reset! missing-items (->> missing (map db/notes-by-id)))
    (reset! linked-items (->> linked-ids (map db/notes-by-id)))))

(defn item->uri [item]
  ;; TODO impl proper
  (str "notes/" (-> item :org/source-file fs/file-name)))

(defn export-dailies
  ([] (export-dailies nil))
  ([opts]
   (let [days-ago (:days-ago opts 7)
         days     (->> (dates.tick/days days-ago)
                       (filter daily/items-for-day))]
     (reset-linked-items)
     (doseq [day days]
       (daily/export-for-day
         {:day day
          :id->link-uri
          (fn [id]
            (let [item (db/fetch-with-id id)]
              (if-not item
                (println "[WARN: bad data]: could not find org item with id:" id)
                (let [linked-id (:org/id item)]
                  (if (@published-ids linked-id)
                    (item->uri item)
                    (do
                      (println "[INFO: missing link]: removing link to unpublished note: "
                               (:org/name item))
                      (swap! missing-ids conj id)
                      nil))))))})))))

(comment
  (export-dailies {:days-ago 7}))

(set-linked-items)

(comment
  (def proc-gen-item
    (->>
      @linked-items
      (filter (comp #(string/includes? % "proc") :org/name))
      first))

  (swap! published-ids conj (:org/id proc-gen-item)))

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
