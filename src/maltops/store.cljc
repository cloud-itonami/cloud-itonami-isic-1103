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
