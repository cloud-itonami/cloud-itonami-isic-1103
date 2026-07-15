(ns maltops.registry
  "Pure validation functions for malt-liquor (beer) and malt-manufacturing
  production parameters. These are called by the Governor to independently
  verify physical/operational constraints -- the advisor's confidence is
  NOT sufficient to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `packaging-line-calibration-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `maltops.governor`).")

(defn abv-out-of-tolerance?
  "Independently verify that the batch's actual ABV falls within
  tolerance of the product's declared target. Sits outside the tolerance
  band and the batch risks an excise/tax-classification misclassification
  -- a decision this actor never makes on its own; it only proposes
  logging the observed value so a human can act."
  [actual-percent target-percent tolerance-percent]
  (or (< actual-percent (- target-percent tolerance-percent))
      (> actual-percent (+ target-percent tolerance-percent))))

(defn ibu-out-of-range?
  "Independently verify that the batch's actual bitterness (IBU) falls
  within the product's declared-style window. Outside the window
  indicates the finished beer no longer matches its declared style -- a
  style/label misclassification with real consumer-facing consequences."
  [actual-ibu min-ibu max-ibu]
  (or (< actual-ibu min-ibu)
      (> actual-ibu max-ibu)))

(defn diacetyl-exceeds-max?
  "Independently verify that the batch's diacetyl (buttery/butterscotch
  off-flavor) residue does not exceed the product's maximum allowable
  level."
  [actual-ppb max-ppb]
  (> actual-ppb max-ppb))

(defn microbial-load-exceeds-max?
  "Independently verify that the batch's actual total-plate-count
  (CFU/mL) does not exceed the product's maximum allowable level. Wild-
  yeast/bacteria infection above the regulatory/product action level is
  one of the most serious food-safety hazards specific to malt-liquor
  production -- a hard, un-overridable stop."
  [actual-cfu-per-ml max-cfu-per-ml]
  (> actual-cfu-per-ml max-cfu-per-ml))

(defn extract-yield-below-minimum?
  "Independently verify that a malting batch's actual fine-grind extract
  yield does not fall below the product's minimum required level for a
  legal \"brewing-grade malt\" label claim."
  [actual-percent min-percent]
  (< actual-percent min-percent))

(defn packaging-line-calibration-overdue?
  "Independently verify that the bottling/kegging/canning/bagging-line
  fill-quantity metering equipment was calibrated within the last 90
  days. `last-calibration-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 90 24 60 60 1000)))

(defn fill-volume-variance-excessive?
  "Independently verify that a batch's finished-product fill-quantity
  variance (drift from the product's standard-of-fill target, in the
  product's native packaging unit) does not exceed the maximum
  tolerance. Excessive variance indicates the packaging line is out of
  calibration or the standard-of-fill was not met."
  [actual-variance max-variance]
  (> actual-variance max-variance))

(defn abv-label-mismatch?
  "True when the batch's LABEL-declared ABV falls outside the
  jurisdiction's label-print-accuracy tolerance of the actual measured
  ABV (mislabeling risk -- a genuine excise-tax and consumer-protection
  hazard). Distinct from `abv-out-of-tolerance?` above, which checks the
  actual value against the product STYLE's excise-tax-class band, not
  against the batch's own printed label."
  [actual-abv-percent declared-label-abv-percent tolerance-percent]
  (and (some? actual-abv-percent) (some? declared-label-abv-percent)
       (or (< actual-abv-percent (- declared-label-abv-percent tolerance-percent))
           (> actual-abv-percent (+ declared-label-abv-percent tolerance-percent)))))

(defn contamination-detected?
  "Independently verify a batch's contamination-detection result (foreign
  material -- glass/metal fragments from the packaging line -- or a
  positive off-flavor/spoilage-marker screen, e.g. wild-yeast infection).
  Any detection is a genuine physical/quality hazard -- this predicate
  simply coerces the raw fact to a boolean so the Governor's check
  functions stay uniform in shape with every other independently-
  verified physical constraint in this namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn sanitation-score-insufficient?
  "Independently verify that the brewery/malthouse's clean-in-place (CIP)
  sanitation score meets the minimum required. Score is 0-100, assessed
  by a third-party auditor against food-safety sanitation standards (a
  significant concern specific to preventing wild-yeast/bacteria
  contamination during mashing, fermentation, and packaging)."
  [actual-score min-score-required]
  (< actual-score min-score-required))
