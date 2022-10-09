(ns org-blog.daily
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [org-blog.render :as render]
   [babashka.fs :as fs]
   [org-blog.item :as item]))

(defonce ^:dynamic day (t/today))
(defonce ^:dynamic *id->link-uri* (fn [_] nil))

(def this-ns *ns*)

^{::clerk/no-cache true}
(defn todays-org-path [] (garden/daily-path day))

^{::clerk/no-cache true}
(def todays-org-item
  (-> (todays-org-path)
      org-crud/path->nested-item))

(defn day->uri [d]
  (str "daily/" d ".html"))

;; TODO find previous/next _with content_
(def previous-day (t/<< day (t/new-period 1 :days)))
(def next-day (t/>> day (t/new-period 1 :days)))

(defn previous-uri [] (day->uri previous-day))
(defn next-uri [] (day->uri next-day))

;; we need to track what files are published, what dailies have content, etc
;; TODO how to skip dailies with no content?
(defn export-for-day [{:keys [day id->link-uri]}]
  (when day
    (println "[EXPORT] exporting daily for: " day)
    (with-bindings
      {#'org-blog.daily/day            day
       #'org-blog.daily/*id->link-uri* (or id->link-uri *id->link-uri*)}
      (let [path (todays-org-path)
            uri  (day->uri day)]
        (if (fs/exists? path)
          (render/path+ns-sym->spit-static-html
            (str "public/" uri)
            (symbol (str this-ns)))
          (println "[WARN] no daily file for " day " at path " path))))))

(comment
  (export-for-day {:day (t/today)})
  (export-for-day {:day (str (t/<< (t/today) (t/new-period 1 :days)))}))

(defn items->content [items opts]
  (let [tags (:tags opts #{})]
    (->>
      items
      (filter #(item/item-has-tags % tags))
      (mapcat #(item/item->md-content
                 % {:id->link-uri *id->link-uri*}))
      (string/join "\n"))))

(def allowed-tags #{"til" "talk" "bugstory" "hammock"})

(comment
  (items->content
    (:org/items todays-org-item)
    {:tags allowed-tags}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# " (:org/name todays-org-item)))
(clerk/md
  (items->content
    (:org/items todays-org-item)
    {:tags allowed-tags}))

(clerk/html
  [:div
   [:a {:href (str "/" (previous-uri))}
    (str "<< " previous-day)]])

(clerk/html
  [:div
   [:a {:href (str "/" (next-uri))}
    (str ">> " next-day)]])
