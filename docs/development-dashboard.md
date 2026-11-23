# SYNTRA Development Dashboard

**Dashboard version:** 1.1.0  
**Last reviewed:** 2026-11-23  
**Scope:** Repository-level progress tracking. Product runtime status is not automatically measured here.

## Overall status

| Area | Status | Notes |
|---|---|---|
| Ecosystem documentation | 🟢 Baseline established | Core documentation is present; continue review as projects evolve. |
| GitHub issue templates | 🟢 Added | Bug report and feature request forms are in the repository. |
| Pull request template | 🟢 Added | Includes scope, testing, risk, and security prompts. |
| Repository checks | 🟢 Passed | [GitHub Actions run #45154179133](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/actions/runs/45154179133) passed on 2026-11-23; required files and local Markdown links passed. |
| Issue labels | 🟡 Planned | Label taxonomy is documented; labels themselves have not been created. |
| GitHub Projects board | 🟡 Planned | Board structure is documented; no board has been created. |
| SYNTRA OS | 🟡 CI passed; hardware QA pending | Host Debug/Release tests and ARM64/QEMU checks passed on the recorded main revision; no physical-device validation is recorded. |
| Syntra Files | 🟡 Debug APK CI passed; device QA pending | v0.69.0 Debug APK CI passed historically. Source is now v0.71.0 (versionCode 701); newer CI runs were pending when last checked. Manual device QA is not recorded. |

## Product tracking

### SYNTRA OS

- **Source version:** 0.1.0
- **Status:** In development; recorded CI checks passed.
- **Latest main revision:** [11db170](https://github.com/dea78799-dotcom/SyntraOS/commit/11db17032c7de96a9ad4aed7fc541542d352be60)
- **CI evidence:** [Successful SyntraOS CI run](https://github.com/dea78799-dotcom/SyntraOS/actions/runs/45151413981) · [Evidence record](evidence/2026-11-23-syntraos-ci.md)
- **Next tracking items:** validate on supported physical hardware, record device/firmware environment, and continue feature-level and security testing.

### Syntra Files

- **Source version:** 0.71.0 (Android versionCode 701)
- **Status:** Testing and upgrading; CI runs for newer commits were in progress at last check. The successful v0.69.0 build is historical, not evidence for v0.71.0.
- **Latest main revision checked:** [82461ec](https://github.com/dea78799-dotcom/Syntra-Files/commit/82461ec4ee015f4fc9b54c29308a37590edcc78e)
- **Latest CI status:** [Android Actions](https://github.com/dea78799-dotcom/Syntra-Files/actions) — verify the current result before downloading an artifact.
- **Manual QA:** [Checklist](https://github.com/dea78799-dotcom/Syntra-Files/blob/main/docs/physical-device-qa-checklist.md) · [Issue #1](https://github.com/dea78799-dotcom/Syntra-Files/issues/1)
- **Next tracking items:** install the latest successful APK on a physical Android device; validate SAF permission persistence, browsing, search, sorting, file operations, large files, and error recovery.

## Task tracking

- [Task Registry](task-registry.md) lists prioritized tasks, statuses, acceptance criteria, and linked GitHub issues. The initial tasks remain open until their full acceptance criteria are met.

## Version tracking

- [Version Tracker](version-tracker.md) records source versions, revisions, CI evidence, release channels, and publication history. The current source versions are development versions; no software releases are recorded yet.

## Repository quality

The ecosystem repository check workflow verifies a small set of structural/documentation expectations. It is not a substitute for product compilation, device testing, security review, or release QA.

- Workflow: [Repository Checks](../.github/workflows/repository-checks.yml)
- Script: [check_repo.py](../scripts/check_repo.py)
- QA standard: [quality-assurance.md](quality-assurance.md)
- Issue workflow: [issue-tracking.md](issue-tracking.md)
- Release process: [release-management.md](release-management.md)

## Update procedure

1. Update the product status only when there is evidence.
2. Link evidence (issue, PR, build, or test report).
3. Keep “planned”, “in progress”, “testing”, and “released” distinct.
4. Report CI results separately from device/runtime QA.
5. Review this page whenever a major milestone or release changes.

## Build and QA evidence

- [Product Build and QA Evidence Template](product-evidence-template.md) is the standard record for build identity, test environment, QA gates, defects, and release decisions.
- [SYNTRA OS CI evidence](evidence/2026-11-23-syntraos-ci.md) and [Syntra Files CI evidence](evidence/2026-11-23-syntra-files-ci.md) document the current successful CI runs.
- Product release QA remains YELLOW until device/runtime compatibility, security review, and release-readiness evidence is recorded.

## Current limitations

This is a manually maintained dashboard, not a live monitoring service. It does not automatically fetch issue counts, release tags, workflow runs, device test results, or project-board data. Product evidence links reflect the verified snapshot recorded on 2026-11-23 and must be refreshed after later product revisions.
