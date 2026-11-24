# SYNTRA NEXUS

**Version:** 0.11.4-dev  
**Status:** Local activity summary, explicit MediaProjection consent prototype, fail-closed screen-share authorization foundation, and opt-in LAN service discovery; no network pairing transport or screen streaming yet  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current prototype scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token; invitation lifetime is limited to two minutes.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Scan QR codes using Google Play services Code Scanner; the app does not request direct camera permission.
- Valid scanned invitations are shown in a local review dialog; scanning does not establish trust or a network connection.
- Create a per-installation EC P-256 signing identity in Android Keystore. Private key material is non-exportable; the app displays the public-key SHA-256 fingerprint in grouped form and can copy the raw fingerprint for manual comparison.
- Validate fingerprint formatting as exactly 64 hexadecimal characters and group it into 16 four-character blocks for visual comparison; unit tests cover valid, short and non-hex inputs.
- Build a canonical, versioned transcript with fixed field ordering, length-prefixed values, both public identities, invitation request ID, expiry, and independent 32-byte peer nonces.
- Sign and verify transcript hashes with ECDSA/SHA-256, a NEXUS-specific domain separator, and explicit initiator/responder role binding.
- Verify that a peer's supplied public key exactly matches the identity assigned to that role in the transcript.
- Pin each handshake session to the SHA-256 hash of its exact canonical transcript and verify that the local controller key is the transcript's initiator identity; an unbound or substituted transcript is rejected.
- Require a separate role-bound signature over an explicit APPROVE decision from the peer after local approval; the state machine requires both approvals before entering PAIRED.
- Maintain a synchronized one-time token registry with ACTIVE, REDEEMED, and REVOKED states; store token fingerprints rather than raw tokens.
- Persist registry records using AES-256-GCM, Android Keystore and atomic file writes. The QR issuer registers each invitation locally before showing it.
- Provide a local Activity Monitor card using Android UsageStats after the device owner explicitly grants Usage Access in Settings.
- Show today's app foreground-time totals and an estimated screen-interactive total from Android screen-state events when those events are available.
- Keep activity data on the same phone; no remote activity feed or background upload is implemented.
- Add unit tests for screen-session aggregation, including multiple sessions, open sessions, duplicate state events and empty timelines.
- Add an explicit screen-sharing permission card. The user must tap the button and approve Android's MediaProjection system dialog.
- Hold the approved MediaProjection session in a non-exported foreground service with an ongoing notification and a visible Stop action.
- Require notification permission on Android 13+ before opening the consent flow.
- Automatically end the consent session after 30 minutes.
- Add a fail-closed authorization gate for future screen sharing. It requires verified peer identity, mutual pairing approval, capability grants on both devices, sharer MediaProjection consent, receiver approval for the current session, encrypted transport, and a visible foreground notification.
- Add bounded frame-metadata validation for future encoded frames; no pixel payload is accepted by this metadata model.
- Add unit tests proving each missing authorization requirement blocks transmission and invalid frame metadata is rejected.
- Add opt-in Android NSD (DNS-SD/mDNS) discovery for the dedicated `_syntranexus._tcp.` service type.
- Show discovered service name, IP address and port in the local dashboard, clearly marked as unverified.
- Provide explicit Start Scan and Stop Scan controls; discovery stops when the dashboard component is disposed.
- Filter discovery results by the NEXUS service type and service-name prefix, then validate basic metadata before display.
- Add unit tests for service metadata validation.
- Add the INTERNET permission required for LAN discovery on the current target SDK.
- Keep discovery separate from trust: the app does not connect to discovered addresses, redeem QR tokens, exchange identities or send app data.
- Clarify the screen-share UI state: it reports that a service-start request was sent, not that the MediaProjection service has confirmed an active session.

## Not implemented yet

- Redeeming invitations over a real peer-to-peer connection; QR registration is local to the issuer and is not exchanged with the scanner.
- Distributed one-time redemption or replay prevention across devices.
- Live exchange of fresh nonces and signatures between two devices.
- TLS handshake, authenticated LAN transport, encrypted durable trusted-device persistence or remote revocation.
- NEXUS companion service advertisement; discovery currently searches for compatible advertisements but this app does not yet publish one.
- Connecting the authorization gate to a real transport or enforcing it at a network boundary.
- Actual screen-frame capture, encoding, transmission, receiver rendering or remote stop signaling.
- Automatic game classification. A future game-time total needs an explicit user-reviewed app list.
- Reliable real-time foreground-app/online status; UsageStats does not provide a guaranteed live presence signal.
- Remote activity summaries, website filtering, remote actions, TV client or cloud sync.
- Verified Android Keystore persistence and UsageStats behavior on physical devices.

**Important:** Activity Monitor is local to the phone where permission was granted. It does not monitor another device. Screen-state history may be incomplete on some Android versions or after reboot. App usage values are Android-reported foreground totals, not a guarantee of exact screen-on time. NEXUS does not collect messages, keystrokes, notification contents or private app data.

## Local trusted-device registry foundation (0.11.4-dev)

- Add a local in-memory registry for peer identities only after the pairing state machine reaches PAIRED with BOTH_APPROVED.
- Registration requires the exact transcript pinned by the handshake, the local public key and peer public key assigned to their transcript roles, and a valid display name.
- Store the peer's public key and SHA-256 fingerprint in the registry; callers receive defensive copies of key bytes.
- Revoke a local record by fingerprint; revoked records are excluded from the active-device list.
- Reject unverified sessions, transcript substitution, and peer keys that do not match the transcript.

**Current limitation:** this registry is an in-memory prototype. It is cleared when the app process is restarted, has no UI yet, and does not send revocation to another device. Encrypted durable persistence is the next step. This feature does not make the QR scanner or LAN discovery a trust mechanism.

## Device identity display (0.11.3-dev)

The dashboard can display and copy the SHA-256 fingerprint of the installation's Android Keystore public key. The private key remains non-exportable. A fingerprint is useful for out-of-band comparison but is not, by itself, proof that a remote device is trusted. No LAN handshake or remote identity exchange is enabled by this UI change.

## LAN discovery boundary (0.11.2-dev)\n\nLAN discovery uses Android NSD and is started only after the user taps Scan LAN. The UI displays matching service metadata as untrusted. Service discovery can be spoofed by another device on the same network; the name, IP address and port are not proof of identity. This milestone does not open a TCP socket, connect to a discovered peer, transmit QR tokens, or establish TLS. The current app does not yet advertise its own NEXUS service, so a second device will not appear unless it runs a compatible advertiser.\n\nThe current target SDK is 35. Android documentation says apps targeting SDK 36 or lower retain local-network access implicitly through INTERNET; do not add ACCESS_LOCAL_NETWORK until the app targets SDK 37 or higher and implements its runtime permission flow.\n\n## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow is configured to run unit tests and a debug build. A successful CI build validates compilation and included unit tests; it does not replace testing on a physical device. No successful build or test result is claimed until a workflow reports it.

## Planned device capabilities

See [`docs/DEVICE-CAPABILITIES.md`](docs/DEVICE-CAPABILITIES.md) for screen-sharing consent and local domain-filtering design.

## Security baseline

- Both devices must explicitly approve pairing before trust is established.
- Every future capability requires a separate, visible and revocable grant.
- Usage Access must be granted by the device owner in Android Settings.
- Screen sharing must use MediaProjection's system consent and a visible foreground notification; the current prototype does not capture or transmit frames.
- The future frame-transmission path must pass every ScreenShareAuthorization gate; discovery or QR scanning alone is insufficient.
- Never store passwords, pairing secrets, or private keys in source control.
- No hidden collection of messages, photos, screens, or private app data.
