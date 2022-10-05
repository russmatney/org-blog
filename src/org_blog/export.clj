(ns org-blog.export
  (:require
   [org-blog.daily :as daily]
   [dates.tick :as dates.tick]))



(defn export-dailies
  ([] (export-dailies nil))
  ([opts]
   (let [days-ago (:days-ago opts 7)
         days     (dates.tick/days days-ago)]
     (doseq [day days]
       (daily/export-for-day {:day day})))))

(comment
  (export-dailies {:days-ago 14}))
