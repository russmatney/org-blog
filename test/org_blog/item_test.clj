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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; basic
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest item->hiccup-headline-test
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

(comment
  (def x
    [:div
     [:div
      [:h1 "2022-10-30"]
      [:div "body-content"]]
     [:div
      [:h1 "maybe goals"]
      [:div "#goals"]
      [:div "body-content"]]
     [:div
      [:h2 "clawe"]
      [:div "body-content"]]
     [:div
      [:h3 "restore proper tauri clerk notebook toggle" ]
      [:div "body-content"]]
     [:div
      [:h3 "watch for burying situations" ]
      [:div "body-content"]]])

  (tree-seq vector? seq x)

  )
