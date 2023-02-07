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
    (binding [core/*now-provider* #(LocalDate/of 2004 5 2)]
      (is (= "2004" (core/calver "YYYY")))
      (is (= "4" (core/calver "YY")))
      (is (= "04" (core/calver "0Y")))))
  
  (testing "should accept month formats"
    (binding [core/*now-provider* #(LocalDate/of 2005 8 23)]
      (is (= "8" (core/calver "MM")))
      (is (= "08" (core/calver "0M")))))
  
  (testing "should accept day formats"
    (binding [core/*now-provider* #(LocalDate/of 2009 9 3)]
      (is (= "3" (core/calver "DD")))
      (is (= "03" (core/calver "0D")))))

  (testing "should accept week formats"
    (binding [core/*now-provider* #(LocalDate/of 2015 1 29)]
      (is (= "5" (core/calver "WW")))
      (is (= "05" (core/calver "0W")))))

  (testing "default format"
    (binding [core/*now-provider* #(LocalDate/of 2025 2 3)]
      (is (= "25.2.3" (core/calver))) ;; default = "YY.MM.DD"
      (is (= "2025.02.03" (core/calver "YYYY.0M.0D")))))
  
  (testing "should accept complex formats"
    (binding [core/*now-provider* #(LocalDate/of 2025 2 3)]
      (are [format calver]
          (= calver (core/calver format))
        "YY.MM.DD" "25.2.3"
        "YYYY.MM.DD" "2025.2.3"
        "YY.0M.0D" "25.02.03"
        "YY.0M" "25.02"
        "YY.DD" "25.3"
        "YY.WW.DD" "25.6.3"
        "YY.0W.0D" "25.06.03"
        "YYYY.WW" "2025.6"
        "YYYY.0W.0M-foobar/DD" "2025.06.02-foobar/3")))

  (testing "should take counter into account"
    (binding [core/*now-provider* #(LocalDate/of 2014 5 6)]
      (are [current format target]
          (= target (core/calver current format))
        "14.5.123" "YY.MM.CC" "14.5.124"
        "14.4.123" "YY.MM.CC" "14.5.0"
        "13.5.178" "YY.MM.CC" "14.5.0"

        "14.6.123" "YY.DD.CC" "14.6.124"
        "14.3.123" "YY.DD.CC" "14.6.0"
        "13.6.178" "YY.DD.CC" "14.6.0"

        "14.19.123" "YY.WW.CC" "14.19.124"
        "14.1.123" "YY.WW.CC" "14.19.0"
        "13.6.178" "YY.WW.CC" "14.19.0"))))
