(ns maltops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [maltops.facts :as facts]))

;; ──────────────────────── Product Type Lookups ──────────────────────

(deftest product-type-by-id-test
  (testing "lager product type exists"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (some? p))
      (is (= (:id p) :beer/lager))
      (is (= (:abv-target-percent p) 5.0))
      (is (= (:ibu-max p) 25))))

  (testing "ale product type exists"
    (let [p (facts/product-type-by-id :beer/ale)]
      (is (some? p))
      (is (= (:abv-target-percent p) 5.5))
      (is (= (:ibu-max p) 45))))

  (testing "stout product type exists with the widest bitterness window"
    (let [p (facts/product-type-by-id :beer/stout)]
      (is (some? p))
      (is (= (:ibu-min p) 30))
      (is (= (:ibu-max p) 60))))

  (testing "base malt product type exists with an extract-yield minimum and no ABV"
    (let [p (facts/product-type-by-id :malt/base-malt)]
      (is (some? p))
      (is (= (:abv-target-percent p) 0.0))
      (is (= (:extract-yield-min-percent p) 80.0))))

  (testing "nonexistent product type returns nil"
    (is (nil? (facts/product-type-by-id :beer/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP NTA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/nta)]
      (is (some? j))
      (is (= (:abv-label-tolerance-percent j) 0.3))))

  (testing "US TTB jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/ttb)]
      (is (some? j))
      (is (= (:abv-label-tolerance-percent j) 0.3))))

  (testing "EU DG-TAXUD jurisdiction exists with a wider label tolerance"
    (let [j (facts/jurisdiction-by-id :eu/dg-taxud)]
      (is (some? j))
      (is (= (:abv-label-tolerance-percent j) 0.5))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── ABV Label Accuracy ──────────────────────

(deftest abv-label-within-tolerance-test
  (testing "exact match is within tolerance"
    (is (true? (facts/abv-label-within-tolerance? :us/ttb 5.0 5.0))))

  (testing "within tolerance passes"
    (is (true? (facts/abv-label-within-tolerance? :us/ttb 5.2 5.0))))

  (testing "outside tolerance fails"
    (is (false? (facts/abv-label-within-tolerance? :us/ttb 5.6 5.0))))

  (testing "accepts a resolved jurisdiction map"
    (let [j (facts/jurisdiction-by-id :jp/nta)]
      (is (true? (facts/abv-label-within-tolerance? j 5.0 5.0)))
      (is (false? (facts/abv-label-within-tolerance? j 6.0 5.0))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :us/ttb)
          evidence [:grain-intake-record :malting-log :mashing-log :fermentation-log
                    :abv-test :ibu-test :diacetyl-test :microbial-test :fill-volume-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :us/ttb)
          evidence [:grain-intake-record :malting-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))

;; ──────────────────────── Production Safety Predicates ──────────────────

(deftest abv-in-tolerance-test
  (testing "ABV at target passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/abv-in-tolerance? 5.0 p)))))

  (testing "ABV at upper tolerance boundary passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/abv-in-tolerance? 5.3 p)))))

  (testing "ABV below tolerance fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/abv-in-tolerance? 4.6 p)))))

  (testing "ABV above tolerance fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/abv-in-tolerance? 5.4 p))))))

(deftest ibu-in-range-test
  (testing "IBU within style window passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/ibu-in-range? 15 p)))))

  (testing "IBU below style window fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/ibu-in-range? 3 p)))))

  (testing "IBU above style window fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/ibu-in-range? 40 p))))))

(deftest diacetyl-within-max-test
  (testing "diacetyl at or below max passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/diacetyl-within-max? 30 p)))
      (is (true? (facts/diacetyl-within-max? 10 p)))))

  (testing "diacetyl above max fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/diacetyl-within-max? 45 p))))))

(deftest microbial-load-within-max-test
  (testing "microbial load at or below max passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/microbial-load-within-max? 50 p)))
      (is (true? (facts/microbial-load-within-max? 20 p)))))

  (testing "microbial load above max fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/microbial-load-within-max? 80 p))))))

(deftest fill-volume-in-range-test
  (testing "fill volume at target passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/fill-volume-in-range? 350 p)))))

  (testing "fill volume within tolerance passes"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (true? (facts/fill-volume-in-range? 355 p)))))

  (testing "fill volume outside tolerance fails"
    (let [p (facts/product-type-by-id :beer/lager)]
      (is (false? (facts/fill-volume-in-range? 330 p))))))

(deftest extract-yield-meets-minimum-test
  (testing "extract yield at minimum passes"
    (let [p (facts/product-type-by-id :malt/base-malt)]
      (is (true? (facts/extract-yield-meets-minimum? 80.0 p)))))

  (testing "extract yield above minimum passes"
    (let [p (facts/product-type-by-id :malt/base-malt)]
      (is (true? (facts/extract-yield-meets-minimum? 82.0 p)))))

  (testing "extract yield below minimum fails"
    (let [p (facts/product-type-by-id :malt/base-malt)]
      (is (false? (facts/extract-yield-meets-minimum? 75.0 p))))))
