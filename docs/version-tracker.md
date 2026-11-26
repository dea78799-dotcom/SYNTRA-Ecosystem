# SYNTRA Version Tracker

**Tracker version:** 1.3.0  
**Last reviewed:** 2026-11-26

This page records source versions, verified revisions, CI evidence, and software release status. A version present in source configuration is a development version; it is not automatically a published release.

## Active product records

| Project | Version reference | Channel / maturity | Current status |
|---|---|---|---|
| [SYNTRA OS](https://github.com/dea78799-dotcom/SyntraOS) | 0.1.0 | Early Development | Primary development focus; physical-device QA not established |
| [SYNTRA VPN](https://github.com/dea78799-dotcom/SYNTRA-VPN) | 0.6.5-dev | Android prototype / engineering | Gateway and end-to-end VPN operation not confirmed |
| [SYNTRA VM](https://github.com/dea78799-dotcom/SYNTRA-VM) | 0.1 development sandbox | Development | Phase 1 Billing Foundation |
| [SYNTRA SOCIAL](https://github.com/dea78799-dotcom/SYNTRA-Social) | Prototype 2.56 | Prototype | Not production-ready |
| [SYNTRA NEXUS](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/tree/main/projects/syntra-nexus) | 0.11.37-dev | Prototype | Production transport and remote-control capabilities not confirmed |
| [SYNTRA Website](https://dea78799-dotcom.github.io/SYNTRA-Website) | 2.15 | Maintained website | Active public website |
| [SYNTRA Discord Bridge / AUTO-BOT](https://github.com/dea78799-dotcom/SYNTRA-Discord-Bridge) | Runtime service; source version not asserted here | Operational infrastructure | Discord login and slash-command registration observed in Railway logs on 2026-11-26 |
| [SYNTRA Ecosystem / GROUP](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem) | Documentation 1.1.0 catalogue | Governance/documentation | Maintained |

Version references above are drawn from the latest available project notes and are not all independently rebuilt in this documentation update. Verify the canonical product repository before preparing a release.

## Discontinued project history

### Syntra Files
- **Lifecycle status:** Discontinued by Founder decision on 2026-11-26.
- **Historical source version previously recorded:** 0.71.0 (versionCode 701).
- **Historical CI:** Earlier v0.69.0 CI success and later v0.71.0 source snapshot are historical records only.
- **Current release status:** No ongoing development, testing, upgrade, or release work is planned.
- **Retention:** Keep repository, commits, changelog, and historical QA/CI records for reference.
- **Historical repository:** https://github.com/dea78799-dotcom/Syntra-Files

Historical versions and CI records must not be interpreted as current support or an active release channel.

## CI and release evidence

- Link each CI result to the exact source revision and workflow run.
- Record device/runtime tests separately from CI.
- A successful build does not prove complete product QA or production readiness.
- Do not carry forward old test results as evidence for newer source revisions.

## Release history

No new software release is declared by this documentation update. Add release records only when a real artifact has been published and verified.

| Project | Version | Channel | Date | Source revision | Notes |
|---|---|---|---|---|---|
| — | — | — | — | — | No new release entries in this update |

## Version record template

Copy this template for each release candidate or published release:

- **Project:**
- **Version:**
- **Channel:** Development / Alpha / Beta / RC / Stable / Hotfix
- **Status:** Planned / Building / Testing / Approved / Published / Withdrawn
- **Target platform(s):**
- **Source revision / commit:**
- **Build identifier:**
- **Date prepared:**
- **Date published:**
- **Release owner:**
- **QA summary:** GREEN / RED / YELLOW / N/A (explain)
- **Known issues:**
- **Release notes:**
- **Artifact link:** (only after a real artifact exists)
- **Integrity hash:** (only after artifact verification)
- **Rollback / recovery note:**

## Release readiness gates

1. Scope and acceptance criteria reviewed.
2. Source revision is identifiable.
3. Build process and artifact are reproducible enough for the project.
4. Applicable QA checks completed and recorded.
5. No unresolved release-blocking defects.
6. Security/privacy review appropriate to the change.
7. Release notes and known limitations prepared.
8. Owner approval documented.

## Rules

- Record versions from actual source configuration or clearly label them as version references.
- Never invent a build number, release date, or artifact URL.
- Documentation changes alone do not create a software release.
- Alpha/Beta/RC/Stable labels must match test evidence and intended audience.
- Keep historical records, including discontinued projects, without treating them as active.
- Follow [Release Management](release-management.md) and [Quality Assurance](quality-assurance.md).
