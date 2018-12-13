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

;; Utilities for sprint-based

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
;; Seed functions
;;
;; may accept parameters for setup
;; all return a "seeding" version
;; a "seeding" is a starting point
;; (i.e. current version, last known
;; version, etc)
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

(defn git-last-tag
  ([]
   (git-last-tag nil))
  ([match-pattern]
   (let [base-cmd ["git" "describe" "--tags" "--abbrev=0"]
         cmd (if match-pattern (conj base-cmd (str "--match=" match-pattern)) base-cmd)
         {:keys [exit err out]} (apply shell/sh cmd)]
     (when (not= 0 exit)
       (throw (ex-info "git-last-tag failed" {:reason err
                                              :cmd cmd})))
     out)))

(defn unstage
  []
  (let [version (slurp ".orzo-stage")]
    (io/delete-file ".orzo-stage")
    version))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Meta functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instant
  []
  (.toString (Instant/now)))

(defn env
  [var-name]
  (System/getenv var-name))

(defn git-sha
  ([]
   (git-sha 7))
  ([length]
   (let [{:keys [exit err out]} (shell/sh "git" "rev-parse" (str "--short=" length) "HEAD")]
     (when (not= 0 exit)
       (throw (ex-info "git-sha failed" {:reason err})))
     (string/trim out))))

(defn git-count-since-last-tag
  ([]
   (git-count-since-last-tag nil))
  ([match-pattern]
   (let [last-tag (git-last-tag match-pattern)
         sha (git-sha)
         cmd ["git" "rev-list" "--count" (str last-tag ".." sha)]
         {:keys [exit err out]} (shell/sh cmd)]
     (when (not= 0 exit)
       (throw (ex-info "git-count-since-last-tag failed" {:reason err})))
     out)))

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
;;
;; first parameter is always the transformed
;; version. The other parameters configure
;; this bumper
;; returns bumped up version
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

(defn assert-git-clean?
  [version]
  (let [{:keys [exit err out]} (shell/sh "git" "status" "-s")]
    (when (not= 0 exit)
      (throw (ex-info "assert-git-clean? failed" {:reason err})))
    (when (not (empty? out))
      (throw (ex-info "git repo is not clean" {:reason out})))
    version))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistence functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-file
  ([version path]
   (spit (io/file path) version)
   version)
  ([version path template-path]
   (let [template (-> template-path io/file slurp)]
     (spit (io/file path)
           (string/replace template #"\$\{VERSION\}" version))
     version)))

(defn git-tag
  [version]
  (let [{:keys [exit err]} (shell/sh "git" "tag" version)]
    (when (not= 0 exit)
      (throw (ex-info "git-tag failed" {:reason err})))
    version))

(defn git-push-tag
  ([version]
   (git-push-tag version "origin"))
  ([version remote]
   (let [{:keys [exit err]} (shell/sh "git" "push" remote version)]
     (when (not= 0 exit)
       (throw (ex-info "git-push-tag failed" {:reason err})))
     version)
   version))

(defn stage
  [version]
  (spit ".orzo-stage" version)
  version)



(comment
  (-> (read-file (io/resource "base-version.txt"))
      (extract-base-semver)
      (append "-SNAPSHOT")
      (append (str "-g" (git-sha)))
      (prepend "v")
      (stage))


  (-> (unstage)
      #_(git-tag)
      #_(git-push-tag)
      (save-file "version.txt"))

  (read-file (io/resource "crazy-file.json")
             #"(?s)^.*\"version\"\s*:\s*\"([^\"]*).*$")


  (sprint-calendar-based "3.4.5" {:indicator :minor
                                  :sprint-file-path (io/resource "sprint-file")})


  (calendar-sprint-number {:sprint-file-path (io/resource "sprint-file")})

  (get-sprint-date-pair (io/resource "sprint-file"))
  
  
  )
