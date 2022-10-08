(ns org-blog.item
  (:require
   [clojure.set :as set]
   [org-crud.markdown :as org-crud.markdown]
   [clojure.string :as string]))

(defn item-has-tags [item tags]
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

(defn item->md-content
  "Returns a seq of strings"
  ([item] (item->md-content item nil))
  ([item opts]
   (let [tags (:org/tags item)
         [title & body]
         (org-crud.markdown/item->md-body
           item opts
           #_{:id->link-uri *id->link-uri*})]
     (concat
       [title
        (when (seq tags)
          (->> tags (string/join ":") (#(str "tags: :" % ":"))))
        ""]
       body))))

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
