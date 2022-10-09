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
(def this-file *file*)

^{::clerk/no-cache true}
(defn todays-org-path [] (garden/daily-path day))

^{::clerk/no-cache true}
(def todays-org-item
  (-> (todays-org-path)
      org-crud/path->nested-item))

(defn day->uri [d]
  (str "public/daily/" d ".html"))

(defn yesterday-uri []
  (day->uri (t/<< day (t/new-period 1 :days))))

(defn tomorrow-uri []
  (day->uri (t/>> day (t/new-period 1 :days))))

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
            uri (symbol (str this-ns)))
          (println "[WARN] no daily file for " day " at path " path))))))

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reexport! []
  (export-for-day {:day day}))

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn reload! []
  (clerk/show! this-file))

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
   [:a {:href (str "/" (yesterday-uri))} "yesterday"]])

(clerk/html
  [:div
   [:a {:href (str "/" (tomorrow-uri))} "tomorrow"]])
