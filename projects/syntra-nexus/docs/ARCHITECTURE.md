# SYNTRA NEXUS — Architecture 0.4

## Product boundary

NEXUS is an opt-in device-management system. A device is manageable only after its owner or authorized user approves pairing and grants specific capabilities. No stealth monitoring or bypass of Android permissions is part of the design.

## Local-first topology

```text
Android Controller
       |
 QR Invitation / Mutual Approval
       |
 Local Device Registry
       |
 LAN Discovery / Authenticated TLS
       |
 Approved NEXUS Agent (future)
```

The Android application is the first controller. A TV client is deferred until the target TV platform is selected. The MVP does not depend on cloud services. Google Play services Code Scanner is used only to present the QR scanning interface; scanning does not create a network connection.

## Components

- **Controller UI:** device dashboard, QR invitation, pairing status and revocation.
- **Pairing:** short-lived invitation payload, receiving-side validation, mutual approval and replay prevention.
- **Identity:** device key pair generated and protected with Android Keystore; public-key fingerprint used for peer identity.
- **Trust store:** local record of approved device identity, capabilities and revocation state.
- **LAN transport:** future discovery through Android Network Service Discovery (mDNS/DNS-SD) and an authenticated TLS channel.
- **Device agent:** future companion installed on each participating device. It exposes only explicitly granted actions.
- **Capability policy:** allow-list of operations per paired device; deny by default.

## Current implementation boundary

MVP 0.4-dev generates QR invitations, scans QR codes through Google Play services Code Scanner, and creates a local EC P-256 device signing key in Android Keystore. Scanned payloads are validated locally for scheme, host, version, required and duplicate fields, token format, payload length and expiry. A valid scan is shown for review. A local-only state machine can record pending review, accepted on this device, rejected on this device, or expired. A local acceptance is not peer approval and does not establish trust. The private key is non-exportable; only the public key and SHA-256 fingerprint are exposed. ECDSA/SHA-256 transcript-signing helpers include a NEXUS-specific domain separator. The app does not yet build a canonical peer transcript or verify a remote peer. No token is redeemed and no trust relationship is created. There is still no LAN receiver, mutual authentication, TLS session, trusted-device record or replay database.

## Device identity implementation

- Generate an EC key pair on curve `secp256r1` (NIST P-256) under a stable per-installation Android Keystore alias.
- Restrict the key to signing and verification with SHA-256.
- Keep the private key inside Android Keystore; never serialize or transmit it.
- Identify the public key using a SHA-256 fingerprint. Users must compare fingerprints over a trusted channel before relying on a peer identity.
- Sign only a 32-byte SHA-256 digest of a canonical, versioned pairing transcript. The signature input is domain-separated for NEXUS.
- Key deletion is a local action and does not automatically revoke records on other devices.

This is identity-key plumbing, not a complete authentication protocol. It must be combined with fresh nonces, transcript binding, one-time token redemption, mutual verification and explicit approval before trust is granted.

## Pairing protocol direction

1. Initiator creates a random request ID and a cryptographically random one-time token.
2. The invitation is encoded as a QR payload and expires after two minutes.
3. Receiver scans the QR with the Google code scanner and validates version, format, token structure and expiry locally.
4. Receiver contacts the initiator over the local network; the token is redeemed once and then invalidated.
5. Both devices exchange public identity information and establish an authenticated TLS session.
6. Both sides display the peer identity and requested capabilities; each side must explicitly approve.
7. The approved public identity and capability grants are persisted. Either side can revoke the relationship.

Do not put long-lived secrets in QR codes. Do not trust a device based only on its IP address or local network presence. A QR token must be single-use and rate-limited when redemption is implemented.

## Network behavior

- MVP 0.1 was a UI-only prototype and performed no network discovery.
- MVP 0.2-dev generates a local QR invitation only; no network discovery or socket connection is implemented.
- A future LAN milestone may use Android NSD (DNS-SD/mDNS).
- Discovery results are untrusted until authenticated pairing completes.
- Connections must fail closed when authentication or authorization fails.
- No port forwarding or public Internet exposure is required for the local-first milestone.

## Android constraints and permissions

- The app currently targets SDK 35 and does not request local-network access because it does not perform LAN operations yet.
- Android documentation states that apps targeting SDK 36 or lower receive local-network access implicitly through INTERNET; apps targeting SDK 37 or higher must handle ACCESS_LOCAL_NETWORK as a runtime permission.
- Add and request network permissions only when the LAN feature is implemented, and verify behavior on the target Android versions.
- Android does not allow a normal app to silently control arbitrary apps or inspect private app data. Remote capabilities must use public Android APIs, explicit runtime permissions, or a companion app's own supported interface.
- Accessibility services must not be used as a general-purpose hidden-control bypass.

## TV strategy

Keep the protocol and capability model platform-neutral. Implement a TV client only after confirming whether the target is Android TV / Google TV, Samsung Tizen, LG webOS, or a TV box. Different platforms expose different APIs and permissions.

## Milestones

- **0.1 Foundation:** Compose dashboard and truthful prototype states — complete.
- **0.2a Invitation:** local QR generation, short-lived random token and unit tests — complete.
- **0.2b Scanner:** QR scanning and local payload validation — prototype implemented.
- **0.2c Review state:** local invitation review state machine and expiry boundary checks — implemented; still no peer handshake.
- **0.3 Identity foundation:** local Android Keystore key pair, public fingerprint and transcript-signature primitives — implemented; peer authentication is not complete.
- **0.4 Mutual pairing:** canonical transcript, fresh nonces, one-time token redemption, mutual verification, approval and local trust registry.
- **0.5 LAN Link:** authenticated discovery and TLS connection status.
- **0.6 Capabilities:** small, safe allow-list of device actions.
- **0.7 TV Client:** platform-specific client after target selection.
