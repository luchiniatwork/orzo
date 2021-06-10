(ns orzo.git
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-clean?
  "Throws if the repo is not clean."
  [version]
  (let [{:keys [exit err out]} (shell/sh "git" "status" "-s")]
    (when (not= 0 exit)
      (throw (ex-info "assert-clean? failed" {:anomaly/category :cmd-failure
                                              :reason err})))
    (when (not (empty? out))
      (throw (ex-info "git repo is not clean" {:anomaly/category :repo-not-clean
                                               :reason out})))
    version))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Info functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn last-tag
  "Returns the last tag from your repo. Many projects use tags to represent versions.

  It can also receive an optional git match pattern to be passed to
  git when looking for the tag.

  If a tag is not found, an exception is thrown."
  ([]
   (last-tag nil))
  ([match-pattern]
   (let [base-cmd ["git" "describe" "--tags" "--abbrev=0"]
         cmd (if match-pattern (conj base-cmd (str "--match=" match-pattern)) base-cmd)
         {:keys [exit err out]} (apply shell/sh cmd)]
     (when (not= 0 exit)
       (throw (ex-info "last-tag failed" {:reason err
                                          :cmd cmd})))
     out)))

(defn sha
  "Returns the current commit sha. By default it will return just the 7
  first characters. You can specify more if you need to."
  ([]
   (sha 7))
  ([length]
   (let [{:keys [exit err out]} (shell/sh "git" "rev-parse" (str "--short=" length) "HEAD")]
     (when (not= 0 exit)
       (throw (ex-info "sha failed" {:reason err})))
     (string/trim out))))

(defn count-since-last-tag
  "Returns the numeric cound of commits since the last tag. Some
  projects use that as a way to version.

  It can also receive an optional git match pattern to be passed to
  git when looking for the tag.

  If a tag is not found, an exception is thrown."
  ([]
   (count-since-last-tag nil))
  ([match-pattern]
   (let [last-tag (last-tag match-pattern)
         sha (sha)
         cmd ["git" "rev-list" "--count" (str last-tag ".." sha)]
         {:keys [exit err out]} (shell/sh cmd)]
     (when (not= 0 exit)
       (throw (ex-info "count-since-last-tag failed" {:reason err})))
     out)))

(defn unclean-status
  ([]
   (unclean-status "unclean"))
  ([micro-str]
   (try
     (assert-clean?)
     ""
     (catch Exception _
       micro-str))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Transformer functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistence functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tag
  "Creates a local tag with the incoming version."
  [version]
  (let [{:keys [exit err]} (shell/sh "git" "tag" version)]
    (when (not= 0 exit)
      (throw (ex-info "tag failed" {:reason err})))
    version))

(defn push-tag
  "Pushes whatever has been tagged or added to `origin` by default
  unless another remote has been specified."
  ([version]
   (push-tag version "origin"))
  ([version remote]
   (let [{:keys [exit err]} (shell/sh "git" "push" remote version)]
     (when (not= 0 exit)
       (throw (ex-info "push-tag failed" {:reason err})))
     version)))
