(ns orzo.sprint
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))

(defn ^:private ^:no-doc sprint-parse-line
  [line]
  (let [split (string/split line #" ")]
    {:sprint (Integer/parseInt (first split))
     :end-date (LocalDate/parse (last split))}))

(defn ^:private ^:no-doc get-sprint-date-pair
  [path]
  (let [lines (some-> path io/file slurp string/split-lines)
        last-sprint (sprint-parse-line (last lines))]
    {:sprint (:sprint last-sprint)
     :end-date (:end-date last-sprint)}))

(defn ^:private ^:no-doc smaller-indicator
  [from-indicator]
  (case from-indicator
    :major :minor
    :minor :patch
    :patch nil
    nil))

(defn ^:private ^:no-doc build-sprint-calendar-semver
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
  "Returns a number based on the sprint of the project. Sprints usually
  emcompass several weeks.

  It receives a map with the following keys:

  - `:sprint-size`: (default 2) how many weeks each sprint usually has
  - `:sprint-file-path`: a path to a configuration file that's used as
  foundation for the calculations

  The file specified follows the convention where each line is
  `<SPRINT_NUM> <YYYY-MM-DD>` in a sequential manner where
  `<SPRINT_NUM>` represents the sprint number and `YYYY-MM-DD`
  represents the last day of the sprint.

  At least one line is mandatory to set the first sprint.

  Thanks to Andre Carvalho for the inspiration behind this idea."
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
