(ns org-blog.item
  (:require
   [clojure.set :as set]
   [org-crud.markdown :as org-crud.markdown]
   [clojure.string :as string]
   [org-crud.core :as org-crud]
   [org-blog.db :as db]
   [org-blog.uri :as uri]))

(defn item-has-any-tags
  "Returns truthy if the item has at least one matching tag."
  [item]
  (-> item :org/tags seq))

(defn item-has-tags
  "Returns truthy if the item has at least one matching tag."
  [item tags]
  (-> item :org/tags (set/intersection tags) seq))

;; (defn item-has-parent [item parent-names]
;;   (when-let [p-name (:org/parent-name item)]
;;     (->> parent-names
;;          (filter (fn [match] (string/includes? p-name match)))
;;          seq)))

;; (defn items-with-tags [items tags]
;;   (->> items (filter #(item-has-tags % tags))))

;; (defn items-with-parent [items parent-names]
;;   (->> items (filter #(item-has-parent % parent-names))))

(defn item->all-tags [item]
  (->> item org-crud/nested-item->flattened-items
       (mapcat :org/tags) (into #{})))

(defn item->all-links [item]
  (->> item org-crud/nested-item->flattened-items
       (mapcat :org/links-to) (into #{})))

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

(defn item->name-str
  ([item] (item->name-str item nil))
  ([item opts]
   (let [opts    (merge {:id->link-uri uri/*id->link-uri*} opts)
         [title] (org-crud.markdown/item->md-body item opts)]
     title)))

(defn item->plain-title [item]
  (-> item :org/name
      (org-crud.markdown/org-line->md-line
        {:id->link-uri (fn [_] nil)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; markdown helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->md-content
  "Returns a seq of strings"
  ([item] (item->md-content item nil))
  ([item opts]
   (let [opts (merge {:id->link-uri uri/*id->link-uri*} opts)
         [title & body]
         (org-crud.markdown/item->md-body item opts)]
     (concat
       [title
        (when-let [tag-line (item->tag-line item)]
          (str "### " tag-line))
        ""]
       body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; hiccup helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-url? [text]
  (boolean (re-seq #"^https?://" text)))

(def org-link-re #"\[\[([^\]]*)\]\[([^\]]*)\]\]")

(defn ->hiccup-link [{:keys [text link]}]
  (when link
    (let [id       (some->> (re-seq #"^id:([A-Za-z0-9-]*)" link) first second)
          link-uri (when id (uri/*id->link-uri* id))]
      (cond
        (and id link-uri)
        ;; TODO maybe flag these as a different color
        [:a {:href link-uri}
         [:span text]]

        (and id (not link-uri))
        ;; TODO tooltip for 'future-link'
        [:span text]

        :else
        [:a {:href link}
         [:span text]]))))

(defn parse-hiccup-link
  "Returns hiccup representing the next link in the passed string"
  [s]
  (when s
    (let [res  (re-seq org-link-re s)
          link (some-> res first second)
          text (some-> res first last)]
      (->hiccup-link {:text text :link link}))))

(defn interpose-links [s]
  (->> (loop [s s out []]
         (let [next-link (parse-hiccup-link s)
               parts     (string/split s org-link-re 2)]
           (if (< (count parts) 2)
             (concat out (if next-link (conj parts next-link) parts))
             (let [[new-s rest] parts
                   out          (concat out [new-s next-link])]
               (recur rest out)))))
       (remove #{""})))

(defn render-text
  "Converts a passed string into spans.

  Wraps naked urls in [:a {:href url} [:span url]]"
  [text]
  (->
    text
    (string/trim)
    (string/split #" ")
    (->>
      (partition-by is-url?)
      (map (fn [strs]
             (let [f-str (-> strs first string/trim)]
               (cond
                 (and (= 1 (count strs)) (is-url? f-str))
                 (->hiccup-link {:text f-str :link f-str})

                 :else
                 [:span (string/join " " strs)])))))))

(comment
  (render-text "https://github.com/coleslaw-org/coleslaw looks pretty cool!")
  ;; NOTE not handled here - see interpose-links
  (render-text "[[https://www.patreon.com/russmatney][on patreon]]"))

(defn render-text-and-links [s]
  (when s
    (->> s interpose-links
         (mapcat (fn [chunk]
                   (if (string? chunk)
                     (render-text chunk)
                     [chunk])))
         (interpose [:span " "]))))

(comment

  (render-text-and-links
    "check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this video]] on youtube!")

  (render-text-and-links
    "[[https://github.com/russmatney/some-repo][leading link]]
check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this video]] on youtube
and [[https://github.com/russmatney/org-blog][this repo]]
and [[https://github.com/russmatney/org-crud][this other repo]]"))

(defn item->hiccup-headline [item]
  (when (:org/name item)
    (->> item :org/name render-text-and-links
         (into [(case (:org/level item)
                  :level/root :h1
                  1           :h1
                  2           :h2
                  3           :h3
                  4           :h4
                  5           :h5
                  6           :h6
                  :span)]))))

(defn item->hiccup-body [item]
  (->> item :org/body
       (partition-by (comp #{:blank :metadata} :line-type))
       (map (fn [group]
              (let [first-elem-type (-> group first :line-type)]
                (cond
                  (#{:blank} first-elem-type) [:br]
                  (#{:table-row} first-elem-type)
                  (->> group (map :text)
                       ;; join the lines so we can handle multi-line links
                       ;; NOTE here we forego the original line breaks :/
                       (string/join " ")
                       (render-text-and-links)
                       (into [:p]))))))))

(defn item->hiccup-content [item]
  (let [children (->> item :org/items (map item->hiccup-content))]
    (->>
      (concat
        [(item->hiccup-headline item)]
        (item->hiccup-body item)
        children)
      (into [:div]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links and backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn backlink-list
  [id]
  (->> id
       db/notes-linked-from
       (filter (comp uri/*id->link-uri* :org/id)) ;; filter if not 'published'
       (mapcat (fn [item]
                 (let [link-name (:org/parent-name item (:org/name item))]
                   (concat
                     [(str "### [" link-name "](" (-> item :org/id uri/*id->link-uri*) ")")]
                     ;; disabled backlink content for now b/c for links from dailies
                     ;; that don't have parents, too much is pulled and unraveled
                     (org-crud.markdown/item->md-body
                       item
                       {:id->link-uri uri/*id->link-uri*}
                       )))))))

(defn backlinks
  "Returns markdown representing a list of backlinks for given `:org/id`"
  [id]
  (let [blink-lines (backlink-list id)]
    (when (seq blink-lines)
      (concat
        ["---" "" "# Backlinks" ""]
        blink-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; note row
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tags-list
  ([note] (tags-list note nil))
  ([note tags]
   (let [tags (or tags (:org/tags note))]
     (when (seq tags)
       (->>
         tags
         (map #(str "#" %))
         (map-indexed
           (fn [_i tag]
             [:a {:href  (str "/tags.html" tag)
                  :class ["font-mono"]} tag]))
         (into [:div]))))))

(defn note-row
  ([note] (note-row note nil))
  ([note opts]
   (let [is-daily? (re-seq #"/daily/" (:org/source-file note))
         children-with-tags
         (if is-daily?
           (cond->> (:org/items note)
             true
             (filter item-has-any-tags)
             (:tags opts)
             (filter #(item-has-tags % (:tags opts))))
           nil)]
     [:div
      {:class ["flex" "flex-col"]}
      [:div
       {:class ["flex" "flex-row" "justify-between"]}
       [:h3
        {:class ["hover:underline" "whitespace-nowrap"
                 "pr-2"]}
        [:a
         {:class ["cursor-pointer"]
          :href  (uri/*id->link-uri* (:org/id note))}
         (:org/name note)]]

       ;; [:div
       ;;  {:class ["font-mono"]}
       ;;  (->> note :file/last-modified dates/parse-time-string
       ;;       (t/format (t/formatter "hh:mma")))]

       ;; TODO colorize these tags with
       (tags-list note
                  (->> (item->all-tags note) sort))]

      (->> children-with-tags
           (map (fn [ch]
                  (let [t (item->plain-title ch)]
                    [:div
                     {:class ["pl-4"
                              "flex" "flex-row" "justify-between"]}
                     ;; TODO ideally this is a link to an anchor tag for the daily
                     [:h4 t]
                     (tags-list ch)]))))])))
