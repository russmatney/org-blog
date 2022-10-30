(ns org-blog.pages.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]

   [org-blog.item :as item]
   [org-blog.notes :as notes]
   [clojure.set :as set]
   [org-blog.render :as render]))

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
    [:h2 {:class ["font-mono"]} "Home"]]

   [:div
    [:h3 [:a {:href "/notes/blog_about.html"} "About"]]]

   [:div
    [:h3 "Notes"]
    [:div
     {:class "pl-4"}
     [:h3
      [:a {:href "/last-modified.html"} "...by Last Modified"]]
     [:h3
      [:a {:href "/tags.html"} "...by Tag"
       ;; TODO include 5 most common tags
       ]]]]

   [:div
    [:h3 "Projects"]
    (->> (notes/published-notes)
         (filter (comp seq
                       #(set/intersection
                          #{"project" "projects"} %)
                       :org/tags))
         ;; TODO sort projects, include tags
         (map (fn [note]
                ;; TODO include link to repo
                ;; TODO include short description
                (item/note-row note)))
         (into [:div {:class "pl-4"}]))]

   #_ [:div
       [:h3 "Commits"]
       (->> (notes/published-notes)
            (filter (comp seq
                          #(set/intersection
                             #{"project" "projects"} %)
                          :org/tags))
            (filter :org.prop/repo)
            (map (fn [note]
                   (let [repo (:org.prop/repo note)]
                     [:h3 repo])))
            (into [:div {:class "pl-4"}]))]

   [:hr]
   [:div
    [:h3 "Recently modified"]
    [:div
     (->>
       (notes-by-day *notes*)
       (take 5) ;; 5 most recent day blocks
       (map (fn [[day notes]] (day-block {:day day :notes notes})))
       (into [:div]))]]])

(comment
  (render/write-page
    {:path    "public/index.html"
     :content (page)
     :title   "Home"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/html (page))
