(ns core-test
  (:require [clojure.test :refer :all]
            [orzo.core :as core])
  (:import (java.time LocalDate)))

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

(deftest calver
  (testing "should accept year formats"
    (let [date (LocalDate/of 2004 5 6)]
      (is (= "2004" (core/calver date "YYYY")))
      (is (= "4" (core/calver date "YY")))
      (is (= "04" (core/calver date "0Y")))))
  
  (testing "should accept month formats"
    (let [date (LocalDate/of 2033 8 12)]
      (is (= "8" (core/calver date "MM")))
      (is (= "08" (core/calver date "0M")))))
  
  (testing "should accept day formats"
    (let [date (LocalDate/of 2530 12 3)]
      (is (= "3" (core/calver date "DD")))
      (is (= "03" (core/calver date "0D")))))

  (testing "should accept week formats"
    (let [date (LocalDate/of 2012 1 20)]
      (is (= "3" (core/calver date "WW")))
      (is (= "03" (core/calver date "0W")))))

  (testing "should accept week formats"
    (are [args calver]
        (let [[y m d fstr] args]
          (= calver (core/calver (LocalDate/of y m d) fstr)))
      [2012 1 25 "YY.MM.DD"] "12.1.25"
      [2002 12 3 "YY.MM.DD"] "2.12.3"
      [2021 6 10 "YYYY.WW"] "2021.23"
      [2045 1 1 "YYYY.0W.0M-MM/DD"] "2045.52.01-1/1"))

  (testing "should have reasonable defaults"
    (is (= 10 (count (core/calver))))
    (is (= 5 (count (core/calver "0Y/0W"))))
    (is (= (.getYear (LocalDate/now)) (Integer/parseInt (core/calver "YYYY"))))))
