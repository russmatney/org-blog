(ns org-blog.export
  (:require
   [org-blog.daily :as daily]
   [dates.tick :as dates.tick]))



(defonce linked-ids (atom #{}))

(defn export-dailies
  ([] (export-dailies nil))
  ([opts]
   (let [days-ago (:days-ago opts 7)
         days     (dates.tick/days days-ago)]
     (doseq [day days]
       (daily/export-for-day
         {:day          day
          :id->link-uri (fn [id]
                          (swap! linked-ids conj id)
                          nil)}))

     (println "linked ids" @linked-ids))))

(comment
  (export-dailies {:days-ago 7}))
