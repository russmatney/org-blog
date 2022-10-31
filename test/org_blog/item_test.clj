(ns org-blog.item-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [org-crud.parse :as parse]
   [org-blog.item :as item]
   [clojure.string :as string]
   [org-blog.uri :as uri]))

(defn hiccup->elements
  "Given some hiccup, returns all the elements matching the passed set of elem types.

  (hiccup->elements some-hiccup #{:a :span :p}) => list of matching hiccup vectors"
  ([content] (hiccup->elements content nil))
  ([content elem-types]
   (let [elem-types (or elem-types :all-types)]
     (->> (tree-seq vector? seq content)
          (filter (fn [node]
                    (and (vector? node)
                         (or (and (set? elem-types)
                                  (elem-types (first node)))
                             (= :all-types elem-types)))))))))

(defn elems->strings
  "For a flat list of hiccup elems, collects all the strings in a set."
  [elems]
  (->> elems
       (mapcat #(filter string? %))
       (remove #{" "})
       (into #{})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def headers-test-case
  (parse/parse-lines
    [":PROPERTIES:"
     ":ID:       d0417009-ad54-4f22-a577-6cb00df5a50e"
     ":END:"
     "#+title: 2022-10-30"
     ""
     "* maybe goals :goals:"
     "** [[id:bfc118eb-23b2-42c8-8379-2b0e249ddb76][clawe]]"
     "*** [X] restore proper tauri clerk notebook toggle"
     "CLOSED: [2022-10-30 Sun 13:56]"
     "i think this is just the title check"
     "*** watch for burying situations"]))

(deftest item->hiccup-content-test
  (testing "displays basic h1s"
    (let [t      "some title"
          node   (parse/parse-lines [(str "#+title: " t)])
          hiccup (item/item->hiccup-headline node)]
      (is (= hiccup [:h1 [:span t]]))))

  (testing "displays expected headers"
    (let [content      (item/item->hiccup-content headers-test-case)
          headers      (hiccup->elements content #{:h1 :h2 :h3})
          header-freqs (->> headers (map first) frequencies)
          strings      (->> headers (mapcat (fn [el] (hiccup->elements el #{:span})))
                            elems->strings)]
      (is (= {:h1 2 :h2 1 :h3 2} header-freqs))
      (is (= #{"2022-10-30" "maybe goals" "clawe"
               "restore proper tauri clerk notebook toggle"
               "watch for burying situations"}
             strings)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def body-test-case
  (parse/parse-lines
    [":PROPERTIES:"
     ":ID:       d0417009-ad54-4f22-a577-6cb00df5a50e"
     ":END:"
     "#+title: 2022-10-30"
     ""
     "* [[id:8d9423ca-73f8-4cc1-8f46-3e19a11d8d22][obs]] see yourself while streaming with a 'windowed projector' :obs:"
     "Did this a month or two ago, but couldn't at all remember it this morning."
     "here's the vid:"
     "https://www.youtube.com/watch?v=Z9S_2FmLCm8"
     ""
     "See also: [[id:cffb5f16-8e58-4f82-9667-85b7785a4bfd][getting started with obs studios]]"
     ""
     "Keyword is 'windowed projector' - right click the source in obs, then 'windowed"
     "projector' to get the video to pop out"
     "** [X] restore proper tauri clerk notebook toggle"
     "CLOSED: [2022-10-30 Sun 13:56]"
     "i think this is just the title check"
     "* coleslaw - a common lisp static blog tool"
     "https://github.com/coleslaw-org/coleslaw looks pretty cool!"
     ""
     "* some vid i found"
     "check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this"
     "video]] on youtube!"]))

(deftest item->hiccup-content-test-body
  (testing "displays expected bodies"
    (let [content (item/item->hiccup-content body-test-case)
          p-nodes (hiccup->elements content #{:p})
          strings (->> p-nodes (mapcat #(hiccup->elements % #{:span})) elems->strings)

          expected-strings
          #{"Keyword is 'windowed projector' - right click the source in obs, then 'windowed projector' to get the video to pop out"
            "getting started with obs studios"
            "https://www.youtube.com/watch?v=Z9S_2FmLCm8"
            "See also:"
            "i think this is just the title check"
            "Did this a month or two ago, but couldn't at all remember it this morning. here's the vid:"
            "https://github.com/coleslaw-org/coleslaw"
            "looks pretty cool!"
            "check out"
            "this video"
            "on youtube!"

            }]
      ;; all these strings should be in there
      (is (= expected-strings strings))

      ;; should be split into paragraphs
      (is (= 6 (count p-nodes))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest item->hiccup-content-render-hrefs-test
  (testing "displays header urls as hrefs"
    (let [input-lines
          ["** check out this video: https://www.youtube.com/watch?v=Z9S_2FmLCm8 on youtube!"]
          content (->> input-lines parse/parse-lines item/item->hiccup-content)
          a-elems (hiccup->elements content #{:a})
          a-elem  (some->> a-elems first)

          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"check out this video:"
                             "https://www.youtube.com/watch?v=Z9S_2FmLCm8"
                             "on youtube!"}]
      (is (= 1 (count a-elems)))
      (is (= "https://www.youtube.com/watch?v=Z9S_2FmLCm8" (-> a-elem second :href)))
      (is (= strings expected-strings))))

  (testing "displays body urls as hrefs"
    (let [input-lines
          ["check out this video: https://www.youtube.com/watch?v=Z9S_2FmLCm8 on youtube!"]
          content (->> input-lines parse/parse-lines item/item->hiccup-content)
          a-elems (hiccup->elements content #{:a})
          a-elem  (some->> a-elems first)

          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"check out this video:"
                             "https://www.youtube.com/watch?v=Z9S_2FmLCm8"
                             "on youtube!"}]
      (is (= 1 (count a-elems)))
      (is (= "https://www.youtube.com/watch?v=Z9S_2FmLCm8" (-> a-elem second :href)))
      (is (= strings expected-strings)))))

(deftest item->hiccup-content-render-org-links-test
  (testing "displays header org-links as hrefs"
    (let [input-lines
          ["** check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this video]] on youtube!"]
          content (->> input-lines parse/parse-lines item/item->hiccup-content)
          a-elems (hiccup->elements content #{:a})
          a-elem  (some->> a-elems first)

          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"check out" "this video" "on youtube!"}]
      (is (= 1 (count a-elems)))
      (is (= "https://www.youtube.com/watch?v=Z9S_2FmLCm8" (-> a-elem second :href)))
      (is (= expected-strings strings))))

  (testing "displays body org-links as hrefs"
    (let [input-lines
          ["check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this video]] on youtube!"]
          content (->> input-lines parse/parse-lines item/item->hiccup-content)
          a-elems (hiccup->elements content #{:a})
          a-elem  (some->> a-elems first)

          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"check out" "this video" "on youtube!"}]
      (is (= 1 (count a-elems)))
      (is (= "https://www.youtube.com/watch?v=Z9S_2FmLCm8" (-> a-elem second :href)))
      (is (= expected-strings strings)))))

(deftest item->hiccup-links-multi-line
  (testing "handles body org-links with line breaks"
    (let [input-lines
          ["check out [[https://www.youtube.com/watch?v=Z9S_2FmLCm8][this"
           "video]] on youtube!"]
          content (->> input-lines parse/parse-lines item/item->hiccup-content)
          a-elems (hiccup->elements content #{:a})
          a-elem  (some->> a-elems first)

          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"check out" "this video" "on youtube!"}]

      (is (= 1 (count a-elems)))
      (is (= "https://www.youtube.com/watch?v=Z9S_2FmLCm8" (-> a-elem second :href)))
      (is (= expected-strings strings)))))

(deftest item->hiccup-links-support-id-links
  (testing "supports published links"
    (let [some-id      (str (random-uuid))
          some-uri     "/notes/some-note.html"
          id->link-uri (fn [_] some-uri)

          input-lines [(str "[[id:" some-id "][this note]]")]

          content
          (with-bindings
            {#'uri/*id->link-uri* id->link-uri}
            (->> input-lines
                 parse/parse-lines
                 item/item->hiccup-content))

          a-elems          (hiccup->elements content #{:a})
          a-elem           (some->> a-elems first)
          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"this note"}]
      (is (= 1 (count a-elems)))
      (is (= some-uri (-> a-elem second :href)))
      (is (= expected-strings strings))))

  (testing "unpublished links show as plain text"
    (let [some-id      (str (random-uuid))
          id->link-uri (fn [_] nil)
          input-lines  [(str "[[id:" some-id "][this note]]")]

          content
          (with-bindings
            {#'uri/*id->link-uri* id->link-uri}
            (->> input-lines
                 parse/parse-lines
                 item/item->hiccup-content))

          a-elems          (hiccup->elements content #{:a})
          strings          (-> content (hiccup->elements #{:span}) elems->strings)
          expected-strings #{"this note"}]
      (is (= 0 (count a-elems)))
      (is (= expected-strings strings)))) )





(deftest item->hiccup-src-block-test
  (let [input-lines
        (string/split-lines "
* some loop/recur :code:

#+BEGIN_SRC clojure
(defn some-func [s]
  (println s))
#+END_SRC")
        content (->> input-lines parse/parse-lines item/item->hiccup-content)]
    content

    ;; TODO consider helpers for colorizing code block
    ;; TODO consider org -> markdown -> html
    ))
