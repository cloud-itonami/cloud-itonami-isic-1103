# cloud-itonami-isic-1103: Malt Liquor and Malt Manufacturing Coordination Actor

**ISIC Rev. 4 1103** — Manufacture of malt liquors and malt

A distributed actor for autonomous, compliant coordination of malt-liquor
(beer) brewery and malthouse operations: grain intake → malting
(steeping/germination/kilning, for malt-product batches) → mashing →
fermentation → packaging → ABV/IBU-bitterness/diacetyl/microbial-load/
extract-yield/fill-quantity inspection → ABV labeling → finished-product
logistics. Sealed LLM advisor; independent Governor enforcement;
append-only audit ledger. **Not equipment control.** Mash-tun, lauter-tun,
fermentation-tank, and packaging-line equipment operation remain
exclusive to licensed brewery/malthouse staff, and excise/tax-
classification decisions (e.g. reclassifying a batch's national/federal
excise-tax category) remain exclusive to human tax authorities.

## Scope

This actor coordinates **plant-operations workflow** for malt-liquor
(beer) and malt manufacturing (lager, ale, stout, and base malting-grade
malt):
- Production batch logging (grain intake, malting/mashing/fermentation/
  packaging parameters, ABV, IBU, evidence checklist)
- Equipment maintenance scheduling (mash tuns, lauter tuns, fermentation
  tanks, packaging lines, fill-quantity meters)
- Food-safety concern escalation (wild-yeast/bacteria contamination,
  ABV-labeling mismatch, excess diacetyl off-flavor)
- Finished-product shipment coordination

**Out of scope:**
- Direct mashing/fermentation/packaging-line equipment control (brewery/
  malthouse staff exclusive)
- Excise/tax-classification authority (human tax authority only — this
  actor never reclassifies a batch's excise-tax category, it only logs
  observed values and, when warranted, raises a flag)
- Regulatory interpretation (proposals cite jurisdiction specifications;
  the Governor enforces only published requirements)
- Any actual sale/distribution transaction (this actor performs
  coordination and compliance-logging only, never executes a sale)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the
advisor's confidence for anything safety- or compliance-relevant, and it
always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes
    any proposal that would touch mashing/fermentation/packaging-line
    control or excise/tax-classification-authority decisions
  - Proposal asserting an `:effect` other than `:propose`
    (`:effect-not-propose`)
  - Brewery/malthouse batch record not independently verified/registered
    before any proposal is made against it (`:batch-not-registered`) —
    applies to every proposal op, not only shipment coordination
  - No jurisdiction citation (`:no-spec-basis`) — can't verify
    requirements without one
  - Evidence checklist incomplete (`:evidence-incomplete`)
  - ABV outside the declared product's tolerance band
    (`:abv-out-of-tolerance`) — crossing the band risks an excise/tax-
    classification misclassification
  - Bitterness (IBU) outside the declared style's window
    (`:ibu-out-of-range`)
  - Diacetyl (buttery/butterscotch off-flavor) residue exceeds the
    product's quality ceiling (`:diacetyl-exceeds-max`)
  - Microbial load (total-plate-count) exceeds the product's regulatory
    action level (`:microbial-load-exceeded`)
  - Extract yield below the minimum for a "brewing-grade malt" label
    claim (`:extract-yield-below-minimum`)
  - Contamination detected on the batch's own inspection — foreign
    material / wild-yeast infection / off-flavor marker
    (`:contamination-detected`)
  - Packaging-line fill-quantity metering calibration overdue
    (`:packaging-line-calibration-overdue`)
  - Finished-product fill-quantity variance excessive
    (`:fill-volume-variance-excessive`) — standard-of-fill
  - ABV-label mismatch — label-declared ABV inaccurate against the
    actual measured value (`:abv-label-mismatch`)
  - Brewery/malthouse clean-in-place (CIP) sanitation score insufficient
    (`:sanitation-score-insufficient`)
  - Unresolved food-safety flag (`:food-safety-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit
    guards)
- **Escalate** (human sign-off always required):
  - `:log-production-batch` / `:coordinate-shipment` — real actuation
    events, always require brewery/malthouse-operator sign-off even when
    the Governor is otherwise clean
  - `:flag-food-safety-concern` — a food-safety concern (wild-yeast
    contamination, ABV-labeling mismatch, excess diacetyl) is never
    auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a
  mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current
    allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four
operation types, all `:effect :propose`:

- **`:log-production-batch`** — Log grain intake → malting → mashing →
  fermentation → packaging batch (ABV, IBU, extract yield) into
  production records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for mash
  tuns/lauter tuns/fermentation tanks/packaging lines/fill-quantity
  meters (routine, low risk)
- **`:flag-food-safety-concern`** — Surface a food-safety or compliance
  concern (e.g. wild-yeast/bacteria contamination, ABV-labeling
  mismatch); always escalates
- **`:coordinate-shipment`** — Coordinate outbound shipment of finished
  product (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly
anything that would amount to direct mashing/fermentation/packaging-line
control, or an excise/tax-classification-authority decision — is refused
unconditionally by the Governor (`:op-not-allowed`), regardless of
advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone
(not in the kotoba-lang monorepo), override `:local/root` paths in
`deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see
`SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated
industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
