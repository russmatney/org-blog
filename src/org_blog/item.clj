(ns org-blog.item
  (:require
   [clojure.set :as set]
   [org-crud.markdown :as org-crud.markdown]
   [clojure.string :as string]
   [org-crud.core :as org-crud]
   [babashka.fs :as fs]))

(defn item-has-tags
  "Returns truthy if the item has at least one matching tag."
  [item tags]
  (-> item :org/tags (set/intersection tags) seq))

(defn item-has-parent [item parent-names]
  (when-let [p-name (:org/parent-name item)]
    (->> parent-names
         (filter (fn [match] (string/includes? p-name match)))
         seq)))

(defn items-with-tags [items tags]
  (->> items (filter #(item-has-tags % tags))))

(defn items-with-parent [items parent-names]
  (->> items (filter #(item-has-parent % parent-names))))

(defn item->tag-line
  ([item] (item->tag-line nil item))
  ([opts item]
   (let [tags
         (if (:include-child-tags opts)
           (->> item org-crud/nested-item->flattened-items
                (mapcat :org/tags) (into #{}))
           (:org/tags item))]
     (when (seq tags)
       (->> tags (map #(str ":" %)) (string/join "\t"))))))

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn item->title-content
  ([item] (item->title-content item nil))
  ([item opts]
   (some->> (org-crud.markdown/item->md-body item opts) first)))

(defn item->md-content
  "Returns a seq of strings"
  ([item] (item->md-content item nil))
  ([item opts]
   (let [
         [title & body]
         (org-crud.markdown/item->md-body item opts)]
     (concat
       [title
        (when-let [tag-line (item->tag-line item)]
          (str "### " tag-line))
        ""]
       body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; content by 'tag groups'
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn content-with-tags
  [items {:keys [title tags parent-names]}]
  (let [filtered-items (concat
                         (items-with-tags items tags)
                         (items-with-parent items parent-names))
        lines          (->> filtered-items (mapcat item->md-content))]
    (when (seq lines)
      (concat
        [(str "## " title "\n")]
        lines))))

;; TODO avoid including items multiple times? or at least make it more clear/less redundant
(defn content-for-tag-groups
  [items tag-groups]
  (->> tag-groups
       (mapcat #(content-with-tags items %))
       (string/join "\n")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->uri
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->uri [path]
  (-> path fs/file-name fs/strip-ext
      (#(str (if (string/includes? path "/daily/")
               "daily"
               "note")
             "/" % ".html"))))

(defn item->uri [item]
  (-> item :org/source-file path->uri))
