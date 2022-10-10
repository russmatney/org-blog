(ns org-blog.note
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [clojure.string :as string]
   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [nextjournal.clerk :as clerk]
   [org-blog.render :as render]
   [babashka.fs :as fs]
   [org-blog.item :as item]
   [org-blog.config :as config]))

(def ^:dynamic *path* (first (garden/flat-garden-paths)))
(def ^:dynamic *id->link-uri* (fn [_] nil))
(def ^:dynamic *allowed-tags* config/allowed-tags)

(defn ->uri [path]
  (-> path fs/file-name fs/strip-ext (#(str "note/" % ".html"))))

(comment
  (->uri *path*))

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
        (str "public/" (->uri path))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}


(clerk/md (str "# " (:org/name note)))
(clerk/md (path->content *path*))

;; TODO backlinks
