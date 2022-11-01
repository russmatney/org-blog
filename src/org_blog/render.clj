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
   [nextjournal.clerk.viewer :as clerk-viewer]))

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
            :href  "/index.html"} "all"]]]
     [:div
      [:h4
       [:a {:class ["font-mono"
                    "hover:underline"
                    "cursor-pointer"]
            :href  about-link-uri} "about"]]]]]
   [:hr]])

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
  (hiccup2.core/html
    {:mode :html}
    (hiccup.page/doctype :html5)
    [:html
     {:class "overflow-hidden min-h-screen dark"}
     [:head
      [:title title]
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

      ;; expanded (clerk-view/include-css+js) and wrapped strs in (hiccup/raw) to prevent escaping
      [:script {:type "text/javascript", :src "https://cdn.tailwindcss.com?plugins=typography"}]
      [:script
       (hiccup2.core/raw
         "tailwind.config = {  darkMode: \"class\",  content: [\"./public/build/index.html\", \"./public/build/**/*.html\", \"./build/viewer.js\"],  safelist: ['dark'],  theme: {    extend: {},    fontFamily: {      sans: [\"Fira Sans\", \"-apple-system\", \"BlinkMacSystemFont\", \"sans-serif\"],      serif: [\"PT Serif\", \"serif\"],      mono: [\"Fira Mono\", \"monospace\"]    }  },  variants: {    extend: {},  },  plugins: [],}")]
      [:style {:type "text/tailwindcss"}
       (hiccup2.core/raw
         "@tailwind base;\n@tailwind components;\n@tailwind utilities;\n\n@layer base {\n  html {\n    font-size: 18px;\n  }\n  @media (max-width: 600px) {\n    html {\n      font-size: 16px;\n    }\n  }\n  .font-condensed { font-family: \"Fira Sans Condensed\", sans-serif; }\n  .font-inter     { font-family: \"Inter\", sans-serif; }\n  body {\n    @apply font-serif antialiased text-gray-900 sm:overscroll-y-none;\n  }\n  code, .code {\n    @apply font-mono text-sm text-gray-900 bg-slate-50 px-0.5 py-px rounded dark:bg-gray-800;\n  }\n  code::before, code::after { @apply content-none !important; }\n  h1, h3, h4, h5, h6 {\n    @apply font-condensed font-bold mt-8 first:mt-0;\n  }\n  h2 {\n    /*We cannot collapse margins due to nesting but we want to*/\n    /*keep the h2’s large margin visible*/\n    @apply font-condensed font-bold mt-8 first:mt-2;\n  }\n  h1 { @apply text-4xl; }\n  h2 { @apply text-3xl; }\n  h3 { @apply text-2xl; }\n\n  button { @apply focus:outline-none; }\n  strong { @apply font-bold; }\n  em     { @apply italic; }\n  pre    { @apply m-0 font-mono; }\n}\n\n/* Compatibility */\n/* --------------------------------------------------------------- */\n/* TODO: Verify which colors are in use and replace with Tailwind\n   colors accordingly. Move Nj-specific styles out of here. */\n\n:root {\n  --teal-color: #31afd0;\n  --dark-teal-color: #095960;\n  --near-black-color: #2e2e2c;\n  --red-color: #d64242;\n  --dark-blue-color: #1f2937;\n  --dark-blue-60-color: rgba(28, 42, 56, 0.6);\n  --gray-panel-color: rgba(239, 241, 245, 1.000);\n  --brand-color: var(--dark-blue-color);\n  --link-color: #5046e4;\n  --command-bar-selected-color: var(--teal-color);\n}\n\n.serif      { @apply font-serif; }\n.sans-serif { @apply font-sans; }\n.monospace  { @apply font-mono; }\n.inter      { @apply font-inter; }\n\n.border-color-teal { border-color: var(--dark-teal-color); }\n.teal { color: var(--teal-color); }\n.bg-dark-blue { background: var(--dark-blue-color); }\n.bg-dark-blue-60 { background: rgba(28, 42, 56, 0.6); }\n.bg-gray-panel { background: var(--gray-panel-color); }\n.text-dark-blue  { color: var(--dark-blue-color); }\n.text-dark-blue-60 { color: var(--dark-blue-60-color); }\n.border-dark-blue-30 { border-color: rgba(28, 42, 56, 0.6); }\n.text-brand { color: var(--dark-blue-color); }\n.bg-brand { background: var(--dark-blue-color); }\n.text-selected { color: white; }\n.red { color: var(--red-color); }\n\n/* Disclose Button */\n/* --------------------------------------------------------------- */\n\n.disclose {\n  @apply content-none border-solid cursor-pointer inline-block relative mr-[3px] top-[-2px] transition-all;\n  border-color: var(--near-black-color) transparent;\n  border-width: 6px 4px 0;\n}\n.disclose:hover {\n  border-color: var(--near-black-color) transparent;\n}\n.dark .disclose,\n.dark .disclose:hover {\n  border-color: white transparent;\n}\n.disclose.collapsed {\n  @apply rotate-[-90deg];\n}\n\n/* Layout */\n/* --------------------------------------------------------------- */\n\n.page {\n  @apply max-w-5xl mx-auto px-12 box-border flex-shrink-0;\n}\n.max-w-prose { @apply max-w-[46rem] !important; }\n.max-w-wide  { @apply max-w-3xl !important; }\n\n/* List Styles */\n/* --------------------------------------------------------------- */\n\n.task-list-item + .task-list-item,\n.viewer-markdown ul ul {\n  @apply mt-1 mb-0;\n}\n\n/* compact TOC */\n.viewer-markdown .toc ul {\n  list-style: none;\n  @apply my-1;\n}\n\n/* Code Viewer */\n/* --------------------------------------------------------------- */\n\n.viewer-code {\n  @apply font-mono bg-slate-100 rounded-sm text-sm overflow-x-auto dark:bg-gray-800;\n}\n.viewer-code .cm-content {\n  @apply py-4 px-8;\n}\n@media (min-width: 960px){\n  .viewer-notebook .viewer-code .cm-content {\n    @apply py-4 pl-12;\n  }\n}\n/* Don’t show focus outline when double-clicking cell in Safari */\n.cm-scroller { @apply focus:outline-none; }\n\n/* Syntax Highlighting */\n/* --------------------------------------------------------------- */\n\n.inspected-value { @apply text-xs font-mono leading-[1.25rem]; }\n.cmt-strong, .cmt-heading { @apply font-bold; }\n.cmt-italic, .cmt-emphasis { @apply italic; }\n.cmt-strikethrough { @apply line-through; }\n.cmt-link { @apply underline; }\n.untyped-value { @apply whitespace-nowrap; }\n\n.cm-editor, .cmt-default, .viewer-result {\n  @apply text-slate-800 dark:text-slate-300;\n}\n.cmt-keyword {\n  @apply text-purple-800 dark:text-pink-400;\n}\n.cmt-atom, .cmt-bool, .cmt-url, .cmt-contentSeparator, .cmt-labelName {\n  @apply text-blue-900 dark:text-blue-300;\n}\n.cmt-inserted, .cmt-literal {\n  @apply text-emerald-700 dark:text-emerald-200;\n}\n.cmt-string, .cmt-deleted {\n  @apply text-rose-700 dark:text-sky-300;\n}\n.cmt-italic.cmt-string {\n  @apply dark:text-sky-200;\n}\n.cmt-regexp, .cmt-escape {\n  @apply text-orange-500 dark:text-orange-300;\n}\n.cmt-variableName {\n  @apply text-blue-800 dark:text-sky-300;\n}\n.cmt-typeName, .cmt-namespace {\n  @apply text-emerald-600 dark:text-emerald-300;\n}\n.cmt-className {\n  @apply text-teal-600 dark:text-teal-200;\n}\n.cmt-macroName {\n  @apply text-teal-700 dark:text-teal-200;\n}\n.cmt-propertyName {\n  @apply text-blue-700 dark:text-blue-200;\n}\n.cmt-comment {\n  @apply text-slate-500 dark:text-slate-400;\n}\n.cmt-meta {\n  @apply text-slate-600 dark:text-slate-400;\n}\n.cmt-invalid {\n  @apply text-red-500 dark:text-red-300;\n}\n\n.result-data {\n  @apply font-mono text-sm overflow-x-auto whitespace-nowrap leading-normal;\n}\n.result-data::-webkit-scrollbar, .path-nav::-webkit-scrollbar {\n  @apply h-0;\n}\n.result-data-collapsed {\n  @apply whitespace-nowrap;\n}\n.result-data-field {\n  @apply ml-4 whitespace-nowrap;\n}\n.result-data-field-link{\n  @apply ml-4 whitespace-nowrap cursor-pointer;\n}\n.result-data-field-link:hover {\n  @apply text-black bg-black/5;\n}\n.result-text-empty {\n  color: rgba(0,0,0,.3);\n}\n.browsify-button:hover {\n  box-shadow: -2px 0 0 2px #edf2f7;\n}\n\n/* Prose */\n/* --------------------------------------------------------------- */\n\n.viewer-notebook,\n.viewer-markdown {\n  @apply prose\n    dark:prose-invert\n    prose-a:text-blue-600 prose-a:no-underline hover:prose-a:underline\n    dark:prose-a:text-blue-300\n    prose-p:mt-4 prose-p:leading-snug\n    prose-ol:mt-4 prose-ol:mb-6 prose-ol:leading-snug\n    prose-ul:mt-4 prose-ul:mb-6 prose-ul:leading-snug\n    prose-blockquote:mt-4 prose-blockquote:leading-snug\n    prose-hr:mt-6 prose-hr:border-t-2 prose-hr:border-solid prose-hr:border-slate-200\n    prose-figure:mt-4\n    prose-figcaption:mt-2 prose-figcaption:text-xs\n    prose-headings:mb-4\n    prose-table:mt-0\n    prose-th:mb-0\n    prose-img:my-0\n    prose-code:font-medium prose-code:bg-slate-100\n    max-w-none;\n}\n.viewer-markdown blockquote p:first-of-type:before,\n.viewer-markdown blockquote p:last-of-type:after {\n  @apply content-none;\n}\n\n/* Images */\n/* --------------------------------------------------------------- */\n\n\n/* Todo Lists */\n/* --------------------------------------------------------------- */\n\n.contains-task-list {\n  @apply pl-6 list-none;\n}\n.contains-task-list input[type=\"checkbox\"] {\n  @apply appearance-none h-4 w-4 rounded border border-slate-200 relative mr-[0.3rem] ml-[-1.5rem] top-[0.15rem];\n}\n.contains-task-list input[type=\"checkbox\"]:checked {\n  @apply border-indigo-600 bg-indigo-600 bg-no-repeat bg-contain;\n  background-image: url(\"data:image/svg+xml,%3csvg viewBox='0 0 16 16' fill='white' xmlns='http://www.w3.org/2000/svg'%3e%3cpath d='M12.207 4.793a1 1 0 010 1.414l-5 5a1 1 0 01-1.414 0l-2-2a1 1 0 011.414-1.414L6.5 9.086l4.293-4.293a1 1 0 011.414 0z'/%3e%3c/svg%3e\");\n}\n\n/* Markdown TOC */\n/* --------------------------------------------------------------- */\n\n.viewer-markdown .toc      { @apply mt-4; }\n.viewer-markdown h1 + .toc { @apply mt-8; }\n\n.viewer-markdown .toc h1,\n.viewer-markdown .toc h2,\n.viewer-markdown .toc h3,\n.viewer-markdown .toc h4,\n.viewer-markdown .toc h5,\n.viewer-markdown .toc h6 {\n  @apply text-base text-indigo-600 font-sans my-0;\n}\n.viewer-markdown .toc a {\n  @apply text-indigo-600 font-normal no-underline hover:underline;\n}\n.viewer-markdown .toc li    { @apply m-0; }\n.viewer-markdown .toc ul ul { @apply pl-4; }\n\n/* Notebook Spacing */\n/* --------------------------------------------------------------- */\n\n.viewer-notebook { @apply py-16; }\n#clerk-static-app .viewer-notebook { @apply pt-[0.8rem] pb-16; }\n.viewer-markdown *:first-child:not(.viewer-code):not(li):not(h2) { @apply mt-0; }\n/*.viewer + .viewer { @apply mt-6; }*/\n.viewer + .viewer-result { @apply mt-0; }\n.viewer-code + .viewer-result { @apply mt-3; }\n.viewer-markdown + .viewer-markdown { @apply mt-0; }\n\n/* Sidenotes */\n/* --------------------------------------------------------------- */\n\n.sidenote-ref {\n  @apply top-[-3px] inline-flex justify-center items-center w-[18px] h-[18px]\n    rounded-full bg-slate-100 border border-slate-300 hover:bg-slate-200 hover:border-slate-300\n    m-0 ml-[4px] cursor-pointer;\n}\n.sidenote {\n  @apply hidden float-left clear-both mx-[2.5%] my-4 text-xs relative w-[95%];\n}\n.sidenote-ref.expanded + .sidenote {\n  @apply block;\n}\n@media (min-width: 860px) {\n  .sidenote-ref {\n    @apply top-[-0.5em] w-auto h-auto inline border-0 bg-transparent m-0 pointer-events-none;\n  }\n  .sidenote sup { @apply inline; }\n  .viewer-markdown .contains-sidenotes p { @apply max-w-[65%]; }\n  .viewer-markdown p .sidenote {\n    @apply mr-[-54%] mt-[0.2rem] w-1/2 float-right clear-right relative block;\n  }\n}\n.viewer-code + .viewer:not(.viewer-markdown):not(.viewer-code):not(.viewer-code-folded),\n.viewer-code-folded + .viewer:not(.viewer-markdown):not(.viewer-code):not(.viewer-code-folded),\n.viewer-result + .viewer-result {\n  @apply mt-2;\n}\n.viewer-code + .viewer-code-folded {\n  @apply mt-4;\n}\n.viewer-result {\n  @apply leading-tight mb-6;\n}\n.viewer-result figure {\n  @apply mt-0 !important;\n}\n@media (min-width: 768px) {\n  .devcard-desc > div {\n    @apply max-w-full m-0;\n  }\n}\n\n/* Command Palette */\n/* --------------------------------------------------------------- */\n\n.nj-commands-input {\n  @apply bg-transparent text-white;\n}\n.nj-context-menu-item:hover:not([disabled]) {\n  @apply cursor-pointer;\n  background-color: rgba(255,255,255,.14);\n}\n\n/* Devdocs */\n/* --------------------------------------------------------------- */\n\n.logo, .logo-white {\n  @apply block indent-[-999em];\n  background: url(/images/nextjournal-logo.svg) center center no-repeat;\n}\n.devdocs-body {\n  @apply font-inter;\n}\n\n/* Workarounds */\n/* --------------------------------------------------------------- */\n\n/* Fixes vega viewer resizing into infinity */\n.vega-embed .chart-wrapper { @apply h-auto !important; }\n/* fixes fraction separators being overridden by tw’s border-color */\n.katex * { @apply border-black; }\n")]
      [:script {:type "text/javascript"
                :src  "https://storage.googleapis.com/nextjournal-cas-eu/assets/28ktYzexRpt9ZsXvxpxDRnu497pkEeZjEvXB1NMVzfEoPEgsbQXEyM3j5CEucNccte6QGnX1qQxHL2KHfoBRG2FN-viewer.js"}]
      [:link {:type "text/css"
              :href "https://cdn.jsdelivr.net/npm/katex@0.13.13/dist/katex.min.css"
              :rel  "stylesheet"}]
      [:link {:type "text/css"
              :href "https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;700&family=Fira+Mono:wght@400;700&family=Fira+Sans+Condensed:ital,wght@0,700;1,700&family=Fira+Sans:ital,wght@0,400;0,500;0,700;1,400;1,500;1,700&family=PT+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap"
              :rel  "stylesheet"}]]
     [:body.dark:bg-gray-900
      (header)
      [:div.flex
       [:div.flex-auto.h-screen.overflow-y-auto
        [:div.flex.flex-col.items-center.flex-auto
         [:div
          {:class ["w-full" "max-w-prose" "px-8" "viewer-notebook"]}
          content]]]]]]))

(defn write-page [{:keys [path title content]}]
  (ensure-path path)
  (spit path (->html-with-escaping (if title (str main-title " - " title) main-title) content))
  (format-html-file path))

(comment
  (write-page
    {:path    "public/test.html"
     :content [:div
               [:h1 "test page"]
               [:h2 "full of content"]]}))
