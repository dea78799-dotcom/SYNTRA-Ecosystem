# SYNTRA NEXUS — Architecture 0.3

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

MVP 0.3-dev generates QR invitations and scans QR codes through Google Play services Code Scanner. Scanned payloads are validated locally for scheme, host, version, required and duplicate fields, token format, payload length and expiry. A valid scan is shown for review. A local-only state machine can record pending review, accepted on this device, rejected on this device, or expired. A local acceptance is not peer approval and does not establish trust. No token is redeemed and no trust relationship is created. There is still no LAN receiver, mutual authentication, TLS session, trusted-device record or replay database.

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
- **0.3 Pairing protocol:** one-time token redemption, mutual approval and local trust registry.
- **0.4 LAN Link:** authenticated discovery and TLS connection status.
- **0.5 Capabilities:** small, safe allow-list of device actions.
- **0.6 TV Client:** platform-specific client after target selection.
