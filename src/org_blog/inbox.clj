(ns org-blog.inbox
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [garden.core :as garden]
   [org-crud.core :as org-crud]
   [nextjournal.clerk :as clerk]
   [tick.core :as t]
   [ralphie.zsh :as r.zsh]
   [org-blog.item :as item]))

(def this-ns *ns*)
(def this-file *file*)
(defn reload! []
  (clerk/show! this-file))
^::clerk/no-cache
(def last-load-time (atom (t/now)))

(def nodes-map
  (->>
    {:journal   (-> "~/todo/journal.org" r.zsh/expand)
     :projects  (-> "~/todo/projects.org" r.zsh/expand)
     :today     (garden/daily-path)
     :yesterday (garden/daily-path 1)}
    (map (fn [[k path]]
           [k (org-crud/path->flattened-items path)]))
    (into {})))

(def last-seven (garden/daily-paths 7))
(def last-fortnight (garden/daily-paths 14))
(def last-30 (garden/daily-paths 30))

(def all-nodes (->> (vals nodes-map) (apply concat)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

^{::clerk/no-cache true}
(clerk/html
  [:div.text-center
   (when @last-load-time
     [:div.mt-2.text-white.text-lg
      (str @last-load-time)])
   #_[:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
      {:on-click (fn [e] '(v/clerk-eval '(reload!)))} "Reload 🎲!"]])

;; # Inbox

(clerk/md
  (item/content-for-tag-groups
    all-nodes
    [{:tags         #{"inbox"}
      :parent-names #{"inbox"}}]))
