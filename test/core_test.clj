(ns core-test
  (:require [clojure.test :refer :all]
            [orzo.core :as core]))

(deftest should-normalize-semver
  (is (= "3.5.123"
         (core/extract-base-semver "v3.5.123"))))

(deftest should-set-semver-no-riple
  ;; all indicators
  (is (= "1.3.5"
         (core/set-semver "0.0.0"
                          {:major 1
                           :minor 3
                           :patch 5})))
  ;; partial indicators
  (is (= "1.3.5"
         (core/set-semver "1.3.0"
                          {:patch 5}))))

(deftest should-set-semver-riple
  (is (= "2.0.0"
         (core/set-semver "1.5.23"
                          {:major 2
                           :ripple? true})))
  (is (= "1.5.0"
         (core/set-semver "1.3.43"
                          {:minor 5
                           :ripple? true}))))
