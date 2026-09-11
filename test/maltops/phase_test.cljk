(ns maltops.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [maltops.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "malting is valid"
    (is (true? (phase/valid-phase? :malting))))

  (testing "fermentation is valid"
    (is (true? (phase/valid-phase? :fermentation))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> malting is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :malting))))

  (testing "intake -> mashing is valid (skip malting, e.g. pre-malted grain purchased)"
    (is (true? (phase/can-transition? :intake :mashing))))

  (testing "malting -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :malting :intake))))

  (testing "fermentation -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :fermentation :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :fermentation :fermentation))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :fermentation)))
    (is (false? (phase/can-transition? :fermentation :invalid)))))
