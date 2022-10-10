(ns org-blog.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [nextjournal.clerk :as clerk]
   [babashka.fs :as fs]

   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [org-blog.render :as render]
   [org-blog.item :as item]
   [org-blog.config :as config]
   [org-blog.db :as db]
   [org-crud.markdown :as org-crud.markdown]))

(def ^:dynamic *path*
  (->> (garden/flat-garden-paths)
       (filter (comp #(string/includes? % "async_mario")))
       first))

(def ^:dynamic *id->link-uri*
  (fn [_]
    ;; NOTE do not publish with this val
    ;; this just helps dev on the notes page by including backlinks
    "some-link"))

(def ^:dynamic *allowed-tags* config/allowed-tags)

(defn path->uri [path]
  (-> path fs/file-name fs/strip-ext (#(str "note/" % ".html"))))

(comment
  (path->uri *path*))

(def this-ns *ns*)

(defn export-note
  [{:keys [path id->link-uri allowed-tags]}]
  (when (and path (fs/exists? path))
    (println "[EXPORT] exporting note: " (fs/file-name path))
    (with-bindings
      {#'org-blog.note/*path*         (or path *path*)
       #'org-blog.note/*id->link-uri* (or id->link-uri *id->link-uri*)
       #'org-blog.note/*allowed-tags* (or allowed-tags *allowed-tags*)}
      (render/path+ns-sym->spit-static-html
        (str "public/" (path->uri path))
        (symbol (str this-ns))))))

^{::clerk/no-cache true}
(def note (-> *path* org-crud/path->nested-item))

(defn path->content
  ([path] (path->content path nil))
  ([path opts]
   (->
     path
     ;; TODO opt-in/out of note children?
     org-crud/path->nested-item
     (item/item->md-content (merge {:id->link-uri *id->link-uri*} opts))
     (->> (string/join "\n")))))

(comment
  (item/item->md-content
    (org-crud/path->nested-item *path*)
    {:id->link-uri *id->link-uri*})
  (path->content *path*))

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
                     (org-crud.markdown/item->md-body item
                                                      {:id->link-uri *id->link-uri*})))))))

(defn backlinks [id]
  (let [blink-lines (backlink-list id)]
    (when (seq blink-lines)
      (concat
        ["---" "" "# Backlinks" ""]
        blink-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# " (:org/name note)))
(clerk/md (path->content *path*))

^{::clerk/no-cache true}
(clerk/md (->> (backlinks
                 (-> *path* org-crud/path->nested-item :org/id))
               (string/join "\n")))
