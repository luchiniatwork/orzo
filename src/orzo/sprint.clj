(ns orzo.spring
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))


(defn ^:private sprint-parse-line
  [line]
  (let [split (string/split line #" ")]
    {:sprint (Integer/parseInt (first split))
     :end-date (LocalDate/parse (last split))}))

(defn ^:private get-sprint-date-pair
  [path]
  (let [lines (some-> path io/file slurp string/split-lines)
        last-sprint (sprint-parse-line (last lines))]
    {:sprint (:sprint last-sprint)
     :end-date (:end-date last-sprint)}))

(defn ^:private build-sprint-calendar-semver
  [orig-semver indicator sprint-number]
  (let [at-indicator-value (get orig-semver indicator)
        smaller-indicator' (smaller-indicator indicator)
        smaller-indicator'' (smaller-indicator smaller-indicator')
        smaller-indicator-value (get orig-semver smaller-indicator')]
    (cond-> orig-semver
      (not= at-indicator-value sprint-number)
      (assoc smaller-indicator' 0)

      (and smaller-indicator'' (not= at-indicator-value sprint-number))
      (assoc smaller-indicator'' 0)
      
      (= at-indicator-value sprint-number)
      (assoc smaller-indicator' (inc smaller-indicator-value))
      
      :always (assoc indicator sprint-number))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Info functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn calendar-sprint-number
  [{:keys [sprint-size sprint-file-path]
    :or {sprint-size 2}}]
  (if-not sprint-file-path
    (throw (ex-info "sprint-file-path must be provided" {})))
  (let [{:keys [sprint end-date]} (get-sprint-date-pair sprint-file-path)
        now (LocalDate/now)
        weeks (-> ChronoUnit/WEEKS (.between end-date now))]
    (if (.isAfter now end-date)
      (+ (int (Math/ceil (/ weeks sprint-size)))
         sprint)
      sprint)))
