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
   [org-blog.render :as render]
   [babashka.fs :as fs]))

(defonce ^:dynamic day (t/today))

(def this-ns *ns*)
(def this-file *file*)
(declare todays-org-path)

(defn export-for-day [{:keys [day]}]
  (when day
    (println "[EXPORT] exporting daily for: " day)
    (with-bindings {#'org-blog.daily/day day}
      (let [path (todays-org-path)]
        (if (fs/exists? path)
          (render/path+ns-sym->spit-static-html
            (str "public/daily/" day ".html")
            (symbol (str this-ns)))
          (println "[WARN] no daily file for " day " at path " path))))))

(defn reexport! []
  (export-for-day {:day day}))

(defn reload! []
  (clerk/show! this-file))

(comment
  (export-for-day {:day (t/today)})
  (export-for-day {:day (str (t/<< (t/today) (t/new-period 1 :days)))}))

^{::clerk/no-cache true}
(defn todays-org-path [] (garden/daily-path day))

^{::clerk/no-cache true}
(def todays-org-item
  (-> (todays-org-path)
      org-crud/path->nested-item))

(defn item-has-tags [item tags]
  (-> item :org/tags (set/intersection tags) seq))

(defn items-with-tags [items tags]
  (->> items (filter #(item-has-tags % tags))))

(comment
  (->>
    (items-with-tags (:org/items todays-org-item) #{"til"})
    (mapcat org-crud.markdown/item->md-body)))

(defn content-with-tags
  [items {:keys [title tags]}]
  (let [notes (->>
                (items-with-tags items tags)
                (mapcat (fn [item]
                          (let [[title & body]
                                (org-crud.markdown/item->md-body item)]
                            (concat
                              [title
                               (->> (:org/tags item)
                                    (string/join ":")
                                    (#(str "tags: :" % ":")))
                               ""]
                              body))))
                seq)]
    (when (seq notes)
      (concat
        [(str "## " title "\n")]
        notes))))

(comment
  (content-with-tags
    (:org/items todays-org-item)
    {:title "TIL" :tags #{"til"}}))

(def tag-groups
  [{:title       "TIL"
    :description "Today I learned"
    :tags        #{"til"}}
   {:title "Talks" :tags #{"talk"}}
   {:title "Stories" :tags #{"bugstory"}}
   {:title       "Hammock" :tags #{"hammock"}
    :description "Some things I'm turning over"}])

(defn content-for-tag-groups [items]
  (->> tag-groups
       (mapcat #(content-with-tags items %))
       (string/join "\n")))


;; TODO flag for filtering elems when printing to 'public'

^::clerk/no-cache
(def last-load-time (atom (t/now)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/viewer
  '(fn [last-load-time]
     (v/html [:div.text-center
              (when last-load-time
                [:div.mt-2.text-white.text-lg
                 last-load-time])
              [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
               {:on-click (fn [e] (v/clerk-eval '(reload!)))} "Reload 🎲!"]
              [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
               {:on-click (fn [e] (v/clerk-eval '(re-export!)))} "Re-export 🎲!"]]))}
(str @last-load-time)

(clerk/md (str "# " (:org/name todays-org-item)))
(clerk/md (content-for-tag-groups (:org/items todays-org-item)))
