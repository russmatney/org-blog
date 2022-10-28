(ns org-blog.render
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.java.io :as io]
   [hiccup.page :as hiccup]
   [nextjournal.clerk.analyzer :as clerk-analyzer]
   [nextjournal.clerk.eval :as clerk-eval]
   [nextjournal.clerk.view :as clerk-view]
   [nextjournal.clerk.viewer :as clerk-viewer]))

(defn format-html-file [path]
  (-> ^{:out :string}
      (process/$ tidy -mqi
                 --indent-spaces 1
                 --tidy-mark no
                 --enclose-block-text yes
                 --enclose-text yes
                 ~path)
      process/check :out))

;; --new-inline-tags fn
(comment
  (format-html-file "public/test.html")
  (format-html-file "public/last-modified.html")
  )

(defn eval-notebook
  "Evaluates the notebook identified by its `ns-sym`"
  [ns-sym]
  (try
    (if-let [path (-> ns-sym clerk-analyzer/ns->path (str ".clj"))]
      (if-let [res (io/resource path)]
        (clerk-eval/eval-file res)
        (println "[WARN]: could not create resource for path" path))
      (println "[WARN]: could not create path for ns-symbol" ns-sym))
    (catch Throwable e
      (println "error evaling notebook", ns-sym)
      (println e))))

(def main-title "DangerRuss Notes")
(def about-link-uri "/note/blog_about.html")

(defn header []
  [:div
   {:class ["flex" "flex-col" "items-center"
            "text-gray-900" "dark:text-white"]}
   [:div
    {:class ["flex" "flex-row"
             "items-center"
             "max-w-prose" "w-full" "px-8" "py-2"]}
    [:h3 {:class ["font-mono"]} main-title]

    [:div
     {:class ["ml-auto" "flex" "flex-row" "space-x-4"]}
     [:div
      [:h4
       [:a {:class ["font-mono"
                    "hover:underline"
                    "cursor-pointer"]
            :href  "/index.html"} "all"]]]
     [:div
      [:h4
       [:a {:class ["font-mono"
                    "hover:underline"
                    "cursor-pointer"]
            :href  about-link-uri} "about"]]]]]
   [:hr]])

(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} state]
  (hiccup/html5
    {:class "overflow-hidden min-h-screen"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     (clerk-view/include-css+js)]
    [:body.dark:bg-gray-900
     (header)
     [:div#clerk]
     [:script "let viewer = nextjournal.clerk.sci_viewer
let state =  " (-> state clerk-viewer/->edn pr-str
                       #_(string/replace #" " " \\\\\n")) "
viewer.set_state(viewer.read_string(state))
viewer.mount(document.getElementById('clerk'))\n"
      (when conn-ws?
        "const ws = new WebSocket(document.location.origin.replace(/^http/, 'ws') + '/_ws')
ws.onmessage = msg => viewer.set_state(viewer.read_string(msg.data));
window.ws_send = msg => ws.send(msg);
ws.onopen = () => ws.send('{:path \"' + document.location.pathname + '\"}'); ")]]))

(defn doc->html [evaled-notebook]
  (->html {} {:doc (clerk-view/doc->viewer {} evaled-notebook) :error nil}))

(defn doc->static-html [evaled-notebook]
  (->html {:conn-ws? false} {:doc (clerk-view/doc->viewer {:inline-results? true} evaled-notebook)}))

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ns-sym->html [ns-sym]
  (some-> (eval-notebook ns-sym) doc->html))

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ns-sym->viewer [ns-sym]
  (some-> (eval-notebook ns-sym) (clerk-view/doc->viewer)))

(defn ensure-path [path]
  (let [parent (fs/parent path)]
    (when-not (fs/exists? parent)
      (println "ensuring parent dir exists")
      (fs/create-dirs parent))))

(defn path+ns-sym->spit-static-html [path ns-sym]
  (ensure-path path)
  (spit path (doc->static-html (eval-notebook ns-sym))))

(comment
  (doc->static-html (eval-notebook 'org-blog.daily))
  (path+ns-sym->spit-static-html "test.html" 'org-blog.daily))

(defn ->html-page [title hic]
  (hiccup/html5
    {:class "overflow-hidden min-h-screen dark"}
    [:head
     [:title title]
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     (clerk-view/include-css+js)]
    [:body.dark:bg-gray-900
     (header)
     [:div.flex
      hic]]))

(defn write-page [{:keys [path title content]}]
  (ensure-path path)
  (spit path (->html-page (if title (str main-title " - " title) main-title) content))
  (format-html-file path))

(comment
  (write-page
    {:path    "public/test.html"
     :content [:div
               [:h1 "test page"]
               [:h2 "full of content"]]}))
