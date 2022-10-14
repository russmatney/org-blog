(ns org-blog.pages.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]

   [org-blog.item :as item]
   [org-blog.publish :as publish]))

(def *notes* (publish/published-notes))

(defn notes-by-day [notes]
  (->> notes
       (map #(dissoc % :org/body))
       (group-by #(-> % :file/last-modified dates/parse-time-string t/date))
       (map (fn [[k v]] [k (into [] v)]))
       (sort-by first t/>)))

(comment
  (notes-by-day *notes*))

(defn note-row [note]
  (let [all-tags (item/item->all-tags note)]
    [:div
     {:class ["flex" "flex-row" "justify-between"]}
     [:h3
      {:class ["hover:underline"]}
      [:a
       {:class ["cursor-pointer"]
        :href  (publish/id->link-uri (:org/id note))}
       (:org/name note)]]

     ;; [:div
     ;;  {:class ["font-mono"]}
     ;;  (->> note :file/last-modified dates/parse-time-string
     ;;       (t/format (t/formatter "hh:mma")))]

     (->>
       all-tags
       (map #(str "#" % " "))
       (into [:div {:class ["font-mono"]}]))]))

(defn day-block [{:keys [day notes]}]
  [:div
   [:div
    {:class ["flex" "flex-row" "justify-center"]}
    [:h3
     {:class ["pb-2"]}
     (t/format (t/formatter "EEEE, MMM dd") day)]]

   (->> notes (map note-row) (into [:<>]))
   [:hr]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/html
  [:div
   {:class ["flex" "flex-row" "justify-center"]}
   [:h2 {:class ["font-mono"]} "All"]])

^{::clerk/no-cache true}
(clerk/html
  [:div
   [:div
    (->>
      (notes-by-day *notes*)
      (map (fn [[day notes]] (day-block {:day day :notes notes})))
      (into [:<>]))]])

