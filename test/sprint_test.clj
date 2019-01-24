(ns sprint-test
  (:require [clojure.test :refer :all]
            [orzo.sprint :as sprint])
  (:import (clojure.lang ExceptionInfo)
           (java.time Instant
                      LocalDate)
           (java.time.temporal ChronoUnit)))

(deftest should-throw-on-no-sprint-file
  (is (thrown? ExceptionInfo
               (sprint/calendar-sprint-number nil))))

(deftest should-return-correct-sprint-number-default-two-weeks
  (let [base-sprint 7
        end-date (LocalDate/parse "2019-01-01")
        now (LocalDate/now)
        days (-> ChronoUnit/DAYS (.between end-date now))]
    (if (.isAfter now end-date)
      (is (= (+ (max (int (Math/ceil (/ (/ days 7) 2))) 1)
                base-sprint)
             (sprint/calendar-sprint-number {:sprint-file-path "test/sprint-file"}))))))

(deftest should-return-correct-sprint-number-one-week-sprints
  (let [base-sprint 7
        end-date (LocalDate/parse "2019-01-01")
        now (LocalDate/now)
        days (-> ChronoUnit/DAYS (.between end-date now))]
    (if (.isAfter now end-date)
      (is (= (+ (max (int (Math/ceil (/ (/ days 7) 1))) 1)
                base-sprint)
             (sprint/calendar-sprint-number {:sprint-file-path "test/sprint-file"
                                             :sprint-size 1}))))))
