# SYNTRA Version Tracker

**Tracker version:** 1.0.0  
**Last reviewed:** 2026-11-23

This page tracks versions and release readiness for each product. Version numbers are **not assigned** until the product owner approves a specific release candidate. Empty fields are intentionally left unassigned.

## Current product records

| Product | Current version | Channel | Status | Latest revision | Release date |
|---|---|---|---|---|---|
| SYNTRA OS | Not assigned | Development | In development | Not recorded | Not released |
| Syntra Files | Not assigned | Development / testing | Testing and upgrading | Not recorded | Not released |

## Release history

No SYNTRA OS or Syntra Files software releases are recorded in this repository yet.

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

## Rules

- Never invent a version, build number, release date, or artifact URL.
- Documentation changes alone do not create a software release.
- Alpha/Beta/RC/Stable labels must match test evidence and intended audience.
- Update this tracker when a candidate is prepared or a release is published.
- Keep history; do not overwrite prior published entries.
- For details, follow [Release Management](release-management.md) and [Quality Assurance](quality-assurance.md).
