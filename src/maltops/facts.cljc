(ns maltops.facts
  "Reference facts for malt-liquor (beer) and malt manufacturing:
  product-style production parameters (ABV/IBU-bitterness/diacetyl-off-
  flavor/microbial-load/fill-quantity/extract-yield windows), jurisdiction
  ABV-label-accuracy and evidence-checklist requirements. This namespace
  contains pure lookup functions for regulatory/food-safety compliance
  checks -- the Governor calls these to independently validate proposals;
  the advisor's confidence is never sufficient on its own."
  (:require [clojure.set :as set]))

(def product-types
  "Valid malt-liquor (beer) and malt product categories and their safe
  production windows. `abv-target-percent`/`abv-tolerance-percent`
  follows the same excise/tax-classification-tolerance-band framing US
  TTB uses for malt beverages (27 CFR Part 25 / 27 CFR 7.71): crossing the
  tolerance band risks the batch being reclassified into a different
  excise-tax class, which this actor never decides (see
  `maltops.governor` op-allowlist). `ibu-min/max` is the finished-
  product bitterness (International Bitterness Units) window that defines
  the beer style. `diacetyl-max-ppb` is the maximum allowable buttery/
  butterscotch off-flavor compound residue (a fermentation byproduct that
  must be reduced via a diacetyl rest before packaging -- crossing it is a
  genuine quality/spoilage-adjacent defect). `microbial-load-max-cfu-per-
  ml` is the maximum allowable wild-yeast/bacteria total-plate-count in
  the finished, packaged product -- unfermented base malt (dry grain)
  carries a much higher baseline ceiling than packaged liquid beer because
  it has no alcohol/hop/pasteurization barrier and is not itself a
  microbiologically-stable finished beverage. `fill-volume-target/
  tolerance-ml` is the standard-of-fill packaging window; for the malt
  (dry-grain) product type this numeric field represents the packaged
  bagging-weight target/tolerance in gram-equivalent units, reusing the
  same field name so the Governor's standard-of-fill check stays a single
  unit-agnostic numeric comparison regardless of whether the underlying
  product is packaged by liquid volume (beer) or bulk weight (malt).
  `extract-yield-min-percent` is the minimum fine-grind extract yield
  (percent, per ASBC/EBC malt-analysis convention) a malting batch must
  meet to legally carry a \"brewing-grade malt\" claim -- finished beer
  styles carry no such minimum (0.0), since extract yield is a
  malt-quality-input spec, not a finished-beer output spec."
  {:beer/lager
   {:id :beer/lager
    :name "ラガービール(淡色, 下面発酵)"
    :abv-target-percent 5.0
    :abv-tolerance-percent 0.3
    :ibu-min 8
    :ibu-max 25
    :diacetyl-max-ppb 30
    :microbial-load-max-cfu-per-ml 50
    :fill-volume-target-ml 350
    :fill-volume-tolerance-ml 8
    :extract-yield-min-percent 0.0}

   :beer/ale
   {:id :beer/ale
    :name "エール(上面発酵)"
    :abv-target-percent 5.5
    :abv-tolerance-percent 0.3
    :ibu-min 20
    :ibu-max 45
    :diacetyl-max-ppb 100
    :microbial-load-max-cfu-per-ml 50
    :fill-volume-target-ml 500
    :fill-volume-tolerance-ml 10
    :extract-yield-min-percent 0.0}

   :beer/stout
   {:id :beer/stout
    :name "スタウト(濃色, 上面発酵)"
    :abv-target-percent 6.0
    :abv-tolerance-percent 0.3
    :ibu-min 30
    :ibu-max 60
    :diacetyl-max-ppb 120
    :microbial-load-max-cfu-per-ml 50
    :fill-volume-target-ml 330
    :fill-volume-tolerance-ml 8
    :extract-yield-min-percent 0.0}

   :malt/base-malt
   {:id :malt/base-malt
    :name "ベースモルト(製麦品, 未発酵)"
    :abv-target-percent 0.0
    :abv-tolerance-percent 0.0
    :ibu-min 0
    :ibu-max 0
    :diacetyl-max-ppb 0
    :microbial-load-max-cfu-per-ml 1000
    :fill-volume-target-ml 25000
    :fill-volume-tolerance-ml 250
    :extract-yield-min-percent 80.0}})

(defn product-type-by-id [id]
  (get product-types id))

(def jurisdictions
  "Malt-liquor/malt-manufacturing jurisdictions and their ABV-label-
  accuracy and evidence-checklist requirements. `abv-label-tolerance-
  percent` is the maximum allowed difference between a beer's LABEL-
  declared ABV and its actual measured ABV -- a genuine, real-world
  requirement (US TTB 27 CFR 7.71 permits a tolerance for the alcohol-
  content statement on malt-beverage labels) distinct from the per-style
  excise-tax-class tolerance band in `product-types` above: this one
  governs label print accuracy, not tax classification. Japan's beer/
  malt-liquor excise-tax and labeling authority is 国税庁 (National Tax
  Agency) under 酒税法 (the Liquor Tax Act), which classifies and taxes
  麦酒 (beer) and 発泡酒 (happoshu) partly by malt-ratio and ABV band --
  distinct from 食品表示法/厚生労働省, which governs general food labeling
  but not alcohol excise classification."
  {:jp/nta
   {:id :jp/nta
    :name "日本 (酒税法・国税庁)"
    :abv-label-tolerance-percent 0.3
    :required-evidence
    [:grain-intake-record
     :malting-log
     :mashing-log
     :fermentation-log
     :abv-test
     :ibu-test
     :diacetyl-test
     :microbial-test
     :fill-volume-check]}

   :us/ttb
   {:id :us/ttb
    :name "United States (TTB 27 CFR Part 25 / 27 CFR 7.71)"
    :abv-label-tolerance-percent 0.3
    :required-evidence
    [:grain-intake-record
     :malting-log
     :mashing-log
     :fermentation-log
     :abv-test
     :ibu-test
     :diacetyl-test
     :microbial-test
     :fill-volume-check]}

   :eu/dg-taxud
   {:id :eu/dg-taxud
    :name "European Union (Council Directive 92/83/EEC)"
    :abv-label-tolerance-percent 0.5
    :required-evidence
    [:grain-intake-record
     :malting-log
     :mashing-log
     :fermentation-log
     :abv-test
     :ibu-test
     :diacetyl-test
     :microbial-test
     :fill-volume-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn abv-label-within-tolerance?
  "Positive-sense convenience predicate: does the batch's LABEL-declared
  ABV stay within `jurisdiction`'s label-print-accuracy tolerance of the
  actual measured ABV? `jurisdiction` may be a resolved jurisdiction map
  or a raw jurisdiction id."
  [jurisdiction actual-abv-percent declared-label-abv-percent]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (boolean
     (and j actual-abv-percent declared-label-abv-percent
          (let [tol (:abv-label-tolerance-percent j)]
            (and (>= actual-abv-percent (- declared-label-abv-percent tol))
                 (<= actual-abv-percent (+ declared-label-abv-percent tol))))))))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn abv-in-tolerance?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s ABV tolerance window (inclusive) around its declared
  target? Crossing the window risks a federal/national excise-tax-class
  misclassification, which this actor never decides on its own -- it
  only proposes logging the observed value."
  [percent product]
  (boolean
   (and (some? product)
        (let [target (:abv-target-percent product)
              tol (:abv-tolerance-percent product)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(defn ibu-in-range?
  "Positive-sense convenience predicate: does `ibu` fall within
  `product`'s bitterness (International Bitterness Units) window
  (inclusive) -- the window that defines the beer's declared style?"
  [ibu product]
  (boolean
   (and (some? product)
        (>= ibu (:ibu-min product))
        (<= ibu (:ibu-max product)))))

(defn diacetyl-within-max?
  "Positive-sense convenience predicate: does `ppb` stay at or below
  `product`'s maximum allowable diacetyl (buttery/butterscotch off-
  flavor) residue?"
  [ppb product]
  (boolean
   (and (some? product)
        (<= ppb (:diacetyl-max-ppb product)))))

(defn microbial-load-within-max?
  "Positive-sense convenience predicate: does `cfu-per-ml` stay at or
  below `product`'s maximum allowable total-plate-count?"
  [cfu-per-ml product]
  (boolean
   (and (some? product)
        (<= cfu-per-ml (:microbial-load-max-cfu-per-ml product)))))

(defn fill-volume-in-range?
  "Positive-sense convenience predicate: does `ml` fall within `product`'s
  standard-of-fill window (target +/- tolerance, inclusive)?"
  [ml product]
  (boolean
   (and (some? product)
        (let [target (:fill-volume-target-ml product)
              tol (:fill-volume-tolerance-ml product)]
          (and (>= ml (- target tol))
               (<= ml (+ target tol)))))))

(defn extract-yield-meets-minimum?
  "Positive-sense convenience predicate: does `percent` meet or exceed
  `product`'s minimum required fine-grind extract yield for a legal
  \"brewing-grade malt\" label claim?"
  [percent product]
  (boolean
   (and (some? product)
        (>= percent (:extract-yield-min-percent product)))))
