# SYNTRA NEXUS

**Version:** 0.11.18-dev  
**Status:** Android Keystore and AtomicFile recovery instrumentation tests for pairing and replay stores, replay-ledger crash recovery and integrity coverage, encrypted pairing-transaction persistence and recovery, pairing transaction coordinator, QR-token redemption protocol, durable local handshake replay ledger, trusted-device profile management and TLS identity foundation; no live TLS transport or screen streaming yet  
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


## Encrypted AtomicFile recovery audit (0.11.18-dev)

- Audit all four Android Keystore-backed AtomicFile stores: replay ledger, trusted devices, one-time pairing tokens and pairing transaction.
- Serialize each store's read/write operations across adapter instances in the process with a shared lock.
- Call `AtomicFile.openRead()` before deciding a store is absent, so a committed `.bak` snapshot is recovered even when the base file is missing.
- Bound encrypted reads and validate persisted record counts, identifiers, fingerprints, state values, sequence counters and duplicate entries.
- If an encryption key is missing while the base file or backup still exists, fail closed. Do not silently generate a replacement key that cannot decrypt the existing data.
- Add Android instrumentation coverage for backup-only recovery, partial replacement recovery, ciphertext tampering and missing-key behavior, alongside normal persistence recreation.

**Audit findings addressed:** The replay, trusted-device and token adapters previously bypassed backup recovery when the base file was missing. The replay adapter also needed to fail closed rather than create a replacement key when ciphertext remained. The trusted-device and token adapters now receive the same recovery, locking, bounded-read and missing-key protections. The transaction adapter's key check now also accounts for a backup-only file.

**Verification boundary:** Instrumentation tests are authored and committed, but no successful emulator/CI run is confirmed. Replay protection remains local and is not connected to a socket or distributed across devices. No live pairing or token transmission is enabled.

## Android storage recovery verification (0.11.17-dev)

- Serialize all transaction persistence operations across adapter instances with a shared process lock because Android AtomicFile requires caller-managed synchronization.
- Add Android instrumentation tests that create and reopen the encrypted store, verify the fixed ciphertext size, and exercise Android Keystore AES-GCM on an emulator/device.
- Tamper with ciphertext and verify authentication fails without deleting the damaged file.
- Delete the dedicated Keystore alias while ciphertext remains and verify the adapter fails closed rather than generating a replacement key or erasing data.
- Simulate an interrupted AtomicFile replacement by preserving the previous committed file as the AtomicFile backup and leaving a partial replacement; verify `openRead()` restores the last committed snapshot.
- Run the instrumentation suite through an Android API 35 emulator in GitHub Actions.

**Verification boundary:** The instrumentation suite is configured but is not considered passed until a GitHub Actions run reports success. Emulator behavior does not prove every OEM-specific Keystore implementation or hardware-backed key behavior on physical devices. No network pairing, token transmission, screen streaming or remote actions are enabled.

## Durable replay ledger (0.11.12-dev)

- Persist inbound handshake replay counters and SHA-256 nonce hashes using AES-256-GCM, Android Keystore and AtomicFile.
- Commit replay state before accepting a frame; a write failure returns PERSISTENCE_FAILURE without advancing in-memory state.
- Restore and validate the exact sequence/transcript history after process recreation; reject malformed or unauthenticated storage instead of silently resetting it.
- Bound storage to 1,024 sessions by default, 4,096 maximum, 256 frames per session and a 20 MiB encrypted file.

**Current limitation:** This ledger is a local persistence component only. No socket flow or app UI currently creates and uses it; it does not coordinate replay state between devices or redeem QR invitation tokens. Tests are authored but not yet executed.

## QR token redemption protocol (0.11.13-dev)

- Define a bounded `SNR1` redemption request containing the exact request ID, invitation expiry, one-time bearer token and receiver EC P-256 public key.
- Reject malformed token encodings, non-canonical or non-P-256 public keys, unknown protocol versions, invalid lengths, truncated packets and trailing bytes.
- The issuer verifier compares the request against the exact invitation it created before invoking the existing synchronized one-time token registry.
- Successful redemption consumes the token exactly once. If pairing is interrupted after redemption, the invitation remains consumed and a new QR invitation is required.
- Add tests for codec round trips, malformed packets, invitation mismatch, token mismatch, expiry, one-time redemption and public-key validation.

**Critical limitation:** The request contains the raw QR bearer token. It must only be sent inside a mutually authenticated TLS session after the remote certificate is pinned to the expected peer. The codec and verifier are not connected to a socket, TLS session, UI or trusted-device registration. Tests are authored but not executed.

## Not implemented yet

- Sending the 0.11.13 redemption request over a real peer-to-peer connection; QR registration remains local to the issuer and is not exchanged with the scanner.
- Distributed one-time redemption or replay prevention across devices.
- Live exchange of fresh nonces and signatures between two devices.
- Live TLS handshake and authenticated LAN transport; the pinned trust manager is not wired to a socket or key manager yet.
- Remote revocation.
- NEXUS companion service advertisement; discovery currently searches for compatible advertisements but this app does not yet publish one.
- Connecting the authorization gate to a real transport or enforcing it at a network boundary.
- Actual screen-frame capture, encoding, transmission, receiver rendering or remote stop signaling.
- Automatic game classification. A future game-time total needs an explicit user-reviewed app list.
- Reliable real-time foreground-app/online status; UsageStats does not provide a guaranteed live presence signal.
- Remote activity summaries, website filtering, remote actions, TV client or cloud sync.
- Verified Android Keystore persistence and UsageStats behavior on physical devices.

**Important:** Activity Monitor is local to the phone where permission was granted. It does not monitor another device. Screen-state history may be incomplete on some Android versions or after reboot. App usage values are Android-reported foreground totals, not a guarantee of exact screen-on time. NEXUS does not collect messages, keystrokes, notification contents or private app data.

## Trusted-device profiles (0.11.7-dev)

- Add a validated local rename operation for trusted-device display names (1–48 characters).
- Persist renamed names before changing in-memory state; a storage failure leaves the previous name intact.
- Add filters for active, revoked and all stored records.
- Add a device detail dialog showing the SHA-256 fingerprint, trust timestamp, revocation timestamp and public-key byte length.
- Keep revoked records visible in the revoked/all filters and preserve their revocation timestamp when renamed.
- Add tests for rename persistence after registry recreation, renaming revoked records, unknown fingerprints and invalid names.

**Current limitation:** Display names are local labels and are not synchronized to another device. The registry is still not connected to a completed pairing transport. Persistence recreation is covered with a fake persistence adapter in unit tests; Android Keystore and physical-device restart behavior still require device testing.

## Trusted-device management UI (0.11.6-dev)

- Replace the misleading connected-device count with a dedicated local trusted-device card.
- Display the active trusted-device count, display name, trust timestamp and grouped SHA-256 fingerprint.
- Add copy-fingerprint and refresh actions.
- Add a confirmation dialog before local revocation.
- After successful revocation, reload the active list from the registry.
- Show storage/operation errors explicitly; a failed encrypted-registry load is not presented as an empty device list.
- Explain that local revocation does not notify or disconnect a remote peer because authenticated network transport is not implemented.

**Current limitation:** The UI can display and revoke records already in the encrypted local registry. The current prototype has no completed network pairing flow that inserts a new peer record, so scanning QR or discovering a LAN service still does not add a trusted device.

## Encrypted trusted-device persistence (0.11.5-dev)

- Store trusted peer public keys, SHA-256 fingerprints, display names, trust timestamps and local revocation timestamps in an AES-256-GCM encrypted file.
- Keep the AES key in Android Keystore under a dedicated alias; private identity keys and pairing secrets are not written into this registry.
- Use a fresh GCM IV for each write and Android AtomicFile for replacement.
- Validate the public-key fingerprint, key size, display name and timestamps when loading records; reject duplicate fingerprints and registries above 128 records.
- Persist each mutation before updating in-memory state. If storage fails, the in-memory registry is left unchanged.
- Load the registry in the dashboard and report a read/decryption failure instead of silently treating corrupted data as an empty trusted list.
- Add tests for persistence restoration, revocation restoration, invalid stored fingerprints and fail-closed write failures.

**Current limitation:** The storage layer and dashboard loading are implemented, but no completed remote pairing flow currently calls the registration API. No device should be added merely by scanning QR or discovering an NSD service. Physical-device Keystore behavior and the Android build still require verification.

## Local trusted-device registry foundation (0.11.4-dev)

- Add a local in-memory registry for peer identities only after the pairing state machine reaches PAIRED with BOTH_APPROVED.
- Registration requires the exact transcript pinned by the handshake, the local public key and peer public key assigned to their transcript roles, and a valid display name.
- Store the peer's public key and SHA-256 fingerprint in the registry; callers receive defensive copies of key bytes.
- Revoke a local record by fingerprint; revoked records are excluded from the active-device list.
- Reject unverified sessions, transcript substitution, and peer keys that do not match the transcript.

**Current limitation:** this registry is an in-memory prototype. It is cleared when the app process is restarted, has no UI yet, and does not send revocation to another device. Encrypted durable persistence is implemented in 0.11.5-dev; remote revocation is still not implemented. This feature does not make the QR scanner or LAN discovery a trust mechanism.

## Device identity display (0.11.3-dev)

The dashboard can display and copy the SHA-256 fingerprint of the installation's Android Keystore public key. The private key remains non-exportable. A fingerprint is useful for out-of-band comparison but is not, by itself, proof that a remote device is trusted. No LAN handshake or remote identity exchange is enabled by this UI change.

## LAN discovery boundary (0.11.2-dev)

LAN discovery uses Android NSD and is started only after the user taps Scan LAN. The UI displays matching service metadata as untrusted. Service discovery can be spoofed by another device on the same network; the name, IP address and port are not proof of identity. This milestone does not open a TCP socket, connect to a discovered peer, transmit QR tokens, or establish TLS. The current app does not yet advertise its own NEXUS service, so a second device will not appear unless it runs a compatible advertiser.

The current target SDK is 35. Android documentation says apps targeting SDK 36 or lower retain local-network access implicitly through INTERNET; do not add ACCESS_LOCAL_NETWORK until the app targets SDK 37 or higher and implements its runtime permission flow.

## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
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


## Bounded LAN transport protocol (0.11.8-dev)

- Define a versioned binary envelope with the `SNX1` marker, message type, fixed-length request ID and big-endian payload length.
- Support only the initial protocol message types: HELLO, PAIRING_CHALLENGE, PAIRING_PROOF, PAIRING_APPROVAL, ERROR, PING and PONG.
- Limit payloads to 64 KiB and reject packets outside the permitted total size before parsing.
- Reject unknown protocol versions, unknown message types, invalid request IDs, malformed lengths, truncated packets and trailing bytes.
- Copy payload bytes at object boundaries to prevent callers from mutating packet contents after construction.
- Add unit tests for valid round trips, defensive copies, empty payloads and malformed/oversized packets.

**Security boundary:** This is a packet codec only. It does not open sockets, authenticate peers, establish TLS, redeem tokens, verify signatures, approve pairing or execute remote actions. All decoded data remains untrusted until a future authenticated handshake succeeds. The next milestone must implement and review mutual TLS/identity binding before any real connection or capability message is enabled.


## Pinned peer identity trust policy (0.11.9-dev)

- Add a fail-closed EC public-key pin policy that matches the presented key bytes and SHA-256 fingerprint against an active local trusted-device record.
- Reject unknown keys, revoked devices, malformed/non-canonical key encodings, oversized key material and non-EC keys.
- Add an X.509 trust-manager foundation for both client and server peer checks. It checks leaf-certificate validity, rejects CA certificates as device identities, checks digital-signature key usage when present, and requires the leaf EC public key to match an active trusted record.
- Keep the trust manager separate from socket creation and TLS configuration; no live network connection is started.
- Add unit tests for active, unknown, revoked, mismatched, malformed, oversized and RSA public keys.

**Security boundary:** This is public-key identity pinning, not a general CA trust store or a completed mutual-TLS implementation. The key must already have been accepted through a transcript-bound pairing with approval on both devices. The trust manager does not yet provide a local TLS key manager, open sockets, negotiate TLS, exchange fresh handshake messages, redeem QR tokens or enforce capability grants. Do not treat LAN discovery or a certificate alone as pairing approval.


## TLS identity foundation (0.11.10-dev)

The Android Keystore identity is now exposed to JSSE through a dedicated X.509 key manager without exporting the private key. Initialization checks the local certificate and proves that the private key matches its public key by signing a fresh challenge. TLS context creation is scoped to one exact active peer fingerprint; revoked, unknown and self-referential peers are rejected. TLS 1.3 is preferred and TLS 1.2 is the minimum. Server socket configuration requires client authentication. This milestone creates configuration objects only: it does not open sockets, run a handshake, redeem invitations, exchange packets or stream screen content. See `docs/TLS-FOUNDATION-0.11.10.md`.


## Transcript-bound handshake foundation (0.11.11-dev)

Adds a bounded `SNH1` signed handshake frame bound to the invitation request, expiry, canonical transcript hash, sender role, sender/recipient fingerprints, a fresh 32-byte nonce and a monotonic sequence. ECDSA signatures cover the complete frame header. Verification checks the exact transcript and role-bound public keys. A bounded in-memory replay guard accepts sequence 0 once and then only the next sequence for that sender and invitation. This is a protocol foundation only: the replay guard is not durable or distributed, and no socket, token redemption, remote approval, trust registration or capability transport is enabled. See `docs/HANDSHAKE-0.11.11.md`.


## Pairing transaction coordinator (0.11.14-dev)

- Add a single-attempt coordinator bound to the exact request ID, invitation expiry, canonical transcript SHA-256 and responder fingerprint.
- Enforce the sequence CREATED → TOKEN_REDEEMED → IDENTITY_VERIFIED → LOCAL_APPROVAL_PENDING → PAIRED.
- Identity and peer approval are accepted only through the existing transcript-bound PairingHandshakeSession verifier; no peer-approved Boolean is accepted from transport input.
- The only transition to PAIRED requires the verifier-produced session to be PAIRED with BOTH_APPROVED and bound to the exact same transcript.
- Commit persistence before changing in-memory state; storage errors fail closed.
- A restored non-terminal attempt becomes RECOVERY_REQUIRED. It cannot resume using stale process memory or silently reuse an already redeemed QR token. The user must discard the interrupted attempt and create a fresh invitation.
- Add unit tests for field validation, transition ordering, persistence failure, recovery downgrade, terminal cleanup and expiry.

**Current limitation:** This coordinator is not yet wired to the Android UI, encrypted Android Keystore persistence adapter, network socket or trusted-device registration flow. Tests are authored but have not been executed.


## Encrypted pairing transaction persistence (0.11.15-dev)

- Persist one active pairing transaction in an AES-256-GCM encrypted file using a non-exportable Android Keystore key.
- Use a fresh 12-byte GCM IV, versioned authenticated additional data and Android AtomicFile with fsync/finishWrite/failWrite.
- Store only the request ID, expiry, canonical transcript hash, peer fingerprint, lifecycle state and update timestamp. Never store the QR bearer token, private keys, signatures, raw nonces or message payloads.
- Enforce a fixed-width binary codec and reject unknown markers/versions/states, invalid fields, truncation and trailing bytes.
- On process restart, downgrade every non-terminal transaction to RECOVERY_REQUIRED and persist that downgrade before exposing the coordinator. A storage failure prevents coordinator startup.
- Show transaction recovery status in the dashboard and allow the user to discard the interrupted local transaction; a redeemed QR token remains consumed and must be replaced with a new invitation.
- Add six pure JVM codec tests.

**Current limitation:** Android Keystore encryption/decryption, AtomicFile crash recovery and key invalidation still require a physical-device test. The coordinator is not yet connected to a completed socket pairing flow or automatic trusted-device registration. Unit tests are authored but not executed in this environment.
