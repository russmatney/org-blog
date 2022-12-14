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

(defn notes-by-day [notes]
  (->> notes
       (filter :file/last-modified)
       (map #(dissoc % :org/body))
       (group-by #(-> % :file/last-modified dates/parse-time-string t/date))
       (map (fn [[k v]] [k (into [] v)]))
       (sort-by first t/>)))

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
     (notes-by-day (notes/published-notes))
     (map (fn [[day notes]]
            (when day
              (day-block {:day day :notes notes}))))
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
