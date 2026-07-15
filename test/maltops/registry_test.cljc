(ns maltops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [maltops.registry :as registry]))

;; ──────────────────────── ABV Tolerance ──────────────────────

(deftest abv-out-of-tolerance-test
  (testing "ABV at target with no tolerance returns false"
    (is (false? (registry/abv-out-of-tolerance? 5.0 5.0 0.3))))

  (testing "ABV within tolerance range returns false"
    (is (false? (registry/abv-out-of-tolerance? 5.2 5.0 0.3))))

  (testing "ABV below tolerance returns true (violation)"
    (is (true? (registry/abv-out-of-tolerance? 4.6 5.0 0.3))))

  (testing "ABV above tolerance returns true (violation)"
    (is (true? (registry/abv-out-of-tolerance? 5.4 5.0 0.3)))))

;; ──────────────────────── Bitterness (IBU) Range ──────────────────────

(deftest ibu-out-of-range-test
  (testing "IBU within range returns false (no violation)"
    (is (false? (registry/ibu-out-of-range? 15 8 25))))

  (testing "IBU below minimum returns true (violation)"
    (is (true? (registry/ibu-out-of-range? 3 8 25))))

  (testing "IBU above maximum returns true (violation)"
    (is (true? (registry/ibu-out-of-range? 40 8 25)))))

;; ──────────────────────── Diacetyl Off-Flavor ──────────────────────

(deftest diacetyl-exceeds-max-test
  (testing "diacetyl within max returns false (no violation)"
    (is (false? (registry/diacetyl-exceeds-max? 15 30))))

  (testing "diacetyl at max returns false"
    (is (false? (registry/diacetyl-exceeds-max? 30 30))))

  (testing "diacetyl exceeding max returns true (violation)"
    (is (true? (registry/diacetyl-exceeds-max? 45 30)))))

;; ──────────────────────── Microbial Load ──────────────────────

(deftest microbial-load-exceeds-max-test
  (testing "microbial load within limit returns false (no violation)"
    (is (false? (registry/microbial-load-exceeds-max? 20 50))))

  (testing "microbial load at limit returns false"
    (is (false? (registry/microbial-load-exceeds-max? 50 50))))

  (testing "microbial load exceeding limit returns true (violation)"
    (is (true? (registry/microbial-load-exceeds-max? 80 50)))))

;; ──────────────────────── Extract Yield ──────────────────────

(deftest extract-yield-below-minimum-test
  (testing "extract yield at minimum returns false (no violation)"
    (is (false? (registry/extract-yield-below-minimum? 80.0 80.0))))

  (testing "extract yield above minimum returns false"
    (is (false? (registry/extract-yield-below-minimum? 82.0 80.0))))

  (testing "extract yield below minimum returns true (violation)"
    (is (true? (registry/extract-yield-below-minimum? 75.0 80.0)))))

;; ──────────────────────── Packaging-Line Calibration ──────────────────────

(deftest packaging-line-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          thirty-days-ago (- now (* 30 24 60 60 1000))]
      (is (false? (registry/packaging-line-calibration-overdue? thirty-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/packaging-line-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Fill Volume Variance ──────────────────────

(deftest fill-volume-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/fill-volume-variance-excessive? 10 15))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/fill-volume-variance-excessive? 15 15))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/fill-volume-variance-excessive? 16 15)))))

;; ──────────────────────── ABV Label Accuracy ──────────────────────

(deftest abv-label-mismatch-test
  (testing "label matches actual exactly returns false (no risk)"
    (is (false? (registry/abv-label-mismatch? 5.0 5.0 0.3))))

  (testing "label within tolerance of actual returns false (no risk)"
    (is (false? (registry/abv-label-mismatch? 5.2 5.0 0.3))))

  (testing "label outside tolerance of actual returns true (risk)"
    (is (true? (registry/abv-label-mismatch? 5.6 5.0 0.3)))))

;; ──────────────────────── Contamination ──────────────────────

(deftest contamination-detected-test
  (testing "no detection returns false"
    (is (false? (registry/contamination-detected? false)))
    (is (false? (registry/contamination-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/contamination-detected? true)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
