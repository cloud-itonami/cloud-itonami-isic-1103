(ns maltops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: there was previously no
  demo page and no generator at all (`maltops.sim`, the repo's
  `clojure -M:dev:run` entry point, is still the stub it shipped as --
  confirmed by running it before writing this file: it prints
  \"not yet implemented\" and touches none of the actor code, so there was
  no existing scenario to reuse and none of its ids could be trusted).

  Every id, measurement, verdict, disposition and hold reason on the page
  is produced by actually executing this repo's real actor stack at build
  time --

      maltops.store/demo-store   (seed a fresh store)
        -> maltops.operation/run-operation
             -> maltops.governor/check      (the independent compliance layer)
                  -> maltops.facts / maltops.registry
        -> maltops.store/log-batch / finalize-shipment / append-fact

  -- and read back out of the resulting store and its append-only audit
  ledger. Nothing on the page is hand-typed HTML data: the Japanese
  violation sentences with their embedded numbers are the Governor's own
  `:detail` strings, and the action-gate table is derived from the live
  `governor/allowed-ops` / `high-stakes` / `always-escalate-ops` /
  `confidence-floor` vars rather than transcribed from the README.

  This repo does NOT depend on langgraph (its `deps.edn` `:deps` is
  empty; the `:dev` alias only carries `:override-deps` entries that
  resolve to nothing), so -- unlike the fleet repos that ship a
  StateGraph -- the real entry point driven here is
  `maltops.operation/run-operation`, this actor's own single-proposal
  driver, called once per advisor proposal with the real
  `maltops.governor/check` as its governor-fn and the real
  `maltops.governor/hold-fact` as its hold-fact-fn.

  The ONE thing the scenario supplies rather than derives is the human
  operator's sign-off on the two escalated actuation ops -- which is the
  point of an escalation: `operation/run-operation` deliberately refuses
  to commit them on its own, so a human decision has to come from
  outside the actor. Every HARD hold below never reaches that human.

  DETERMINISTIC: no timestamp, elapsed time or random value appears in
  the page. Batch calibration dates are seeded as day-offsets from the
  build clock (so they never silently age into an overdue verdict), but
  the raw epoch values are never rendered -- only the Governor's verdict
  about them is. Two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [maltops.facts :as facts]
            [maltops.governor :as governor]
            [maltops.operation :as operation]
            [maltops.store :as store]))

;; ----------------------------- the actor -----------------------------

(def ^:private actor-context
  "The context `maltops.operation/run-operation` threads through: who is
  proposing, and which fact constructor records a refusal."
  {:actor-id "maltops-op-1"
   :hold-fact-fn governor/hold-fact})

(def ^:private operator
  "The brewery/malthouse operator whose sign-off the Governor demands for
  every actuation op. Not part of the actor -- escalation exists
  precisely because this decision comes from outside it."
  "brewmaster-1")

(defn- proposal
  "An advisor proposal in the shape the Governor validates: an official
  citation, the jurisdiction it is claimed under, `:effect :propose` (this
  actor never claims write authority for itself) and a confidence."
  [spec jurisdiction confidence]
  {:cites [{:spec spec}]
   :value {:jurisdiction jurisdiction}
   :effect :propose
   :confidence confidence})

(defn- approve-log
  "The store mutation a signed-off `:log-production-batch` authorises."
  [subject]
  (fn [st] (store/log-batch st subject (store/production-batch st subject))))

(defn- approve-shipment
  "The store mutation a signed-off `:coordinate-shipment` authorises."
  [subject]
  (fn [st] (store/finalize-shipment st subject)))

(defn- step!
  "Drive ONE advisor proposal through the real actor stack and fold the
  outcome into the store.

  `on-approve` is the store mutation the operator authorises IF the
  Governor escalates; pass `identity` for an escalating op with no store
  effect, and omit it to leave the escalation open (awaiting sign-off)
  exactly as the Governor left it. A HARD hold never consults it -- that
  is what makes the hold hard."
  ([st op subject prop] (step! st op subject prop nil))
  ([st op subject prop on-approve]
   (let [request {:op op :subject subject}
         {:keys [ok? facts verdict]}
         (operation/run-operation request actor-context prop st governor/check)
         st' (reduce store/append-fact st facts)]
     (cond
       ;; Governor clean, not a mandatory-escalation op -> commits itself.
       ok?
       (store/append-fact st' {:t :committed
                               :op op
                               :actor (:actor-id actor-context)
                               :subject subject
                               :disposition :auto-commit
                               :basis []
                               :confidence (:confidence prop)})

       ;; Soft gate only (high stakes, or confidence under the floor):
       ;; run-operation already wrote the hold; a human may release it.
       (and (:escalate? verdict) on-approve)
       (-> st'
           (store/append-fact {:t :approval-granted
                               :op op
                               :actor (:actor-id actor-context)
                               :subject subject
                               :disposition :committed
                               :approved-by operator
                               :basis []
                               :confidence (:confidence verdict)})
           on-approve)

       ;; HARD hold, or an escalation left open. Nothing else happens.
       :else st'))))

(defn run-demo!
  "Seed a fresh store and run one scenario through the REAL actor,
  reaching every disposition this actor can produce.

  `batch-jp-0431` (clean lager, 酒税法) clears a FULL lifecycle:
  maintenance scheduling auto-commits (the only routine op in the
  allowlist), then batch logging and shipment coordination each escalate
  -- both are real-world actuation, never auto at any confidence -- and
  are signed off by the operator, after which the store's double-commit
  guards HARD-hold the repeat attempts (`:already-processed`,
  `:already-shipment-finalized`). A direct fermentation-tank actuation
  against the same batch is HARD-held as `:op-not-allowed`: equipment
  control is outside this actor's closed allowlist entirely.

  Five further batches each reach a distinct HARD hold that never sees a
  human: `batch-us-0912` total-plate-count over the style's action level
  (and, separately, an uncited food-safety flag -> `:no-spec-basis`);
  `batch-eu-0357` label-declared ABV past the jurisdiction's print
  tolerance (its low-confidence maintenance proposal is left escalated
  and unsigned); `batch-malt-0088` extract yield under the
  brewing-grade-malt claim minimum; `batch-jp-0522` contamination
  detected with its food-safety flag still open (its properly-cited
  food-safety flag DOES escalate and is signed off first);
  `batch-us-0733` an overdue packaging-line meter calibration plus a
  failed CIP sanitation score -- while its maintenance proposal
  auto-commits, which is exactly the response a failed CIP score wants.
  An `:effect :commit` proposal and a proposal against an unregistered
  batch close out the two remaining invariants.

  Returns the resulting store; every field `render` reads is real
  Governor/store output."
  [now-epoch-ms]
  (-> (store/demo-store now-epoch-ms)

      ;; ---- batch-jp-0431: full clean lifecycle -----------------------
      (step! :schedule-maintenance "batch-jp-0431"
             (proposal "Brewhouse-Equipment-Manual" :jp/nta 0.94))
      (step! :log-production-batch "batch-jp-0431"
             (proposal "酒税法 (国税庁)" :jp/nta 0.93)
             (approve-log "batch-jp-0431"))
      (step! :coordinate-shipment "batch-jp-0431"
             (proposal "酒税法 (国税庁)" :jp/nta 0.90)
             (approve-shipment "batch-jp-0431"))
      ;; double-commit guards
      (step! :log-production-batch "batch-jp-0431"
             (proposal "酒税法 (国税庁)" :jp/nta 0.93))
      (step! :coordinate-shipment "batch-jp-0431"
             (proposal "酒税法 (国税庁)" :jp/nta 0.90))
      ;; outside the closed allowlist: equipment control is never ours
      (step! :actuate-fermentation-tank "batch-jp-0431"
             (proposal "Brewhouse-Equipment-Manual" :jp/nta 0.99))

      ;; ---- batch-us-0912: microbial load over the action level -------
      (step! :log-production-batch "batch-us-0912"
             (proposal "27 CFR Part 25" :us/ttb 0.88))
      ;; ... and an attempt to flag it with nothing cited
      (step! :flag-food-safety-concern "batch-us-0912"
             {:cites [] :value {:jurisdiction :us/ttb}
              :effect :propose :confidence 0.86})

      ;; ---- batch-eu-0357: label ABV past the print tolerance ---------
      (step! :log-production-batch "batch-eu-0357"
             (proposal "Council Directive 92/83/EEC" :eu/dg-taxud 0.91))
      ;; low confidence -> escalates, and is deliberately left unsigned
      (step! :schedule-maintenance "batch-eu-0357"
             (proposal "Brewhouse-Equipment-Manual" :eu/dg-taxud 0.42))

      ;; ---- batch-malt-0088: extract yield under the claim minimum ----
      (step! :log-production-batch "batch-malt-0088"
             (proposal "27 CFR Part 25" :us/ttb 0.89))

      ;; ---- batch-jp-0522: contamination + open food-safety flag ------
      (step! :flag-food-safety-concern "batch-jp-0522"
             (proposal "Brewery-HACCP-Plan" :jp/nta 0.93)
             identity)
      (step! :log-production-batch "batch-jp-0522"
             (proposal "酒税法 (国税庁)" :jp/nta 0.93))

      ;; ---- batch-us-0733: overdue calibration + failed CIP score -----
      (step! :schedule-maintenance "batch-us-0733"
             (proposal "Packaging-Line-Meter-Calibration-Procedure" :us/ttb 0.88))
      (step! :log-production-batch "batch-us-0733"
             (proposal "27 CFR Part 25" :us/ttb 0.87))
      ;; a proposal claiming write authority for the actor itself
      (step! :schedule-maintenance "batch-us-0733"
             {:cites [{:spec "Brewhouse-Equipment-Manual"}]
              :value {:jurisdiction :us/ttb}
              :effect :commit :confidence 0.92})

      ;; ---- a batch the plant never registered ------------------------
      (step! :coordinate-shipment "batch-ghost-0001"
             (proposal "27 CFR Part 25" :us/ttb 0.90))))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- hard-hold? [{:keys [t basis]}]
  (and (= :governor-hold t) (seq basis)))

(defn- escalation? [{:keys [t basis]}]
  (and (= :governor-hold t) (empty? basis)))

(defn- rule-list [basis]
  (str/join ", " (map #(str "<code>" (esc (name %)) "</code>") basis)))

(defn- last-fact-for [ledger batch-id]
  (last (filter #(= (:subject %) batch-id) ledger)))

(defn- status-cell [ledger batch-id]
  (let [f (last-fact-for ledger batch-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (hard-hold? f) (str "<span class=\"critical\">HARD 保留 &middot; "
                          (rule-list (:basis f)) "</span>")
      (escalation? f) "<span class=\"warn\">人間承認待ち</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">人間承認 &rarr; 確定</span>"
      (= :committed (:t f)) "<span class=\"ok\">自動確定</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [b]
  (cond
    (:shipment-finalized? b) "<span class=\"ok\">登録済 &rarr; 出荷確定</span>"
    (:processed? b) "<span class=\"warn\">登録済 (出荷未確定)</span>"
    :else "<span class=\"muted\">未登録 (提案段階)</span>"))

(defn- measurement-cell
  "The batch's own measured values next to the product spec the Governor
  checks them against -- both read live out of the store and
  `maltops.facts`, never transcribed."
  [b]
  (let [p (facts/product-type-by-id (:product-type b))
        parts (cond-> []
                (:abv-percent b)
                (conj (str "ABV " (:abv-percent b) "% / 規格 "
                           (:abv-target-percent p) "±" (:abv-tolerance-percent p) "%"))
                (:declared-label-abv-percent b)
                (conj (str "ラベル表示 ABV " (:declared-label-abv-percent b) "%"))
                (:ibu b)
                (conj (str "IBU " (:ibu b) " / 規格 " (:ibu-min p) "-" (:ibu-max p)))
                (:diacetyl-ppb b)
                (conj (str "ダイアセチル " (:diacetyl-ppb b) " / 上限 "
                           (:diacetyl-max-ppb p) " ppb"))
                (:microbial-load-cfu-per-ml b)
                (conj (str "生菌数 " (:microbial-load-cfu-per-ml b) " / 上限 "
                           (:microbial-load-max-cfu-per-ml p) " CFU/mL"))
                (:extract-yield-percent b)
                (conj (str "エキス収率 " (:extract-yield-percent b) "% / 最低 "
                           (:extract-yield-min-percent p) "%"))
                (:fill-volume-variance-ml b)
                (conj (str "充填量分散 " (:fill-volume-variance-ml b) " / 許容 "
                           (:fill-volume-tolerance-ml p)))
                (:sanitation-score b)
                (conj (str "CIP 衛生スコア " (:sanitation-score b))))]
    (str/join "<br>" (map esc parts))))

(defn- batch-row [ledger b-id b]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc b-id)
          (esc (:name (facts/product-type-by-id (:product-type b))))
          (esc (:name (facts/jurisdiction-by-id (:jurisdiction b))))
          (measurement-cell b)
          (lifecycle-cell b)
          (status-cell ledger b-id)))

(defn- gate-cell
  "Read straight off the Governor's own vars, so this table cannot drift
  away from the code that enforces it."
  [op]
  (cond
    (contains? governor/high-stakes op)
    "<span class=\"warn\">常に人間承認 &middot; 実世界アクチュエーション &middot; どの信頼度でも自動確定しない</span>"

    (contains? governor/always-escalate-ops op)
    "<span class=\"warn\">常に人間承認 &middot; 助言者の信頼度だけでは自動解決しない</span>"

    :else
    (str "<span class=\"ok\">Governor が clean かつ信頼度 &ge; "
         (esc governor/confidence-floor) " なら自動確定</span>")))

(defn- gate-row [op]
  (format "        <tr><td><code>:%s</code></td><td>%s</td></tr>"
          (esc (name op)) (gate-cell op)))

(defn- hold-row [{:keys [subject op violations]}]
  (format "        <tr><td><code>%s</code></td><td><code>:%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc subject) (esc (name op))
          (str/join "<br>" (map #(str "<code>" (esc (name (:rule %))) "</code>") violations))
          (str/join "<br>" (map #(esc (:detail %)) violations))))

(defn- disposition-cell [f]
  (cond
    (hard-hold? f) "<span class=\"critical\">HARD 保留 (人間に届かない)</span>"
    (escalation? f) "<span class=\"warn\">人間へエスカレーション</span>"
    (= :approval-granted (:t f)) (str "<span class=\"ok\">"
                                      (esc (:approved-by f)) " が承認 &rarr; 確定</span>")
    (= :committed (:t f)) "<span class=\"ok\">自動確定</span>"
    :else "<span class=\"muted\">&mdash;</span>"))

(defn- ledger-row [{:keys [t op subject basis confidence] :as f}]
  (format "        <tr><td>%s</td><td><code>:%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td class=\"num\">%s</td></tr>"
          (esc (name t)) (esc (name op)) (esc subject)
          (disposition-cell f)
          (if (seq basis) (rule-list basis) "<span class=\"muted\">&mdash;</span>")
          (esc confidence)))

(defn render
  "Render the operator console from a store that has already been run
  through `run-demo!` (or any other real scenario)."
  [st]
  (let [ledger (vec (store/audit-trail st))
        batch-rows (str/join "\n"
                             (for [b-id store/demo-batch-ids
                                   :let [b (store/production-batch st b-id)]
                                   :when b]
                               (batch-row ledger b-id b)))
        gate-rows (str/join "\n" (map gate-row (sort-by name governor/allowed-ops)))
        hold-rows (str/join "\n" (map hold-row (filter hard-hold? ledger)))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        n-hard (count (filter hard-hold? ledger))
        n-esc (count (filter escalation? ledger))
        n-appr (count (filter #(= :approval-granted (:t %)) ledger))
        n-auto (count (filter #(= :committed (:t %)) ledger))]
    (str
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-1103 &middot; malt liquors and malt</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Manufacture of malt liquors and malt (ISIC 1103) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · バッチ登録と出荷確定は常に人間承認</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>生産バッチ</h2>\n"
     "    <p class=\"muted\">Demo snapshot — <code>maltops.store/demo-store</code> を種にして "
     "<code>maltops.operation/run-operation</code> → <code>maltops.governor/check</code> を実際に実行し、"
     "その結果の store と監査台帳から <code>maltops.render-html</code> がビルド時に生成"
     "（<code>clojure -M:dev:render-html</code>）。実測値・規格・判定はすべて実行結果で、手書きの値は無い。</p>\n"
     "    <table>\n"
     "      <thead><tr><th>バッチ</th><th>製品</th><th>法域</th><th>実測値 / 規格</th><th>ライフサイクル</th><th>最終処理</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>アクションゲート (MaltOps Governor)</h2>\n"
     "    <p class=\"muted\">この表は <code>maltops.governor</code> の "
     "<code>allowed-ops</code> / <code>high-stakes</code> / <code>always-escalate-ops</code> / "
     "<code>confidence-floor</code> をそのまま読んで組み立てている。閉じた許可リストの外 —— "
     "仕込み・発酵・包装ラインの機器制御や酒税の税区分判定 —— は信頼度に関わらず無条件で拒否される。</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>ゲート</th></tr></thead>\n"
     "      <tbody>\n"
     gate-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD 保留（この実行で人間に届かなかった提案）</h2>\n"
     "    <p class=\"muted\">HARD 保留は上書きできない。以下の理由文は Governor が返した "
     "<code>:violations</code> の <code>:detail</code> そのもので、数値は store の実測値と "
     "<code>maltops.facts</code> の製品規格から Governor 自身が組み立てたもの。</p>\n"
     "    <table>\n"
     "      <thead><tr><th>バッチ</th><th>Op</th><th>規則</th><th>Governor の判定理由</th></tr></thead>\n"
     "      <tbody>\n"
     hold-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>監査台帳（この実行）</h2>\n"
     "    <p class=\"muted\">追記のみの決定事実ログ。<code>maltops.operation/run-operation</code> は "
     "HARD 保留とエスカレーションを同じ <code>governor/hold-fact</code> に通すため、両者は "
     "<code>:basis</code>（HARD は規則名を持ち、エスカレーションは空）で区別する。</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>バッチ</th><th>処理</th><th>Basis</th><th>信頼度</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>この実行の内訳: 監査事実 " (count ledger)
     " 件 / HARD 保留 " n-hard
     " 件 / 人間へのエスカレーション " n-esc
     " 件（うち承認 " n-appr " 件）/ 自動確定 " n-auto " 件。</p>\n"
     "  <p>Generated by <code>maltops.render-html</code> from a real actor run — "
     "no timestamps, no randomness, byte-identical across reruns.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        st (run-demo! (System/currentTimeMillis))
        ledger (store/audit-trail st)
        html (render st)]
    (io/make-parents out)
    (spit out html)
    (println "wrote" out "(" (count ledger) "ledger facts,"
             (count (filter hard-hold? ledger)) "HARD holds,"
             (count (filter escalation? ledger)) "escalations )")))
