(ns maltops.store
  "Store abstraction for malt-liquor (beer) and malt-manufacturing
  production batches. Current implementation operates on plain data
  (`{:batches {batch-id batch-map} :facts [...]}`); production should
  migrate this seam to Datomic/kotoba-server (the same seam point all
  cloud-itonami actors use) while keeping the same pure-function surface.

  A production batch is the minimal unit of work: one malting/mashing/
  fermentation/packaging run of a malt-liquor (beer) or malt product,
  tracked from grain intake through malting (for malt product batches),
  mashing, fermentation (for beer batches), packaging, and shipment.
  Representative batch keys:
    - :product-type keyword product id (see `maltops.facts/product-types`)
    - :jurisdiction keyword jurisdiction id (see `maltops.facts/jurisdictions`)
    - :abv-percent / :ibu / :diacetyl-ppb / :microbial-load-cfu-per-ml /
      :fill-volume-ml finished-product actuals
    - :extract-yield-percent finished-malt fine-grind extract yield
      (malt-product batches only)
    - :declared-label-abv-percent the ABV value printed on the finished
      product's label (beer batches only)
    - :fill-volume-variance-ml finished-product fill-quantity drift from
      the product's standard-of-fill target
    - :contamination-detected? true if packaging-line inspection or a
      wild-yeast/off-flavor screen flagged a concern
    - :sanitation-score 0-100 brewery/malthouse clean-in-place (CIP)
      hygiene score
    - :packaging-line-last-calibration-date epoch-ms of last fill-
      quantity metering equipment calibration
    - :evidence-checklist evidence items present for the batch
    - :safety-concern-raised? / :safety-concern-resolved? food-safety flag
    - :processed? true once a `:log-production-batch` proposal commits
    - :shipment-finalized? true once a `:coordinate-shipment` proposal commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:batches` in the same store value.")

(defn production-batch
  "Retrieve a batch by id, or nil if it does not exist / is not yet
  registered."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn batch-already-processed?
  "True only if the batch exists and has already been marked processed."
  [st batch-id]
  (true? (:processed? (production-batch st batch-id))))

(defn batch-shipment-finalized?
  "True only if the batch exists and its shipment has already been
  finalized."
  [st batch-id]
  (true? (:shipment-finalized? (production-batch st batch-id))))

(defn log-batch
  "Register/update `batch-data` under `batch-id` and mark it processed
  (one-way flag). Used once a `:log-production-batch` proposal commits."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] (assoc batch-data :processed? true)))

(defn finalize-shipment
  "Mark an existing batch's shipment as finalized (one-way flag). Used once
  a `:coordinate-shipment` proposal commits."
  [st batch-id]
  (assoc-in st [:batches batch-id :shipment-finalized?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))

;; ────────────────────────── Demo seed ──────────────────────────

(def demo-batch-ids
  "Deterministic display order for `demo-batches`. The store keeps batches
  in a map (unordered); anything that renders them -- the operator console
  in `maltops.render-html`, a future CLI -- iterates this vector instead of
  the map so its output is byte-stable across runs."
  ["batch-jp-0431" "batch-us-0912" "batch-eu-0357"
   "batch-malt-0088" "batch-jp-0522" "batch-us-0733"])

(def full-evidence
  "The evidence checklist every jurisdiction in `maltops.facts` currently
  requires (grain intake through fill-quantity check)."
  [:grain-intake-record :malting-log :mashing-log :fermentation-log
   :abv-test :ibu-test :diacetyl-test :microbial-test :fill-volume-check])

(defn demo-batches
  "Six independently-verified brewery/malthouse batch records, seeded as
  already-registered (a proposal may only ever be made against a batch the
  plant registered first -- see `maltops.governor`'s
  `:batch-not-registered` rule). Each carries exactly one intended hazard
  profile so a demo run reaches a distinct Governor verdict per batch:

    - `batch-jp-0431` lager, 日本(酒税法) -- fully clean; clears the whole
      lifecycle (maintenance -> batch logging -> shipment).
    - `batch-us-0912` ale, US TTB -- total-plate-count 180 CFU/mL against
      the style's 50 CFU/mL action level (wild-yeast/bacteria infection).
    - `batch-eu-0357` ale, EU -- actual ABV 5.6% printed on the label as
      5.0%, past the jurisdiction's 0.5% label-print tolerance.
    - `batch-malt-0088` base malt, US TTB -- 76.5% fine-grind extract
      yield against the 80.0% \"brewing-grade malt\" claim minimum.
    - `batch-jp-0522` stout, 日本 -- contamination detected on its own
      packaging-line screen, with the resulting food-safety flag still
      open.
    - `batch-us-0733` lager, US TTB -- CIP sanitation score 62 (floor 75)
      and a packaging-line fill-quantity meter last calibrated 120 days
      ago (recalibration interval 90 days).

  `now-epoch-ms` is supplied by the caller so this namespace stays free of
  host-clock calls (the same discipline `maltops.registry` keeps, and the
  reason `maltops.governor` isolates its clock read to one call site):
  a calibration date is only meaningful relative to the moment the
  Governor evaluates it, so these are expressed as offsets from the
  caller's clock rather than as fixed absolute dates that would silently
  age into `:packaging-line-calibration-overdue`."
  [now-epoch-ms]
  (let [days-ago (fn [n] (- now-epoch-ms (* n 24 60 60 1000)))]
    {"batch-jp-0431"
     {:product-type :beer/lager
      :jurisdiction :jp/nta
      :abv-percent 5.0
      :declared-label-abv-percent 5.0
      :ibu 18
      :diacetyl-ppb 12
      :microbial-load-cfu-per-ml 18
      :fill-volume-variance-ml 4
      :contamination-detected? false
      :packaging-line-last-calibration-date (days-ago 12)
      :sanitation-score 88
      :safety-concern-raised? false
      :evidence-checklist full-evidence}

     "batch-us-0912"
     {:product-type :beer/ale
      :jurisdiction :us/ttb
      :abv-percent 5.5
      :declared-label-abv-percent 5.5
      :ibu 34
      :diacetyl-ppb 40
      :microbial-load-cfu-per-ml 180
      :fill-volume-variance-ml 6
      :contamination-detected? false
      :packaging-line-last-calibration-date (days-ago 20)
      :sanitation-score 81
      :safety-concern-raised? false
      :evidence-checklist full-evidence}

     "batch-eu-0357"
     {:product-type :beer/ale
      :jurisdiction :eu/dg-taxud
      :abv-percent 5.6
      :declared-label-abv-percent 5.0
      :ibu 30
      :diacetyl-ppb 55
      :microbial-load-cfu-per-ml 22
      :fill-volume-variance-ml 7
      :contamination-detected? false
      :packaging-line-last-calibration-date (days-ago 30)
      :sanitation-score 90
      :safety-concern-raised? false
      :evidence-checklist full-evidence}

     "batch-malt-0088"
     {:product-type :malt/base-malt
      :jurisdiction :us/ttb
      :extract-yield-percent 76.5
      :microbial-load-cfu-per-ml 420
      :fill-volume-variance-ml 180
      :contamination-detected? false
      :packaging-line-last-calibration-date (days-ago 25)
      :sanitation-score 84
      :safety-concern-raised? false
      :evidence-checklist full-evidence}

     "batch-jp-0522"
     {:product-type :beer/stout
      :jurisdiction :jp/nta
      :abv-percent 6.0
      :declared-label-abv-percent 6.0
      :ibu 45
      :diacetyl-ppb 90
      :microbial-load-cfu-per-ml 35
      :fill-volume-variance-ml 5
      :contamination-detected? true
      :packaging-line-last-calibration-date (days-ago 15)
      :sanitation-score 79
      :safety-concern-raised? true
      :safety-concern-resolved? false
      :evidence-checklist full-evidence}

     "batch-us-0733"
     {:product-type :beer/lager
      :jurisdiction :us/ttb
      :abv-percent 5.0
      :declared-label-abv-percent 5.0
      :ibu 20
      :diacetyl-ppb 25
      :microbial-load-cfu-per-ml 30
      :fill-volume-variance-ml 5
      :contamination-detected? false
      :packaging-line-last-calibration-date (days-ago 120)
      :sanitation-score 62
      :safety-concern-raised? false
      :evidence-checklist full-evidence}}))

(defn demo-store
  "A fresh store seeded with `demo-batches` and an empty audit ledger."
  [now-epoch-ms]
  {:batches (demo-batches now-epoch-ms)
   :facts []})
