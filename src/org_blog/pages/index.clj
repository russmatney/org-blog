(ns org-blog.pages.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [dates.tick :as dates]
   [garden.core :as garden]

   [org-crud.core :as org-crud]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.pages.daily :as daily]
   [org-blog.publish :as publish]))

(defn some-dailies []
  (->> (garden/daily-paths 30)
       (map org-crud/path->nested-item)
       (filter (comp seq daily/note->daily-items))))

(def ^:dynamic *notes*
  (->> (garden/all-garden-notes-nested)
       (remove (comp #(string/includes? % "/daily/") :org/source-file))
       (sort-by :file/last-modified)
       reverse
       (take 10)
       (concat (some-dailies))
       (into #{})))

(def this-ns *ns*)

(defn export
  [{:keys [notes]}]
  (println "[EXPORT] exporting index.")
  (with-bindings
    {#'org-blog.pages.index/*notes* notes}
    (render/path+ns-sym->spit-static-html
      (str "public/index.html") (symbol (str this-ns)))))

(defn notes-by-day [notes]
  (->> notes
       (map #(dissoc % :org/body))
       (group-by #(-> % :file/last-modified dates/parse-time-string t/date))
       (map (fn [[k v]] [k (into [] v)]))
       (sort-by first t/>)))

(comment
  (notes-by-day *notes*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/html [:div
             {:class ["flex" "flex-row" "justify-center"]}
             [:h2 {:class ["font-mono"]} "All"]])

^{::clerk/no-cache true}
(clerk/html
  [:div
   [:div
    (into [:<>]
          (for [[day notes] (notes-by-day *notes*)]
            [:div
             [:div
              {:class ["flex" "flex-row" "justify-center"]}
              [:h3
               {:class ["pb-2"]}
               (t/format (t/formatter "EEEE, MMM dd") day)]]

             (into [:<>]
                   (for [note notes]
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

                        (into [:div {:class ["font-mono"]}]
                              (for [tag all-tags]
                                (str "#" tag " ")))])))

             [:hr]]))]])

