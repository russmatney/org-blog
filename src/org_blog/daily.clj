(ns org-blog.daily
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [clojure.set :as set]
   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [org-crud.markdown :as org-crud.markdown]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [org-blog.render :as render]))

(defonce ^:dynamic day (t/today))

(def this-ns *ns*)

(defn export-for-day [{:keys [day]}]
  (with-bindings {#'org-blog.daily/day day}
    (render/path+ns-sym->spit-static-html
      (str "public/daily/" day ".html")
      (symbol (str this-ns)))))

(comment
  (export-for-day {:day (t/today)})
  (export-for-day {:day (str (t/<< (t/today) (t/new-period 1 :days)))}))


^{::clerk/no-cache true}
(def todays-org-item
  (-> (garden/daily-path day)
      org-crud/path->nested-item))

(def daily-items
  (->> todays-org-item :org/items))

(defn items-with-tags [tags]
  (->> daily-items
       (filter (comp seq :org/tags))
       (filter (comp seq #(set/intersection tags %) :org/tags))))

(comment
  (->>
    (items-with-tags #{"til"})
    (mapcat org-crud.markdown/item->md-body))
  (items-with-tags #{"bugstory"}))

(defn content-with-tags
  [{:keys [title tags]}]
  (let [notes
        (->>
          (items-with-tags tags)
          (mapcat (fn [item]
                    (let [[title & body]
                          (org-crud.markdown/item->md-body item)]
                      (concat
                        [title
                         (->> (:org/tags item)
                              (string/join ":")
                              (#(str "tags: :" % ":")))]
                        body))))
          seq)]
    (when (seq notes)
      (concat
        [(str "## " title "\n")]
        notes))))

(comment
  (content-with-tags
    {:title "TIL" :tags #{"til"}}))

(def tag-groups
  [{:title "TIL" :tags #{"til"}}
   {:title "Stories" :tags #{"bugstory"}}
   {:title "Hammocking" :tags #{"hammock"}}])

(defn content-for-tag-groups []
  (->> tag-groups
       (mapcat content-with-tags)
       (string/join "\n")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

(clerk/md (str "# " (:org/name todays-org-item)))
(clerk/md (content-for-tag-groups))
