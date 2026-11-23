# SYNTRA Development Dashboard

**Dashboard version:** 1.0.0  
**Last reviewed:** 2026-11-23  
**Scope:** Repository-level progress tracking. Product runtime status is not automatically measured here.

## Overall status

| Area | Status | Notes |
|---|---|---|
| Ecosystem documentation | 🟢 Baseline established | Core documentation is present; continue review as projects evolve. |
| GitHub issue templates | 🟢 Added | Bug report and feature request forms are in the repository. |
| Pull request template | 🟢 Added | Includes scope, testing, risk, and security prompts. |
| Repository checks | 🟡 Workflow added | Runs on GitHub Actions after push; this document does not claim a run has passed. |
| Issue labels | 🟡 Planned | Label taxonomy is documented; labels themselves have not been created. |
| GitHub Projects board | 🟡 Planned | Board structure is documented; no board has been created. |
| SYNTRA OS | 🟡 In development | Product-specific build and test status must be updated by maintainers. |
| Syntra Files | 🟡 Testing and upgrading | No current test results are represented in this dashboard. |

## Product tracking

### SYNTRA OS
- **Status:** In development
- **Next tracking items:** record current build/revision, supported device/environment, known issues, test evidence, and release target.
- **Status evidence:** link to a relevant issue, pull request, build, or test report when available.

### Syntra Files
- **Status:** Testing and upgrading
- **Next tracking items:** record app version/revision, tested file types and actions, device/environment, known issues, and test evidence.
- **Status evidence:** link to a relevant issue, pull request, build, or test report when available.

## Task tracking

- [Task Registry](task-registry.md) lists prioritized tasks, statuses, acceptance criteria, and linked GitHub issues. The initial tasks are open until evidence confirms completion.

## Version tracking

- [Version Tracker](version-tracker.md) records approved product versions, release channels, revisions, QA evidence, and publication history. No software releases are recorded yet.

## Repository quality

The repository check workflow verifies a small set of structural/documentation expectations. It is not a substitute for product compilation, device testing, security review, or release QA.

- Workflow: [Repository Checks](../.github/workflows/repository-checks.yml)
- Script: [check_repo.py](../scripts/check_repo.py)
- QA standard: [quality-assurance.md](quality-assurance.md)
- Issue workflow: [issue-tracking.md](issue-tracking.md)
- Release process: [release-management.md](release-management.md)

## Update procedure

1. Update the product status only when there is evidence.
2. Link evidence (issue, PR, build, or test report).
3. Keep “planned”, “in progress”, “testing”, and “released” distinct.
4. Never mark a check green just because a file exists; report the workflow result separately.
5. Review this page whenever a major milestone or release changes.

## Build and QA evidence

- [Product Build and QA Evidence Template](product-evidence-template.md) is the standard record for build identity, test environment, QA gates, defects, and release decisions.
- Product build and QA status remains unverified until maintainers attach completed records with source revisions and test evidence. The repository CI result does not establish product runtime quality.

## Current limitations

This is a manually maintained dashboard. It does not currently fetch live issue counts, release tags, test results, or project-board data. GitHub Actions will begin running the repository check when the workflow is triggered by a push, pull request, or manual dispatch.
