(ns org-blog.pages.last-modified
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]

   [org-blog.item :as item]
   [org-blog.render :as render]
   [org-blog.notes :as notes]
   [org-blog.config :as config]))

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

   (when (seq notes)
     (->> notes (map item/note-row) (into [:div])))
   [:hr]])

(defn page []
  [:div
   [:div
    {:class ["flex" "flex-row" "justify-center"]}
    [:h2 {:class ["font-mono"]} "Notes By Date Modified"]]

   (->>
     (notes-by-day *notes*)
     (map (fn [[day notes]] (day-block {:day day :notes notes})))
     (into [:div]))])

(comment
  (render/write-page
    {:path    (str (config/blog-content-public) "/last-modified.html")
     :content (page)
     :title   "By Modified Date"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/no-cache true}
(clerk/html (page))

nil
