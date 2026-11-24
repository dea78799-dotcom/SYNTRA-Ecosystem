# SYNTRA NEXUS

**Version:** 0.11.1-dev  
**Status:** Local activity summary, explicit MediaProjection consent prototype, and fail-closed screen-share authorization foundation; no network pairing or screen streaming yet  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current prototype scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token; invitation lifetime is limited to two minutes.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Scan QR codes using Google Play services Code Scanner; the app does not request direct camera permission.
- Valid scanned invitations are shown in a local review dialog; scanning does not establish trust or a network connection.
- Create a per-installation EC P-256 signing identity in Android Keystore. Private key material is non-exportable; the app displays only the public-key SHA-256 fingerprint.
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
- Clarify the UI state: it reports that a service-start request was sent, not that the MediaProjection service has confirmed an active session.

## Not implemented yet

- Redeeming invitations over a real peer-to-peer connection; QR registration is local to the issuer and is not exchanged with the scanner.
- Distributed one-time redemption or replay prevention across devices.
- Live exchange of fresh nonces and signatures between two devices.
- TLS handshake, LAN transport, NSD/mDNS discovery, trusted-device registry or remote revocation.
- Connecting the authorization gate to a real transport or enforcing it at a network boundary.
- Actual screen-frame capture, encoding, transmission, receiver rendering or remote stop signaling.
- Automatic game classification. A future game-time total needs an explicit user-reviewed app list.
- Reliable real-time foreground-app/online status; UsageStats does not provide a guaranteed live presence signal.
- Remote activity summaries, website filtering, remote actions, TV client or cloud sync.
- Verified Android Keystore persistence and UsageStats behavior on physical devices.

**Important:** Activity Monitor is local to the phone where permission was granted. It does not monitor another device. Screen-state history may be incomplete on some Android versions or after reboot. App usage values are Android-reported foreground totals, not a guarantee of exact screen-on time. NEXUS does not collect messages, keystrokes, notification contents or private app data.

## Build and test

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
