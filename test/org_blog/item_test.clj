(ns org-blog.item-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [org-crud.parse :as parse]
   [org-blog.item :as item]))

(defn prop-bucket [test-data]
  (->>
    [":PROPERTIES:"
     (when-let [id (:id test-data)]
       (str ":ID:       " id))
     ":END:"]
    (remove nil?)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def daily-test-case
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
     "*** [X] restore proper tauri clerk notebook toggle"
     "CLOSED: [2022-10-30 Sun 13:56]"
     "i think this is just the title check"
     "* coleslaw - a common lisp static blog tool"
     "https://github.com/coleslaw-org/coleslaw looks pretty cool!"
     ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest item->hiccup-content-test
  (testing "displays basic h1s"
    (let [t      "some title"
          node   (parse/parse-lines [(str "#+title: " t)])
          hiccup (item/item->hiccup-headline node)]
      (is (= hiccup [:h1 t]))))

  (testing "displays expected daily-test-case headers"
    (let [content        (item/item->hiccup-content daily-test-case)
          nodes          (tree-seq vector? seq content)
          header-nodes   (->> nodes (filter
                                      (fn [node] (and (vector? node)
                                                      (#{:h1 :h2 :h3} (first node))))))
          header-elems   (->> header-nodes
                              (map first)
                              frequencies)
          header-content (->> header-nodes
                              (map last)
                              (into #{}))]
      (is (= {:h1 2
              :h2 1
              :h3 2} header-elems))
      (is (= #{"2022-10-30"
               "maybe goals"
               "clawe"
               "restore proper tauri clerk notebook toggle"
               "watch for burying situations"}
             header-content)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest item->hiccup-content-test-body
  (testing "displays expected daily-test-case bodies"
    (let [content (item/item->hiccup-content body-test-case)
          nodes   (tree-seq vector? seq content)
          p-nodes (->> nodes
                       (filter (fn [node]
                                 (and (vector? node)
                                      (#{:p} (first node)))))
                       (into #{}))]
      (is (= 4 (count p-nodes)))
      (is (= #{[:p "Keyword is 'windowed projector' - right click the source in obs, then 'windowed\nprojector' to get the video to pop out"]
               [:p "Did this a month or two ago, but couldn't at all remember it this morning.\nhere's the vid:\nhttps://www.youtube.com/watch?v=Z9S_2FmLCm8"]
               [:p "See also: [[id:cffb5f16-8e58-4f82-9667-85b7785a4bfd][getting started with obs studios]]"]
               [:p "i think this is just the title check"]}
             p-nodes)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest item->hiccup-content-render-hrefs-test
  #_(testing "displays header urls as hrefs"
      (let [content (item/item->hiccup-content daily-test-case)
            nodes   (tree-seq vector? seq content)
            a-nodes (->> nodes
                         (filter (fn [node]
                                   (and (vector? node)
                                        (#{:a} (first node)))))
                         (into #{}))]
        (is (= 2 (count a-nodes)))))

  (testing "displays body urls as hrefs"
    (let [content (item/item->hiccup-content body-test-case)
          nodes   (tree-seq vector? seq content)
          a-nodes (->> nodes
                       (filter (fn [node]
                                 (and (vector? node)
                                      (#{:a} (first node)))))
                       (into #{}))]
      a-nodes
      (is (= 2 (count a-nodes))))))


#_(deftest item->hiccup-content-render-roam-links-test
    (testing "displays header links as hrefs"
      (let [content (item/item->hiccup-content daily-test-case)
            nodes   (tree-seq vector? seq content)
            a-nodes (->> nodes
                         (filter (fn [node]
                                   (and (vector? node)
                                        (#{:a} (first node)))))
                         (into #{}))]
        (is (= 2 (count a-nodes)))))

    (testing "displays body links as hrefs"
      (let [content (item/item->hiccup-content body-test-case)
            nodes   (tree-seq vector? seq content)
            a-nodes (->> nodes
                         (filter (fn [node]
                                   (and (vector? node)
                                        (#{:a} (first node)))))
                         (into #{}))]
        (is (= 2 (count a-nodes))))))
