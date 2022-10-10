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
   [org-blog.item :as item]
   [org-blog.config :as config]
   [org-blog.db :as db]
   [org-crud.markdown :as org-crud.markdown]))

(def ^:dynamic *day* (t/today))
(def ^:dynamic *id->link-uri* (fn [_] nil))
(def ^:dynamic *allowed-tags* config/allowed-tags)
(def ^:dynamic *previous-day* (t/<< *day* (t/new-period 1 :days)))
(def ^:dynamic *next-day* (t/>> *day* (t/new-period 1 :days)))

(defn day->uri [d] (str "daily/" d ".html"))
(defn path->uri [path]
  (str (fs/file-name (fs/parent path))
       "/"
       (fs/strip-ext
         (fs/file-name path))
       ".html"))
(comment
  (day->uri *day*)
  (path->uri
    (garden/daily-path *day*)))

(def this-ns *ns*)

(defn export-for-day
  [{:keys [day id->link-uri previous-day next-day allowed-tags]}]
  (when day
    (if-not (fs/exists? (garden/daily-path day))
      (println "[WARN] no daily file for " day " at path " (garden/daily-path day))
      (do
        (println "[EXPORT] exporting daily for: " day)
        (with-bindings
          {#'org-blog.daily/*day*          (or day *day*)
           #'org-blog.daily/*id->link-uri* (or id->link-uri *id->link-uri*)
           #'org-blog.daily/*previous-day* (or previous-day *previous-day*)
           #'org-blog.daily/*next-day*     (or next-day *next-day*)
           #'org-blog.daily/*allowed-tags* (or allowed-tags *allowed-tags*)}
          (render/path+ns-sym->spit-static-html
            (str "public/" (day->uri day))
            (symbol (str this-ns))))))))

^{::clerk/no-cache true}
(def todays-org-item
  (-> *day* garden/daily-path org-crud/path->nested-item))

(defn previous-uri [] (day->uri *previous-day*))
(defn next-uri [] (day->uri *next-day*))

(defn items-for-day
  ([day] (items-for-day day nil))
  ([day opts]
   (when (-> day garden/daily-path fs/exists?)
     (some->> day
              garden/daily-path org-crud/path->nested-item
              :org/items
              (filter #(item/item-has-tags % (:tags opts *allowed-tags*)))
              seq))))

(defn daily->content
  ([day] (daily->content day nil))
  ([day opts]
   (->>
     (items-for-day day opts)
     (mapcat #(item/item->md-content
                % (merge {:id->link-uri *id->link-uri*} opts)))
     (string/join "\n"))))

(comment
  (daily->content *day*))

(defn backlink-list
  "Backlinks follow a similar pattern to forward-link creation -
  we use the `*id->link-uri*` dynamic binding to determine if the
  link shoudl be created. In this case, we filter out unpublished
  backlinks completely."
  [id]
  (->> id
       db/notes-linked-from
       (filter (comp *id->link-uri* :org/id)) ;; filter if not 'published'
       (mapcat (fn [item]
                 (let [link-name (:org/parent-name item (:org/name item))]
                   (concat
                     [(str "### [" link-name "](" (-> item :org/id *id->link-uri*) ")")]
                     (org-crud.markdown/item->md-body item)))))))

(defn backlinks [id]
  (let [blink-lines (backlink-list id)]
    (when (seq blink-lines)
      (concat
        ["---" "" "# Backlinks" ""]
        blink-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# " (:org/name todays-org-item)))
(clerk/md (daily->content *day*))

(clerk/html
  [:div
   (when *previous-day*
     [:a {:href (str "/" (previous-uri))}
      (str "<< " *previous-day*)])])

(clerk/html
  [:div
   (when *next-day*
     [:a {:href (str "/" (next-uri))}
      (str ">> " *next-day*)])])

^{::clerk/no-cache true}
(clerk/md (->> (backlinks (:org/id todays-org-item)) (string/join "\n")))
