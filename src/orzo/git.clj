(ns orzo.git
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Info functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn last-tag
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
  ([]
   (sha 7))
  ([length]
   (let [{:keys [exit err out]} (shell/sh "git" "rev-parse" (str "--short=" length) "HEAD")]
     (when (not= 0 exit)
       (throw (ex-info "sha failed" {:reason err})))
     (string/trim out))))

(defn count-since-last-tag
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Transformer functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-clean?
  [version]
  (let [{:keys [exit err out]} (shell/sh "git" "status" "-s")]
    (when (not= 0 exit)
      (throw (ex-info "assert-clean? failed" {:reason err})))
    (when (not (empty? out))
      (throw (ex-info "git repo is not clean" {:reason out})))
    version))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistence functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tag
  [version]
  (let [{:keys [exit err]} (shell/sh "git" "tag" version)]
    (when (not= 0 exit)
      (throw (ex-info "tag failed" {:reason err})))
    version))

(defn push-tag
  ([version]
   (push-tag version "origin"))
  ([version remote]
   (let [{:keys [exit err]} (shell/sh "git" "push" remote version)]
     (when (not= 0 exit)
       (throw (ex-info "push-tag failed" {:reason err})))
     version)
   version))
