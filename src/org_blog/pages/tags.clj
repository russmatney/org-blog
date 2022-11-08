(ns org-blog.pages.tags
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]

   [org-blog.item :as item]
   [org-blog.notes :as notes]
   [org-blog.config :as config]
   [org-blog.render :as render]))

(defn notes-by-tag [notes]
  (->> notes
       (map #(dissoc % :org/body))
       (reduce
         (fn [by-tag note]
           (reduce
             (fn [by-tag tag]
               (update by-tag tag (fnil
                                    (fn [notes]
                                      (conj notes note))
                                    #{note})))
             by-tag
             (let [tags (item/item->all-tags note)]
               (if (seq tags) tags
                   ;; here we include items with no tags
                   ;; these should be given tags, and
                   ;; helps to surface them
                   [nil]))))
         {})
       (sort-by (comp (fn [x] (if x (count x) 0)) second) >)))

(defn tag-block [{:keys [tag notes]}]
  [:div
   [:div
    {:class ["flex" "flex-row" "justify-center"]}
    [:h3
     {:class ["pb-2"]}
     [:a {:id tag}
      (or tag "Untagged")]]]

   (when (seq notes)
     (->> notes (map #(item/note-row % {:tags #{tag}})) (into [:div])))
   [:hr]])

(defn page []
  [:div
   [:div
    {:class ["flex" "flex-row" "justify-center"]}
    [:h2 {:class ["font-mono"]} "Notes By Tag"]]
   (->>
     (notes-by-tag (notes/published-notes))
     (map (fn [[tag notes]]
            (when (and tag (seq notes))
              (tag-block {:tag tag :notes notes}))))
     (into [:div]))])

(comment
  (render/write-page
    {:path    (str (config/blog-content-public) "/tags.html")
     :content (page)
     :title   "Home"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/html (page))

nil
