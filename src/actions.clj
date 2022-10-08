(ns actions
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require
   [nextjournal.clerk :as clerk]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
{::clerk/visibility {:result :show}}

;; I'm trying to understand and document how to work across clerk's
;; frontend/backend layer. Could I implement an api like this?

^{::clerk/viewer     clerk/table
  ::clerk/visibility {:code :show}
  :clj-kondo/ignore  [:clojure-lsp/unused-public-var]}
(def actions
  [{:label    "reload one"
    :on-click '(fn [e] (v/clerk-eval '(reload!)))}
   {:label    "reload two"
    :on-click '(reload!)}
   {:label    "run this jvm func"
    :on-click (fn [_] (println "i'm printing on the jvm?"))}
   {:label    "run this js func"
    :on-click '(fn [e] (js/alert "i'm a js alert!"))}])

(clerk/html
  [:div.text-center
   '(v/html
      [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
       {:on-click (fn [e] (v/clerk-eval '(reload!)))} "Reload 🎲!"])])
