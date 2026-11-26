# SYNTRA Development Dashboard

**Dashboard version:** 1.2.0  
**Last reviewed:** 2026-11-26  
**Scope:** Manually maintained ecosystem snapshot. Product runtime status is not automatically measured here.

## Overall status

| Area | Status | Notes |
|---|---|---|
| Ecosystem documentation | 🟢 Reviewed | Product directory and lifecycle statuses refreshed 2026-11-26. |
| GitHub issue templates | 🟢 Added | Bug report and feature request forms are present. |
| Pull request template | 🟢 Added | Includes scope, testing, risk, and security prompts. |
| Repository checks | 🟢 Last recorded pass | Historical run #45154179133 passed on 2026-11-23; rerun after this documentation update to verify the new revision. |
| SYNTRA OS | 🟡 Early Development | Version 0.1.0; recorded CI/QEMU evidence exists, but physical-device validation is not established. |
| SYNTRA VPN | 🟡 Engineering foundation | Android prototype 0.6.5-dev; device-authentication foundations exist; production gateway/end-to-end VPN operation not confirmed. |
| SYNTRA VM | 🟡 Phase 1 | Development sandbox 0.1; billing foundation is the current phase; real payment and VM provisioning not confirmed. |
| SYNTRA SOCIAL | 🟡 Prototype | Version 2.56; Supabase foundations; not production-ready or publicly launched. |
| SYNTRA NEXUS | 🟡 Prototype | Version 0.11.37-dev; local-first/opt-in direction; production transport and remote-control capabilities not confirmed. |
| SYNTRA Website | 🟢 Maintained | Version 2.15; active catalogue excludes Syntra Files. |
| Discord Bridge / AUTO-BOT | 🟢 Runtime connection observed | Railway deployment SUCCESS and runtime logs confirmed Discord login and global slash-command registration on 2026-11-26. Continuous uptime and replica count are not inferred from those logs. |
| Syntra Files | ⚫ Discontinued | Founder decision 2026-11-26; retained for historical reference only. No active development or QA. |

## Product tracking

### SYNTRA OS
- **Source version:** 0.1.0
- **Status:** Early Development; primary product-development focus.
- **Evidence boundary:** Recorded CI/QEMU checks are not physical-device compatibility evidence.
- **Next focus:** Continue core OS engineering and record physical-device validation when available.
- **Repository:** https://github.com/dea78799-dotcom/SyntraOS

### SYNTRA VPN
- **Version reference:** 0.6.5 development
- **Status:** Android prototype / engineering foundation.
- **Current focus:** Device authentication, Control Plane, Android client, and VPN gateway integration.
- **Limitations:** WireGuard engine remains disabled pending gateway and end-to-end tests; do not claim production VPN service.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-VPN

### SYNTRA VM
- **Version reference:** 0.1 development sandbox
- **Status:** Phase 1 Billing Foundation.
- **Business model:** Hybrid — recurring Premium subscription, SYNTRA VM Credits, and additional storage purchases.
- **Limitations:** No verified production billing or VM provisioning.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-VM

### SYNTRA SOCIAL
- **Version reference:** Prototype 2.56
- **Status:** Prototype; not production-ready.
- **Current focus:** Mobile-first social workflows, Supabase foundations, end-to-end testing, privacy, and moderation.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-Social

### SYNTRA NEXUS
- **Version reference:** 0.11.37-dev
- **Status:** Android local-first prototype.
- **Limitations:** Production LAN transport, remote control, file transfer, and screen streaming are not confirmed as available.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/tree/main/projects/syntra-nexus

### SYNTRA Website
- **Version reference:** 2.15
- **Status:** Maintained public website.
- **Current requirement:** Keep public product directory, downloads, and changelog aligned with verified project states.
- **Website:** https://dea78799-dotcom.github.io/SYNTRA-Website
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-Website

### SYNTRA Discord Bridge / AUTO-BOT
- **Status:** Operational community infrastructure.
- **Latest observed evidence:** Railway deployment SUCCESS; runtime logs on 2026-11-26 confirmed founder memory loaded, persistent SYNTRA knowledge loaded (schema v2, 9 projects), Discord login, and global slash-command registration.
- **Limitations:** This snapshot does not prove uninterrupted uptime, 1/1 replica, or that every command was manually tested. Recheck Railway runtime and Discord interactions for live status.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-Discord-Bridge

### SYNTRA Ecosystem / SYNTRA GROUP
- **Status:** Maintained governance/documentation layer.
- **Role:** Shared architecture, standards, product catalogue, roadmap, and project governance.
- **Repository:** https://github.com/dea78799-dotcom/SYNTRA-Ecosystem

## Discontinued projects

### Syntra Files
- **Status:** Discontinued by Founder decision on 2026-11-26.
- **Policy:** Do not include in active product counts, active roadmap tasks, testing queues, or upgrade plans.
- **Retention:** Preserve source repository, changelog, and historical CI evidence as archive/reference. Historical CI does not imply current support.
- **Repository:** https://github.com/dea78799-dotcom/Syntra-Files

## Task tracking

- [Task Registry](task-registry.md) lists prioritized shared tasks and acceptance criteria. Product-specific tasks belong in their respective repositories.
- Remove Syntra Files from active queues; preserve any completed historical issue references.

## Version tracking

- [Version Tracker](version-tracker.md) records source versions, revisions, CI evidence, release channels, and release records.
- A development version is not automatically a published release.

## Repository quality

The ecosystem repository check workflow verifies a limited set of structural/documentation expectations. It is not a substitute for product compilation, device testing, security review, or release QA.

- Workflow: [Repository Checks](../.github/workflows/repository-checks.yml)
- Script: [check_repo.py](../scripts/check_repo.py)
- QA standard: [quality-assurance.md](quality-assurance.md)
- Release process: [release-management.md](release-management.md)

## Update procedure

1. Update status only when supported by Founder confirmation or repository/runtime evidence.
2. Link evidence (issue, PR, build, test report, or runtime log).
3. Keep prototype, in development, operational, testing, released, and discontinued distinct.
4. Report CI results separately from device/runtime QA.
5. Review this page whenever a major milestone or lifecycle decision occurs.

## Current limitations

This is a manually maintained dashboard, not a live monitoring service. It does not automatically fetch issue counts, release tags, workflow runs, device test results, uptime, or replica counts. Recheck source repositories and runtime systems before making current operational claims.
