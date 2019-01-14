(ns orzo.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))

;; Utilities

(defn ^:private parse-number
  [n]
  (try
    (Integer/parseInt n)
    (catch java.lang.NumberFormatException e
      nil)))

(defn ^:private parse-semver
  [semver]
  (let [matches (re-matches #"\D*(?:(\d+)\.?)?(?:(\d+)\.?)?(\d+)?.*" semver)]
    {:major (parse-number (nth matches 1))
     :minor (parse-number (nth matches 2))
     :patch (parse-number (nth matches 3))}))

(defn ^:private greater-indicator
  [from-indicator]
  (case from-indicator
    :major nil
    :minor :major
    :patch :minor
    nil))

(defn ^:private smaller-indicator
  [from-indicator]
  (case from-indicator
    :major :minor
    :minor :patch
    :patch nil
    nil))

(defn ^:private simple-semver-set
  [version {:keys [major minor patch] :as parsed-semver}]
  (let [replace-str (str "$1" (or major 0) "." (or minor 0) "." (or patch 0) "$5")]
    (string/replace-first version
                          #"(\D*)(?:(\d+)\.?)?(?:(\d+)\.?)?(\d+)?(.*)"
                          replace-str)))

;; Utilities for calendar-based sprint numbering

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Info functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-file
  ([path]
   (-> path io/file slurp string/split-lines first))
  ([path regex]
   (let [source (-> path io/file slurp)
         matches (re-matches regex source)]
     (if matches
       (second matches)
       (throw (ex-info (str "regex " (.toString regex) " didn't yield results") {}))))))

(defn unstage
  []
  (let [version (slurp ".orzo-stage")]
    (io/delete-file ".orzo-stage")
    version))

(defn instant
  []
  (.toString (Instant/now)))

(defn env
  [var-name]
  (System/getenv var-name))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Transformer functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append
  [version value]
  (str version value))

(defn prepend
  [version value]
  (str value version))

(defn extract-base-semver
  [version]
  (let [{:keys [major minor patch] :as semver} (parse-semver version)]
    (str (or major 0) "."
         (or minor 0) "."
         (or patch 0))))

(defn set-semver
  [version {:keys [major minor patch ripple?] :as opts}]
  (if-not ripple?
    (simple-semver-set version {:major major :minor minor :patch patch})
    (let [semver (parse-semver version)
          new-semver (cond-> {}
                       (not= major (:major semver)) (assoc :major major
                                                           :minor 0
                                                           :patch 0)
                       (not= minor (:minor semver)) (assoc :major major
                                                           :minor minor
                                                           :patch 0)
                       (not= patch (:patch semver)) (assoc :major major
                                                           :minor minor
                                                           :patch patch))]
      (simple-semver-set version new-semver))))

(defn bump-semver
  [version indicator]
  (let [{:keys [major minor patch] :as semver} (parse-semver version)
        major' (if (= :major indicator) (if major (inc major) 0) major)
        minor' (if (= :minor indicator) (if minor (inc minor) 0) minor)
        patch' (if (= :patch indicator) (if patch (inc patch) 0) patch)]
    (set-semver version {:major major' :minor minor' :patch patch' :ripple? true})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistence functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-file
  ([version path]
   (spit (io/file path) version)
   version)
  ([version path template-path]
   (save-file version path template-path "VERSION"))
  ([version path template-path var-name]
   (let [regex (str "${" var-name "}")
         template (-> template-path io/file slurp)]
     (spit (io/file path)
           (string/replace template regex version))
     version)))

(let [r (str "$" "p")]
  (string/replace "person $p is here" r "tiago"))

(defn stage
  [version]
  (spit ".orzo-stage" version)
  version)
