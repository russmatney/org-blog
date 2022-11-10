(ns org-blog.render
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.java.io :as io]
   [hiccup.page :as hiccup.page]
   [hiccup2.core :as hiccup2.core]
   [nextjournal.clerk.analyzer :as clerk-analyzer]
   [nextjournal.clerk.eval :as clerk-eval]
   [nextjournal.clerk.view :as clerk-view]
   [nextjournal.clerk.viewer :as clerk-viewer]

   [org-blog.config :as config]))

(defn format-html-file [path]
  (-> ^{:out :string}
      (process/$ tidy -mqi
                 --indent-spaces 1
                 --tidy-mark no
                 --enclose-block-text yes
                 --enclose-text yes
                 --drop-empty-elements no
                 ~path)
      process/check :out))

;; --new-inline-tags fn
(comment
  (format-html-file "public/test.html")
  (format-html-file "public/last-modified.html"))

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
            :href  "/index.html"} "home"]]]
     [:div
      [:h4
       [:a {:class ["font-mono"
                    "hover:underline"
                    "cursor-pointer"]
            :href  about-link-uri} "about"]]]]]
   [:hr]])

(defn footer []
  (let [mastodon-href (config/get-mastodon-href)]
    [:div
     {:class ["flex" "flex-col" "items-center" "text-gray-900" "dark:text-white"]}

     [:hr]
     [:div
      {:class ["flex" "flex-row" "space-x-4"]}
      [:div
       [:h4
        [:a {:class ["font-mono"
                     "hover:underline"
                     "cursor-pointer"]
             :href  "/index.html"} "home"]]]
      [:div
       [:h4
        [:a {:class ["font-mono"
                     "hover:underline"
                     "cursor-pointer"]
             :href  about-link-uri} "about"]]]

      (when mastodon-href
        [:div
         [:h4
          [:a {:class ["font-mono"
                       "hover:underline"
                       "cursor-pointer"]
               :href  mastodon-href
               :rel   "me"} "mastodon"]]])]]))

(defn ->html [{:keys [conn-ws?] :or {conn-ws? true}} state]
  (hiccup.page/html5
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

(defn ->html-with-escaping [title content]
  (let [ga-id (config/get-google-analytics-id)]
    (hiccup2.core/html
      {:mode :html}
      (hiccup.page/doctype :html5)
      [:html
       {:class "overflow-hidden min-h-screen dark"}
       [:head
        [:title title]
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

        [:link {:type "text/css" :rel "stylesheet" :href "/styles.css"}]
        [:script {:type "text/javascript"
                  :src  "https://storage.googleapis.com/nextjournal-cas-eu/assets/28ktYzexRpt9ZsXvxpxDRnu497pkEeZjEvXB1NMVzfEoPEgsbQXEyM3j5CEucNccte6QGnX1qQxHL2KHfoBRG2FN-viewer.js"}]
        [:link {:type "text/css"
                :href "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css"
                :rel  "stylesheet"}]
        [:link {:type "text/css"
                :href "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap"
                :rel  "stylesheet"}]

        (when true ;; TODO support 'production' build to prevent livejs inclusion?
          [:script {:type "text/javascript"
                    :src  "https://livejs.com/live.js"}])

        (when ga-id
          [:script {:async true :src (str "https://www.googletagmanager.com/gtag/js?id=" ga-id)}])
        (when ga-id
          [:script
           (hiccup2.core/raw
             (str "window.dataLayer = window.dataLayer || [];
function gtag() { dataLayer.push(arguments); }
gtag('js', new Date());
gtag('config', '" ga-id "');"))])]
       [:body.dark:bg-gray-900
        (header)
        [:div.flex
         [:div.flex-auto.h-screen.overflow-y-auto
          [:div.flex.flex-col.items-center.flex-auto
           [:div
            {:class ["w-full" "max-w-prose" "px-8" "viewer-notebook"]}
            content
            (footer)]]]]]])))

(defn write-page [{:keys [path title content]}]
  (ensure-path path)
  (spit path (->html-with-escaping (if title (str main-title " - " title) main-title) content))
  (format-html-file path))

(defn write-styles []
  (println "[PUBLISH]: exporting tailwind styles")
  (let [content-path (str (config/blog-content-public) "/*.html")]
    (->
      ^{:out :string}
      (process/$ npx tailwindcss -c "resources/tailwind.config.js" -i "resources/styles.css"
                 --content ~content-path
                 -o ~(str (config/blog-content-public) "/styles.css"))
      process/check :out)))

(comment
  (write-page
    {:path    "public/test.html"
     :content [:div
               [:h1 "test page"]
               [:h2 "full of content"]]}))
