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
;; export-mode
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce !export-mode (atom nil))
(defn export-mode? [] @!export-mode)
(defn toggle-export-mode [] (swap! !export-mode not))

(comment
  (not nil)
  (export-mode?)
  (toggle-export-mode))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO move to function and pull from config
;; so that users can opt-in to more of these
(def allowed-tags
  #{"til" "talk" "bugstory" "hammock" "publish" "goals"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; note defs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn note-defs
  "Returns a list of note defs.

  'defs' b/c this is not a full 'note', but just
  some persisted options related to a note."
  []
  (sys/start! `*config*)
  (->>
    (:notes @*config* {})
    (map (fn [[path def]]
           (assoc def :org/short-path path)))))

(defn note-def
  "Returns a single note def for the passed :org/short-path."
  [short-path]
  (sys/start! `*config*)
  ((:notes @*config* {}) short-path))

(defn persist-note-def
  "Adds the passed note to the :notes config.
  Expects at least :org/short-path on the config. "
  [note]
  (let [short-path (:org/short-path note)]
    (if-not short-path
      (println "[ERROR: config/update-note]: no :short-path for passed note")
      (-> @*config*
          (update-in [:notes short-path] merge note)
          write-config))
    (reload-config)))

(defn drop-note-def
  "Removes the note at the passed `:org/short-path` from the :notes config."
  [short-path]
  (-> @*config*
      (update :notes dissoc short-path)
      write-config)
  (reload-config))

(comment
  (note-defs)
  (note-def "garden/some-note.org")
  (persist-note-def {:org/short-path "garden/some-note.org"
                     :with/data      :hi/there})
  (drop-note-def "garden/some-note.org"))
