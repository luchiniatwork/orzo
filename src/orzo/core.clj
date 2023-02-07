(ns orzo.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as s]
            [clojure.string :as string])
  (:import (java.time Instant
                      LocalDate)
           (java.time.temporal WeekFields)))

(defn ^:dynamic *now-provider* [] (LocalDate/now))

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

(def ^:private calver-patterns
  [[#"YYYY" (fn [_ intent] (format "%04d" (.getYear intent)))]
   [#"YY"   (fn [_ intent] (-> (.getYear intent)
                               str (subs 2 4)
                               Integer/parseInt
                               str))]
   [#"0Y"   (fn [_ intent] (format "%02d" (-> intent .getYear
                                              str (subs 2 4)
                                              Integer/parseInt)))]
   [#"MM"   (fn [_ intent] (->> intent .getMonthValue str))]
   [#"0M"   (fn [_ intent] (->> intent .getMonthValue (format "%02d")))]
   [#"DD"   (fn [_ intent] (->> intent .getDayOfMonth str))]
   [#"0D"   (fn [_ intent] (->> intent .getDayOfMonth (format "%02d")))]
   [#"WW"   (fn [_ intent] (str (.get intent (.weekOfWeekBasedYear WeekFields/ISO))))]
   [#"0W"   (fn [_ intent] (format "%02d" (.get intent (.weekOfWeekBasedYear WeekFields/ISO))))]
   [#"CC"   (fn [{:keys [date components]} intent]
              (let [{:keys [year month day week counter]} components]
                (if (and (or (nil? year)
                             (= year (.getYear intent)))
                         (or (nil? month)
                             (= month (.getMonthValue intent)))
                         (or (nil? week)
                             (= week (.get intent (.weekOfWeekBasedYear WeekFields/ISO))))
                         (or (nil? day)
                             (= day (.getDayOfMonth intent))))
                  (if (nil? counter)
                    "0"
                    (str (inc counter)))
                  "0")))]])

(def ^:private calver-map
  [[#"YYYY" :year]
   [#"YY"   :year]
   [#"0Y"   :year]
   [#"MM"   :month]
   [#"0M"   :month]
   [#"DD"   :day]
   [#"0D"   :day]
   [#"WW"   :week]
   [#"0W"   :week]
   [#"CC"   :counter]])


(def ^:private calver-transform-map
  {:day #(Integer/parseInt %)
   :month #(Integer/parseInt %)
   :year #(let [r (Integer/parseInt %)]
            (if (< r 1000) (+ 2000 r) r))
   :week #(Integer/parseInt %)
   :counter #(Integer/parseInt %)})


(defn ^:private format-to-pattern [format]
  (->> calver-map
       (reduce (fn [a [pattern field]]
                 (s/replace a pattern (str "(?<" (name field)  ">\\\\d+)")))
               format)
       re-pattern))


(defn ^:private calver-to-component [calver format]
  (let [matcher (re-matcher (format-to-pattern format) (or calver ""))]
    (re-find matcher)
    (reduce (fn [m [_ field]]
              (try
                (let [v (.group matcher (name field))
                      xfn (get calver-transform-map field)]
                  (assoc m field (xfn v)))
                (catch Throwable _
                  m)))
            {} calver-map)))


(defn ^:private parse-calver [calver format]
  (let [{:keys [year month day week] :as components} (calver-to-component calver format)
        c (java.util.Calendar/getInstance)]
    {:components components
     :date (cond-> c
             year
             (#(do (.set % java.util.Calendar/YEAR year) %))
             week
             (#(do (.set % java.util.Calendar/WEEK_OF_YEAR week) %))
             month
             (#(do (.set % java.util.Calendar/MONTH (dec month)) %))
             day
             (#(do (.set % java.util.Calendar/DAY_OF_MONTH day) %))
             :always
             (#(LocalDate/ofInstant (.toInstant %) (.toZoneId (.getTimeZone %)))))}))


(def ^:private default-calver "YY.MM.DD")


(defn calver
  "Returns a calver (https://calver.org) based on `LocalDate` and
  following the format specified in `format` below:

  - YYYY - Full year - 2006, 2016, 2106
  - YY - Short year - 6, 16, 106
  - 0Y - Zero-padded year - 06, 16, 106
  - MM - Short month - 1, 2 ... 11, 12
  - 0M - Zero-padded month - 01, 02 ... 11, 12
  - WW - Short week (since start of year) - 1, 2, 33, 52
  - 0W - Zero-padded week - 01, 02, 33, 52
  - DD - Short day - 1, 2 ... 30, 31
  - 0D - Zero-padded day - 01, 02 ... 30, 31
  - CC - Cumulative counter - counter is a special case. See below.

  Uses the ISO-8601 definition, where a week starts on Monday and the
  first week of the year has a minimum of 4 days.

  The default format string is `YY.MM.DD`.

  Calling this function without args uses the now LocalDate and the
  default format above.

  Calling this function with one arg will trigger a check whether the
  arg is a format string (and then uses it as such) or whether the arg is
  a current calver fed to this calver calculation (see below the special
  counter case.)

  Lastly, the two-arg signature takes the current calver followed by the
  format string.

  The `CC` marker on the format is a special case. A common pattern when
  using calver is to bump a counter when the other temporal fields match.
  The idea is that if you use `YY.MM.CC` and have a calver `23.2.5` it
  means it was the 6th (0-based) version on month 2, year 2023. Calling
  `calver` with a `CC` marker in the format and a current version with a
  compatible counter will take care of this case."
  ([]
   (calver default-calver))

  ([current-or-format]
   (if (some (fn [[pattern _]]
               (not= current-or-format
                     (s/replace current-or-format pattern "")))
             calver-patterns)
     (calver nil current-or-format)
     (calver current-or-format default-calver)))
  
  ([current format]
   (let [intention-date (*now-provider*)
         current' (parse-calver current format)]
     (reduce (fn [a [pattern xfn]]
               (s/replace a pattern (xfn current' intention-date)))
             format calver-patterns))))

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

(defn overwrite-file
  "Utilized in cases where there are files that need to be changed with
  the new version (i.e. README.md, package.json, etc).

  The key parameters are the file path and the regex to be used to
  find the placement of the version (the implementation uses
  clojure.string/replace).

  A common regex for a traditional semver-like version would look like
  `#\"\\d+.\\d+.\\d+\"`. In some cases, you might need to include other
  markers such as double quotes around the version (with a regex like
  `#\"\"\\d+.\\d+.\\d+\"\"` for instance.)

  For more complex scenarios, you can provide a builder function to
  the regex argument. It is called with the version and expects a
  regex in return.

  The last optional parameter is a function that is called with the
  intended version and is expected to return the string to be inserted
  in the match. This tackles the cases where more characters are
  needed in the match (for instance, the surrounding double quotes
  described above.)"
  ([version path regex-or-builder]
   (overwrite-file version path regex-or-builder identity))
  ([version path regex-or-builder treat-fn]
   (let [regex (if (fn? regex-or-builder) (regex-or-builder version) regex-or-builder)
         content (-> path io/file slurp)]
     (spit (io/file path)
           (s/replace content regex (treat-fn version)))
     version)))

(defn stage
  "Stages the incoming version so that it can be used later."
  [version]
  (spit ".orzo-stage" version)
  version)
