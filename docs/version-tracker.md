# SYNTRA Version Tracker

**Tracker version:** 1.1.0  
**Last reviewed:** 2026-11-23

This page records product source versions, verified revisions, CI evidence, and software release status. A version present in source configuration is recorded as a development version; it is not automatically a published release.

## Current product records

| Product | Source version | Channel | Current status | Latest main revision | CI evidence |
|---|---|---|---|---|---|
| [SYNTRA OS](https://github.com/dea78799-dotcom/SyntraOS) | 0.1.0 | Development | CI passed; physical-device QA pending | [11db170](https://github.com/dea78799-dotcom/SyntraOS/commit/11db17032c7de96a9ad4aed7fc541542d352be60) | [2026-11-23 CI record](evidence/2026-11-23-syntraos-ci.md) |
| [Syntra Files](https://github.com/dea78799-dotcom/Syntra-Files) | 0.69.0 (versionCode 690) | Development | Debug APK CI passed; device QA pending | [bd4f54f](https://github.com/dea78799-dotcom/Syntra-Files/commit/bd4f54f78edd2f27b9845e19050a4173525d4692) | [2026-11-23 CI record](evidence/2026-11-23-syntra-files-ci.md) |

## Verified CI snapshot

- **SYNTRA OS:** The workflow run on the recorded `main` revision completed successfully. Host Debug and Release builds and CTest passed; the ARM64 kernel cross-build, ELF verification, EL0 check, QEMU boot, and desktop framebuffer verification also passed.
- **Syntra Files:** The workflow run on the recorded `main` revision completed successfully. The Debug APK build and artifact upload passed.
- **Important boundary:** These results verify the specific CI jobs only. Physical-device compatibility and complete product QA remain pending. See the two evidence records for scope and limitations.

## Release history

No SYNTRA OS or Syntra Files software releases are recorded in this repository yet. The source versions above are not declarations of a published or stable release.

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

A candidate may advance only when its evidence is recorded:

1. Scope and acceptance criteria reviewed.
2. Source revision is identifiable.
3. Build process and artifact are reproducible enough for the project.
4. Applicable QA checks completed and recorded.
5. No unresolved release-blocking defects.
6. Security/privacy review appropriate to the change.
7. Release notes and known limitations prepared.
8. Owner approval documented.

## Product evidence

Use the [Product Build and QA Evidence Template](product-evidence-template.md) to collect build identity, target environment, QA results, known issues, and release decisions. Verified CI snapshots are stored in [docs/evidence](evidence/); create a new dated record when a product revision or test environment changes.

## Rules

- Record product versions from the actual source configuration; do not invent them.
- Never invent a build number, release date, or artifact URL.
- Documentation changes alone do not create a software release.
- Alpha/Beta/RC/Stable labels must match test evidence and intended audience.
- Update this tracker when a candidate is prepared or a release is published.
- Keep history; do not overwrite prior published entries.
- For details, follow [Release Management](release-management.md) and [Quality Assurance](quality-assurance.md).
