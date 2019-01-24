(ns orzo.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))

;; Utilities

(defn ^:private ^:no-doc parse-number
  [n]
  (try
    (Integer/parseInt n)
    (catch java.lang.NumberFormatException e
      nil)))

(defn ^:private ^:no-doc parse-semver
  [semver]
  (let [matches (re-matches #"\D*(?:(\d+)\.?)?(?:(\d+)\.?)?(\d+)?.*" semver)]
    {:major (parse-number (nth matches 1))
     :minor (parse-number (nth matches 2))
     :patch (parse-number (nth matches 3))}))

(defn ^:private ^:no-doc greater-indicator
  [from-indicator]
  (case from-indicator
    :major nil
    :minor :major
    :patch :minor
    nil))

(defn ^:private ^:no-doc smaller-indicator
  [from-indicator]
  (case from-indicator
    :major :minor
    :minor :patch
    :patch nil
    nil))

(defn ^:private ^:no-doc simple-semver-set
  [version {:keys [major minor patch] :as parsed-semver}]
  (let [replace-str (str "$1" (or major 0) "." (or minor 0) "." (or patch 0) "$5")]
    (string/replace-first version
                          #"(\D*)(?:(\d+)\.?)?(?:(\d+)\.?)?(\d+)?(.*)"
                          replace-str)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Info functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-file
  "Reads a version (or any other information) from a file. If a path is
  provided, the first line of the file should contain the information
  you are want to read.

  If a path and a regex is provided, the whole file is read and the
  first group match of the regex is returned. If no match is found, an
  exception is thrown."
  ([path]
   (-> path io/file slurp string/split-lines first))
  ([path regex]
   (let [source (-> path io/file slurp)
         matches (re-matches regex source)]
     (if matches
       (second matches)
       (throw (ex-info (str "regex " (.toString regex) " didn't yield results") {}))))))

(defn unstage
  "Unstages a version that had been previously stage by the `stage` function."
  []
  (let [version (slurp ".orzo-stage")]
    (io/delete-file ".orzo-stage")
    version))

(defn instant
  "Returns a timestamp as a string."
  []
  (.toString (Instant/now)))

(defn env
  "Returns the value of the environment variable."
  [var-name]
  (System/getenv var-name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Transformer functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append
  "Appends the value to the version in the pipe."
  [version value]
  (str version value))

(defn prepend
  "Prepends the value to the version in the pipe."
  [version value]
  (str value version))

(defn extract-base-semver
  "Parses the incoming version as a pure semver (just a simple string
  of `<major>.<minor>.<patch>`)."
  [version]
  (let [{:keys [major minor patch] :as semver} (parse-semver version)]
    (str (or major 0) "."
         (or minor 0) "."
         (or patch 0))))

(defn set-semver
  "Allows direct manipulation of an incoming semver version.

  The second parameter is a map with the keys:

  * `major`: major number to set the semver
  * `minor`: minor number to set the semver
  * `patch`: patch number to set the semver
  * `ripple?`: (default false) if true, then any change on higher order
  indicator ripple through the lower order ones (i.e. change a `major`
  up would set `minor` and `patch` to `0` each)

  If a certain key is not passed, then that indicator is untouched.

  If the incoming version is not parsed as a semver,
  `extract-base-semver` will be called on it."
  [version {:keys [major minor patch ripple?] :as opts}]
  (let [semver (if (map? version) version (parse-semver version))]
    (if-not ripple?
      (simple-semver-set version
                         (merge semver (cond-> {}
                                         major (assoc :major major)
                                         minor (assoc :minor minor)
                                         patch (assoc :patch patch))))
      (let [new-semver (merge semver
                              (cond-> {}
                                (and major (not= major (:major semver))) (assoc :major major
                                                                                :minor 0
                                                                                :patch 0)
                                (and minor (not= minor (:minor semver))) (assoc :minor minor
                                                                                :patch 0)
                                (and patch (not= patch (:patch semver))) (assoc :patch patch)))]
        (simple-semver-set version new-semver)))))

(defn bump-semver
  "Bumps the specific indicator of an incoming semver. Options for
  indicator are `:major`, `:minor`, and `:patch`.

  Bumps do ripple through lower order indicators (i.e. `0.1.5` with a
  `:minor` bump would lead to `0.2.0`)"
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
  "Saves the incoming version from the pipeline to the file specified by
  the path.

  A template file can be specified as the third parameter. In such
  case, the string `${VERSION}` will be replaced with the incoming
  version.

  It is also possible to specify a placeholder name in case your
  template has a different format (i.e. you could specify \"SEMVER\"
  to have all the `${SEMVER}` replaced."
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

(defn stage
  "Stages the incoming version so that it can be used later."
  [version]
  (spit ".orzo-stage" version)
  version)
