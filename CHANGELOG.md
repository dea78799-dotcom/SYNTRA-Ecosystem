# Changelog

All notable changes to SYNTRA Ecosystem are documented in this file.

This project follows a simple Keep a Changelog-style structure. Versions are assigned when a release is formally prepared; documentation commits do not automatically mean that a product has been released.

## [Unreleased]

### Added
- SYNTRA NEXUS 0.3.0-dev: local QR invitation generation, Google Play services QR scanning, and strict scanned-payload validation.
- NEXUS unit tests for invitation token format, randomness, expiry, and payload validation.
- NEXUS GitHub Actions now runs unit tests before assembling the debug APK.

### Security
- QR invitations are documented as temporary and not yet a complete pairing or authentication flow.
- NEXUS documentation records mutual approval, one-time redemption, Keystore identity, and LAN authentication as release requirements.

### Planned
- Continue refining the ecosystem architecture and product documentation.
- Define measurable completion criteria for each roadmap phase.
- Add project-level validation and release checks when implementation files are introduced.

## [0.1.0] - 2026-11-23

### Added
- Established the SYNTRA Ecosystem repository overview and project status in `README.md`.
- Added the initial ecosystem architecture in `docs/architecture.md`.
- Added the product directory for SYNTRA OS and Syntra Files in `docs/products.md`.
- Added the phased development roadmap in `docs/roadmap.md`.
- Added shared development and quality standards in `docs/development-standards.md`.

### Status
- SYNTRA OS: In development.
- Syntra Files: Testing and upgrading.
- This is a documentation baseline, not a software product release.

---

## Changelog policy

- Record user-visible, technical, documentation, and compatibility changes.
- Group entries under Added, Changed, Fixed, Deprecated, Removed, Security, or Planned where relevant.
- Keep entries concise and describe the impact.
- Do not mark a feature as released until its implementation and required checks are complete.
- Create a versioned section only when the project owner has approved that version.
