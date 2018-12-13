(ns orzo.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))

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

(defn instant
  []
  (.toString (Instant/now)))

(defn git-last-tag
  ([]
   (git-last-tag nil))
  ([match-pattern]
   (let [base-cmd ["git" "describe" "--tags" "--abbrev=0"]
         cmd (if match-pattern (conj base-cmd (str "--match=" match-pattern)) base-cmd)
         {:keys [exit err out]} (shell/sh cmd)]
     (when (not= 0 exit)
       (throw (ex-info "git-last-tag failed" {:reason err})))
     out)))

(defn unstage
  []
  (let [version (slurp ".orzo-stage")]
    (io/delete-file ".orzo-stage")
    version))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bump functions
;;
;; first parameter is always the transformed
;; version. The other parameters configure
;; this bumper
;; returns bumped up version
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-string
  ([version string-to-add]
   (add-string version string-to-add :suffix))
  ([version string-to-add placement]
   (case placement
     :suffix (str version string-to-add)
     :prefix (str string-to-add version))))

(defn add-env
  ([version var-name]
   (add-env version var-name :suffix))
  ([version var-name placement]
   (add-string version (System/getenv var-name) placement)))

(defn extract-base-semver
  [version]
  (let [{:keys [major minor patch] :as semver} (parse-semver version)]
    (str (or major 0) "."
         (or minor 0) "."
         (or patch 0))))

(defn bump-semver
  [version indicator]
  (let [{:keys [major minor patch] :as semver} (parse-semver version)
        replace-str
        (case indicator
          :major (str "$1" (if major (inc major) 0) "." (or minor 0) "." (or patch 0) "$5")
          :minor (str "$1" (or major 0) "." (if minor (inc minor) 0) "." (or patch 0) "$5")
          :patch (str "$1" (or major 0) "." (or minor 0) "." (if patch (inc patch) 0) "$5"))]
    (string/replace-first version
                          #"(\D*)(?:(\d+)\.?)?(?:(\d+)\.?)?(\d+)?(.*)"
                          replace-str)))


(defn ^:private get-sprint-date-pair
  [path]
  (let [matches (some-> path io/file slurp string/split-lines last (string/split #" "))
        sprint (first matches)
        end-date (last matches)]
    {:sprint (Integer/parseInt sprint)
     :end-date (LocalDate/parse end-date)}))

;; TODO: finish this
(defn sprint-calendar-based
  [version {:keys [start-date indicator sprint-size sprint-file-path]
            :or {indicator :minor sprint-size 2}}]
  (if-not sprint-file-path
    (throw (ex-info "sprint-file-path must be provided" {})))
  (let [{:keys [sprint end-date]} (get-sprint-date-pair sprint-file-path)
        now (LocalDate/now)
        weeks (-> ChronoUnit/WEEKS (.between end-date now))
        sprints (+ (inc (int (/ weeks sprint-size)))
                   sprint)]
    sprints))

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
      (add-string "")
      (stage))

  (-> (unstage)
      #_(git-tag)
      #_(git-push-tag)
      (save-file "version.txt"))

  (read-file (io/resource "crazy-file.json")
             #"(?s)^.*\"version\"\s*:\s*\"([^\"]*).*$")


  (sprint-calendar-based "3.4.5" {:sprint-file-path (io/resource "sprint-file")})


  (get-sprint-date-pair (io/resource "sprint-file"))
  
  
  )
