(ns org-blog.config
  (:require
   [clojure.pprint :as pprint]
   [aero.core :as aero]
   [clojure.java.io :as io]
   [systemic.core :as sys :refer [defsys]]))


(def res (io/resource "config.edn"))

(defn ->config [] (aero/read-config res))

(defsys *config* :start (atom (->config)))

(defn reload-config []
  (sys/start! `*config*)
  (reset! *config* (->config)))

(defn write-config
  "Writes the current config to `resources/clawe.edn`"
  [updated-config]
  (sys/start! `*config*)
  (let [updated-config
        ;; note this is not a deep merge
        (merge @*config* updated-config)]
    (pprint/pprint updated-config (io/writer res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO move to function and pull from config
;; so that users can opt-in to more of these
(def allowed-tags
  #{"til" "talk" "bugstory" "hammock" "publish" "goals"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; published notes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn published-notes
  "Returns a list of published notes."
  []
  (sys/start! `*config*)
  (->>
    (:published-notes @*config* {})
    (map (fn [[path def]]
           (assoc def :org/short-path path)))))

(defn published-note
  "Returns a single note for the passed :org/short-path."
  [short-path]
  (sys/start! `*config*)
  ((:published-notes @*config* {}) short-path))

(defn publish-note
  "Adds the passed note to the :published-notes config.
  Expects at least :org/short-path on the config."
  [note]
  (let [short-path (:org/short-path note)]
    (if-not short-path
      (println "[ERROR: config/update-note]: no :short-path for passed note")
      (-> @*config*
          (update-in [:published-notes short-path] merge note)
          write-config))
    (reload-config)))

(comment
  (published-notes)
  (published-note "garden/some-note.org")
  (publish-note {:org/short-path "garden/some-note.org"
                 :with/data      :hi/there}))
