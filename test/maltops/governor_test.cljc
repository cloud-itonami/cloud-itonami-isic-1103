(ns maltops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [maltops.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private hundred-days-ago (- now-ms (* 100 24 60 60 1000)))

(def ^:private clean-batch
  {:product-type :beer/lager
   :jurisdiction :us/ttb
   :abv-percent 5.0
   :ibu 15
   :diacetyl-ppb 15
   :microbial-load-cfu-per-ml 20
   :declared-label-abv-percent 5.0
   :fill-volume-variance-ml 5
   :contamination-detected? false
   :packaging-line-last-calibration-date ten-days-ago
   :sanitation-score 85
   :evidence-checklist [:grain-intake-record :malting-log :mashing-log :fermentation-log
                        :abv-test :ibu-test :diacetyl-test :microbial-test :fill-volume-check]})

(def ^:private clean-malt-batch
  {:product-type :malt/base-malt
   :jurisdiction :us/ttb
   :extract-yield-percent 82.0
   :microbial-load-cfu-per-ml 500
   :fill-volume-variance-ml 50
   :contamination-detected? false
   :packaging-line-last-calibration-date ten-days-ago
   :sanitation-score 85
   :evidence-checklist [:grain-intake-record :malting-log :mashing-log :fermentation-log
                        :abv-test :ibu-test :diacetyl-test :microbial-test :fill-volume-check]})

;; ──────────────────────── Batch Registration (generalized) ──────────────────────

(deftest batch-not-registered-violation-test
  (testing "log-production-batch against an unregistered batch is a hard violation"
    (let [req {:op :log-production-batch :subject "batch-ghost"}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :batch-not-registered) (:violations result)))))

  (testing "schedule-maintenance against an unregistered batch is also a hard violation"
    (let [req {:op :schedule-maintenance :subject "batch-ghost"}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :batch-not-registered) (:violations result)))))

  (testing "a registered batch does not trigger this rule"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :batch-not-registered) (:violations result)))))))

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── ABV Tolerance Violations ──────────────────────

(deftest abv-violation-test
  (testing "batch with ABV out of tolerance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :abv-percent 6.5)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :abv-out-of-tolerance) (:violations result)))))

  (testing "batch with ABV in tolerance passes"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "stout has a much higher ABV target than lager, so lager-level ABV is out of tolerance for it"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :product-type :beer/stout
                                            :abv-percent 5.0
                                            :ibu 40
                                            :diacetyl-ppb 60)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :abv-out-of-tolerance) (:violations result))))))

;; ──────────────────────── Bitterness (IBU) Range Violations ──────────────────────

(deftest ibu-violation-test
  (testing "batch with IBU out of the declared style's window triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :ibu 50)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "Brewhouse-Manual"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :ibu-out-of-range) (:violations result))))))

;; ──────────────────────── Diacetyl Off-Flavor Violations ──────────────────────

(deftest diacetyl-violation-test
  (testing "batch with diacetyl residue exceeding the product's limit triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :diacetyl-ppb 45)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "Brewhouse-Manual"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :diacetyl-exceeds-max) (:violations result))))))

;; ──────────────────────── Microbial Load Violations ──────────────────────

(deftest microbial-load-violation-test
  (testing "batch with microbial load exceeding the product's limit triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :microbial-load-cfu-per-ml 80)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :microbial-load-exceeded) (:violations result)))))

  (testing "the same microbial load that is fine for base malt exceeds packaged lager's stricter limit"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-malt-batch :microbial-load-cfu-per-ml 80)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))
    (let [batch-id "batch-003"
          store {:batches {batch-id (assoc clean-batch :microbial-load-cfu-per-ml 80)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :microbial-load-exceeded) (:violations result))))))

;; ──────────────────────── Extract Yield Violations ──────────────────────

(deftest extract-yield-violation-test
  (testing "malting batch below the labeling minimum extract yield triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-malt-batch :extract-yield-percent 70.0)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ASBC-Malt-Analysis"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :extract-yield-below-minimum) (:violations result)))))

  (testing "malting batch meeting the minimum extract yield does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id clean-malt-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ASBC-Malt-Analysis"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :extract-yield-below-minimum) (:violations result)))))))

;; ──────────────────────── Contamination Violations ──────────────────────

(deftest contamination-violation-test
  (testing "batch with detected contamination triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :contamination-detected? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :contamination-detected) (:violations result))))))

;; ──────────────────────── Packaging-Line Calibration Violations ──────────────────────

(deftest packaging-line-calibration-violation-test
  (testing "batch with overdue packaging-line calibration triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :packaging-line-last-calibration-date hundred-days-ago)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :packaging-line-calibration-overdue) (:violations result))))))

;; ──────────────────────── Fill Volume Variance Violations ──────────────────────

(deftest fill-volume-variance-violation-test
  (testing "batch with excessive fill-quantity variance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :fill-volume-variance-ml 25)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :fill-volume-variance-excessive) (:violations result))))))

;; ──────────────────────── ABV Label Mismatch Violations ──────────────────────

(deftest abv-label-mismatch-violation-test
  (testing "label ABV mismatched against actual ABV beyond jurisdiction tolerance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :declared-label-abv-percent 5.8)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :abv-label-mismatch) (:violations result)))))

  (testing "label ABV within jurisdiction tolerance of actual ABV passes"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch :declared-label-abv-percent 5.2)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR 7.71"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :abv-label-mismatch) (:violations result)))))))

;; ──────────────────────── Sanitation Score Violations ──────────────────────

(deftest sanitation-score-violation-test
  (testing "batch with insufficient sanitation score triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sanitation-score 60)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sanitation-score-insufficient) (:violations result))))))

;; ──────────────────────── Food-Safety Flag Violations ──────────────────────

(deftest food-safety-flag-unresolved-violation-test
  (testing "batch with an unresolved food-safety flag triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? false)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))

  (testing "batch with a resolved food-safety flag does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "Equipment-Manual"}] :value {:jurisdiction :us/ttb} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-production-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Flag Food-Safety Concern Always Escalates ──────────────────────

(deftest flag-food-safety-concern-escalation-test
  (testing "flag-food-safety-concern escalates even when Governor checks are clean and confidence is high"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :flag-food-safety-concern :subject batch-id}
          prop {:cites [{:spec "Brewery-HACCP-Plan"}] :value {:jurisdiction :us/ttb} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Already Processed Violation ──────────────────────

(deftest already-processed-violation-test
  (testing "batch already processed triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :beer/lager
                            :processed? true}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-processed) (:violations result))))))

;; ──────────────────────── Already Shipment Finalized Violation ──────────────────────

(deftest already-shipment-finalized-violation-test
  (testing "batch shipment already finalized triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :shipment-finalized? true)}}
          req {:op :coordinate-shipment :subject batch-id}
          prop {:cites [{:spec "Shipment-Manual"}] :value {:jurisdiction :us/ttb} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-shipment-finalized) (:violations result))))))

;; ──────────────────────── Op-Not-Allowed (Closed Allowlist) ──────────────────────

(deftest op-not-allowed-violation-test
  (testing "direct fermentation-tank control is a hard, permanent block"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :actuate-fermentation-tank :subject batch-id}
          prop {:cites [{:spec "Brewhouse-Manual"}] :value {:jurisdiction :us/ttb} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :op-not-allowed) (:violations result)))))

  (testing "excise-tax-classification authority is a hard, permanent block"
    (let [batch-id "batch-002"
          store {:batches {batch-id clean-batch}}
          req {:op :reclassify-excise-tax-class :subject batch-id}
          prop {:cites [{:spec "27 CFR Part 25"}] :value {:jurisdiction :us/ttb} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :op-not-allowed) (:violations result))))))

;; ──────────────────────── Effect Not Propose ──────────────────────

(deftest effect-not-propose-violation-test
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "Equipment-Manual"}] :value {:jurisdiction :us/ttb} :effect :commit :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :effect-not-propose) (:violations result))))))
