(ns org-blog.pages.last-modified
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]

   [org-blog.item :as item]
   [org-blog.notes :as notes]))

(def ^:dynamic *notes* (notes/published-notes))

(defn notes-by-day [notes]
  (->> notes
       (map #(dissoc % :org/body))
       (group-by #(-> % :file/last-modified dates/parse-time-string t/date))
       (map (fn [[k v]] [k (into [] v)]))
       (sort-by first t/>)))

(comment
  (notes-by-day *notes*))

(defn day-block [{:keys [day notes]}]
  [:div
   [:div
    {:class ["flex" "flex-row" "justify-center"]}
    [:h3
     {:class ["pb-2"]}
     (t/format (t/formatter "EEEE, MMM dd") day)]]

   (->> notes (map item/note-row) (into [:<>]))
   [:hr]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/html
  [:div
   {:class ["flex" "flex-row" "justify-center"]}
   [:h2 {:class ["font-mono"]} "Notes By Last Modified"]])

^{::clerk/no-cache true}
(clerk/html
  [:div
   [:div
    (->>
      (notes-by-day *notes*)
      (map (fn [[day notes]] (day-block {:day day :notes notes})))
      (into [:<>]))]])
