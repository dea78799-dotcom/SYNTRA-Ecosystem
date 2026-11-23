# Changelog

All notable changes to SYNTRA Ecosystem are documented in this file.

This project follows a simple Keep a Changelog-style structure. Versions are assigned when a release is formally prepared; documentation commits do not automatically mean that a product has been released.

## [Unreleased]

### Added
- SYNTRA NEXUS 0.5.0-dev: canonical, versioned mutual-pairing transcript with fixed field ordering, explicit length prefixes, both peer public keys, invitation request ID/expiry, and independent 32-byte nonces.
- NEXUS ECDSA signatures bind the transcript hash to the initiator or responder role; tests cover role-reflection rejection and canonical transcript validation.

### Security
- The transcript is a protocol foundation only; nonce exchange, token redemption, mutual peer verification, user approval, trusted-device storage, and LAN transport remain unimplemented.

### Added
- SYNTRA NEXUS 0.4.0-dev: Android Keystore-backed EC P-256 device identity and public-key fingerprint display.
- NEXUS ECDSA/SHA-256 transcript-signing and verification helpers with domain separation, plus JVM tests for valid signatures and tampering.
- SYNTRA NEXUS 0.3.0-dev: local QR invitation generation, Google Play services QR scanning, and strict scanned-payload validation.
- NEXUS unit tests for invitation token format, randomness, expiry, and payload validation.
- NEXUS GitHub Actions now runs unit tests before assembling the debug APK.

### Security
- QR invitations are documented as temporary and not yet a complete pairing or authentication flow.
- Device identity keys remain non-exportable in Android Keystore; local identity creation does not establish trust or authenticate a peer.
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
