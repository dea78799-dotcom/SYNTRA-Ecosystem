# SYNTRA Version Tracker

**Tracker version:** 1.2.0  
**Last reviewed:** 2026-11-23

This page records product source versions, verified revisions, CI evidence, and software release status. A version present in source configuration is recorded as a development version; it is not automatically a published release.

## Current product records

| Product | Source version | Channel | Current status | Latest main revision | CI evidence |
|---|---|---|---|---|---|
| [SYNTRA OS](https://github.com/dea78799-dotcom/SyntraOS) | 0.1.0 | Development | CI passed on recorded revision; physical-device QA pending | [11db170](https://github.com/dea78799-dotcom/SyntraOS/commit/11db17032c7de96a9ad4aed7fc541542d352be60) | [2026-11-23 CI record](evidence/2026-11-23-syntraos-ci.md) |
| [Syntra Files](https://github.com/dea78799-dotcom/Syntra-Files) | 0.71.0 (versionCode 701) | Development | Newer CI runs pending; v0.69.0 CI evidence is historical; device QA pending | [82461ec](https://github.com/dea78799-dotcom/Syntra-Files/commit/82461ec4ee015f4fc9b54c29308a37590edcc78e) | [Physical-device QA checklist](https://github.com/dea78799-dotcom/Syntra-Files/blob/main/docs/physical-device-qa-checklist.md) · [QA issue #1](https://github.com/dea78799-dotcom/Syntra-Files/issues/1) |

## Verified CI snapshot

- **SYNTRA OS:** The workflow run on the recorded revision completed successfully. Host Debug and Release builds and CTest passed; ARM64 kernel cross-build, ELF verification, EL0 check, QEMU boot, and desktop framebuffer verification passed.
- **Syntra Files v0.69.0:** The earlier workflow run on revision `bd4f54f` completed successfully and uploaded a Debug APK. This is historical evidence for v0.69.0 only.
- **Syntra Files v0.71.0:** Source configuration confirms versionName `0.71.0` and versionCode `701`. Multiple newer Android CI runs were observed in progress at the time of this update; no success/failure is asserted for those runs. Check the repository Actions page for current results.
- **Important boundary:** CI results verify only the jobs actually completed for their specific source revisions. Physical-device compatibility and complete product QA remain pending.

## Release history

No SYNTRA OS or Syntra Files software releases are recorded in this repository yet. Source versions above are not declarations of published or stable releases.

| Product | Version | Channel | Date | Source revision | Notes |
|---|---|---|---|---|---|
| — | — | — | — | — | No product release entries yet |

## Version record template

Copy this template for each release candidate or published release:

- **Product:**
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

## Product evidence

Use the [Product Build and QA Evidence Template](product-evidence-template.md) to collect build identity, target environment, QA results, known issues, and release decisions. Verified CI snapshots are stored in [docs/evidence](evidence/); create a new dated record when a product revision or test environment changes. The [Syntra Files physical-device checklist](https://github.com/dea78799-dotcom/Syntra-Files/blob/main/docs/physical-device-qa-checklist.md) is the next manual test.

## Rules

- Record product versions from actual source configuration; do not invent them.
- Never invent a build number, release date, or artifact URL.
- Documentation changes alone do not create a software release.
- Alpha/Beta/RC/Stable labels must match test evidence and intended audience.
- Update this tracker when a candidate is prepared or a release is published.
- Keep history; do not overwrite prior published entries.
- Follow [Release Management](release-management.md) and [Quality Assurance](quality-assurance.md).
