# SYNTRA Ecosystem — Development Roadmap

> Document status: Reviewed roadmap  
> Version: 1.1.0  
> Last updated: 2026-11-26

This roadmap defines shared ecosystem-level work. Product-specific engineering remains in each product repository. This is a planning document, not a promise of delivery dates.

## 1. Roadmap principles

- Keep SYNTRA OS as the primary product-development focus.
- Maintain accurate status across all active projects.
- Treat prototype, engineering foundation, operational infrastructure, and released product as distinct maturity levels.
- Syntra Files is discontinued; do not schedule new development, testing, or upgrades for it unless the Founder explicitly reopens the project.
- Prefer small, reviewable changes over large unverified updates.
- Do not announce features, compatibility, uptime, or releases before they are verified.
- Preserve historical repositories and records unless deletion is separately authorized.

## 2. Phase overview

| Phase | Focus | Status |
|---|---|---|
| 1 | Repository foundation and project identity | Baseline established; ongoing maintenance |
| 2 | Core ecosystem documentation and standards | In progress |
| 3 | Product directory and status tracking | In progress; catalogue refreshed 2026-11-26 |
| 4 | Website and product discovery | Website v2.15 maintained; content consistency review ongoing |
| 5 | Quality assurance and release process | In progress |
| 6 | Optional ecosystem integrations | Proposed; evaluate only when foundations are stable |

## 3. Shared ecosystem priorities

1. Keep this repository's README, product directory, roadmap, dashboard, and version tracker synchronized.
2. Link each active project to its canonical repository and authoritative documentation.
3. Record evidence separately for CI, device QA, production deployment, and user-visible functionality.
4. Keep public website status and download information aligned with verified releases.
5. Maintain security, privacy, and quality standards across products.
6. Preserve Syntra Files as historical material only; do not include it in active work queues.

## 4. Product priorities and boundaries

| Project | Current direction | Important boundary |
|---|---|---|
| SYNTRA OS | Primary development focus; continue OS foundations and verification | Early Development; no production release claim |
| SYNTRA VPN | Continue Android client, device authentication, Control Plane, and gateway integration | No production VPN claim until end-to-end gateway tests pass |
| SYNTRA VM | Continue Phase 1 Billing Foundation and architecture | No real billing or VM provisioning claim until implemented and tested |
| SYNTRA SOCIAL | Continue prototype and end-to-end testing | Not public-launch or production-ready |
| SYNTRA NEXUS | Continue consent-based local-first prototype | Do not claim production transport or remote-control features |
| SYNTRA Website | Maintain public information, changelog, docs, support, and downloads | Publish only verified project states and artifacts |
| Discord Bridge / AUTO-BOT | Maintain bot reliability and accurate persistent project knowledge | Verify live logs and actual Discord interactions; deployment SUCCESS alone is insufficient |
| SYNTRA Ecosystem / GROUP | Maintain shared governance and documentation | Central docs do not replace product repository evidence |
| Syntra Files | Historical retention only | Discontinued on 2026-11-26; no active development or QA |

## 5. Quality and release process

- [ ] Define a repeatable evidence record for each active product milestone.
- [ ] Keep CI results tied to exact commit hashes.
- [ ] Record device/runtime tests separately from CI.
- [ ] Verify security and privacy requirements before release.
- [ ] Verify artifact links and integrity before publishing downloads.
- [ ] Review public website status after every meaningful product lifecycle change.
- [ ] Ensure discontinued products are not accidentally reintroduced by stale documentation.

## 6. Progress reporting

Use these labels consistently:
- **Not started** — work has not begun.
- **In progress** — implementation or review is underway.
- **Blocked** — progress depends on an unresolved issue or decision.
- **Complete** — exit criteria have been checked and met.
- **Deferred** — intentionally postponed.
- **Discontinued** — project development has ended.

A checked task means the repository task was completed; it does not automatically mean a product feature has passed device testing or is production-ready.

---

**Current state:** The ecosystem catalogue has been refreshed. SYNTRA OS remains the primary development focus; VPN, VM, SOCIAL, and NEXUS remain at prototype/engineering stages; Website and Discord Bridge are maintained operational components; Syntra Files is discontinued.
