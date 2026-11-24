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

MVP 0.4-dev generates QR invitations, scans QR codes through Google Play services Code Scanner, and creates a local EC P-256 device signing key in Android Keystore. Scanned payloads are validated locally for scheme, host, version, required and duplicate fields, token format, payload length and expiry. A valid scan is shown for review. A local acceptance is not peer approval and does not establish trust. The private key is non-exportable; only the public key and SHA-256 fingerprint are exposed. ECDSA/SHA-256 transcript-signing helpers include a NEXUS-specific domain separator. The app does not yet build a canonical peer transcript or verify a remote peer. No token is redeemed and no trust relationship is created. There is still no LAN receiver, mutual authentication, TLS session, trusted-device record or replay database.

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

Keep the protocol and capability model platform-neutral. Implement a TV client only after confirming whether the target TV platform is Android TV / Google TV, Samsung Tizen, LG webOS, or a TV box. Different platforms expose different APIs and permissions.

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

## Pairing protocol foundation (0.5-dev)

The current implementation defines a canonical transcript with a fixed field order: protocol marker and version, invitation request ID, expiry, initiator public key, responder public key, initiator nonce, and responder nonce. Integer fields use big-endian encoding; variable-length values are length-prefixed. Both peer nonces must be independently supplied as 32-byte values.

The transcript is SHA-256 hashed. ECDSA signatures cover the hash together with a NEXUS-specific domain separator and the signer's explicit initiator/responder role. Role separation is intended to prevent a signature produced in one protocol role from being reflected as the other role.

This is not yet a complete handshake. Nonce exchange, one-time invitation redemption, peer-side signature verification, mutual approval, trusted-device persistence, and authenticated LAN transport remain unimplemented.

## Capability roadmap

Proposed optional capabilities are documented in `docs/DEVICE-CAPABILITIES.md`:

- Daily screen-on and app/game usage summaries through Android UsageStats with user-granted access, freshness indicators and aggregate reporting.
- Live screen sharing only through MediaProjection's system consent, foreground notification, visible sharing indicator, and stop controls; no default recording.
- Local adult-domain filtering through an opt-in VPNService/DNS filter with visible settings and an off switch. Keyword filtering is limited to NEXUS-owned text fields; encrypted web content is not inspected.

These capabilities remain unimplemented until the pairing and capability-grant system is real and tested. A pairing alone will not enable monitoring or filtering.

## LAN discovery foundation (0.11.2-dev)

The Android controller includes an opt-in NSD discovery component for the dedicated `_syntranexus._tcp.` DNS-SD service type. The user must start discovery explicitly and can stop it. Matching service records are resolved to a service name, address and port and displayed as unverified metadata. The component does not open sockets, send invitation tokens, authenticate a peer, or establish trust. Discovery records are attacker-controlled network input and must never be treated as identity.

This controller currently searches for NEXUS advertisements but does not publish an advertisement itself. A compatible companion advertiser and a separately designed authenticated pairing/TLS protocol are required before another device can connect. For target SDK 35, INTERNET is declared; ACCESS_LOCAL_NETWORK is not requested because Android's current documented runtime permission enforcement applies to apps targeting SDK 37 or higher.

## Screen-share authorization foundation (0.11.1-dev)

The app now contains a pure policy gate that returns true only when all of these conditions are present: the peer identity is verified, both devices approved pairing, both devices granted the screen-share capability, the sharer approved the current MediaProjection session, the receiver approved the current viewing session, encrypted transport is established, and a visible foreground notification is active.

The gate is deliberately not connected to a socket or capture pipeline yet. It is a fail-closed policy component and a testable prerequisite, not proof of an authenticated session. Frame metadata has bounded dimensions and encoded-size checks; it carries no pixel bytes.

The consent card also distinguishes a start request from a confirmed active service. The current foreground service still does not create a VirtualDisplay, capture frames, record, or transmit content. Actual streaming remains blocked until real mutual pairing, a trust store, authenticated transport, receiver approval, and lifecycle-safe capture are implemented and tested on physical devices.


## Bounded LAN packet protocol (0.11.8-dev)

The transport foundation defines a binary envelope with a fixed `SNX1` marker, protocol version, enumerated message type, fixed 22-character request ID, big-endian payload length and bounded payload. The codec accepts only known message types and protocol version 1, limits payloads to 64 KiB, and rejects truncation, trailing bytes, malformed identifiers and invalid packet sizes. Payload bytes are defensively copied.

The current implementation is deliberately transport-independent. It does not open a socket, advertise a service, authenticate a peer, redeem a pairing token, verify a signature, establish TLS or execute a remote capability. A packet that passes structural parsing is still untrusted input. The next stage must define and review mutual authentication, certificate/identity binding, replay handling and TLS lifecycle before connecting this codec to LAN sockets.


## Pinned peer identity trust policy (0.11.9-dev)

The transport security foundation adds an EC public-key pin policy backed by the active local trusted-device registry. A presented leaf certificate is accepted by the policy only when its EC SubjectPublicKeyInfo bytes and SHA-256 fingerprint match an active, non-revoked record. The X.509 trust-manager adapter also checks certificate validity, rejects CA certificates as device identities, and checks the digital-signature key-usage bit when that extension is present. It applies the same pinned-peer rule to client and server certificates.

This is an identity pin, not a public CA chain validator. Its trust input must come only from a completed, transcript-bound pairing that both devices approved. The adapter is not yet installed in an SSLContext, there is no TLS KeyManager for presenting the local Android Keystore identity, and no SSLSocket is opened. The next implementation must bind the TLS handshake to the same approved identities, define replay-resistant handshake state, and ensure the negotiated TLS version and certificate proof-of-possession are verified before any application packet is accepted.


## TLS identity foundation (0.11.10-dev)

The Android controller now has an X.509 key manager that presents the existing EC P-256 Android Keystore identity. The private key remains non-exportable. Before exposing the identity to JSSE, the manager checks the leaf certificate and signs a fresh random challenge with the private key, then verifies the proof using the certificate public key. This detects a mismatched certificate/key pair.

TLS context construction is scoped to one exact active peer fingerprint. The trust manager checks that expected fingerprint in addition to the active registry record, so a different trusted device cannot satisfy the peer check. TLS 1.3 is preferred and TLS 1.2 is the minimum. Server socket configuration requires client certificate authentication.

This is configuration only. No socket, listener, TLS handshake, invitation redemption or application-packet exchange is implemented. Identity pinning is not public CA-chain or DNS-name validation; the selected peer fingerprint is the identity boundary. The next stage must implement a replay-resistant pairing exchange and lifecycle-safe socket transport before application capabilities are connected.


## Transcript-bound LAN handshake foundation (0.11.11-dev)

The handshake frame binds each signed message to the exact invitation request ID, expiry, canonical transcript hash, sender role, sender and recipient fingerprints, a fresh 32-byte nonce and monotonic per-sender sequence. Its bounded binary codec rejects malformed or oversized frames. The signature covers all frame fields and is verified against the public key assigned to the sender role in the canonical transcript. The receiver checks both role fingerprints and expiry before accepting the proof.

A bounded in-memory replay guard enforces sequence 0 for the first message and the next consecutive sequence thereafter for each invitation/sender pair. Expired sessions are pruned. This guard is process-local and not durable or distributed; it is not a substitute for persistent single-use invitation redemption. The frame verifier and replay guard are not connected to a socket. No token is redeemed, no remote approval is inferred from a signature, and no capabilities are transmitted.


## Durable handshake replay state (0.11.12-dev)

The handshake replay guard now accepts an optional persistence adapter. The Android adapter stores bounded session sequence and nonce-hash history in an AES-GCM encrypted AtomicFile backed by Android Keystore. Each accepted frame is written before the in-memory state advances. A persistence error fails closed. This remains a local component and is not yet wired to the LAN transport or pairing UI.


## QR token redemption protocol (0.11.13-dev)

The issuer-side redemption foundation defines a bounded SNR1 request containing the exact invitation request ID and expiry, the short-lived bearer token and the receiver's canonical EC P-256 public key. The verifier compares all invitation fields against the issuer's original PairingRequest before invoking the synchronized one-time token registry. The registry remains the durable authority for single-use state.

The bearer token is present in the encoded request and therefore must never be transmitted over plaintext or an unverified connection. The payload may only be sent after mutual TLS and peer pinning succeed. A successful redemption consumes the token even if the subsequent pairing is interrupted; recovery requires a new QR invitation. The codec and verifier are not yet connected to TLS, a socket, UI or trusted-device registration.


## Pairing transaction coordination (0.11.14-dev)

The coordinator binds one local transaction to the invitation request ID, expiry, exact transcript hash and responder fingerprint. It enforces the local lifecycle and requires the existing PairingHandshakeSession to prove transcript-bound identity verification and both approvals before the transaction can enter PAIRED. It does not accept a peer-approved Boolean or infer consent from a valid signature alone.

A restored non-terminal attempt is downgraded to RECOVERY_REQUIRED. The user must explicitly discard it and create a new invitation; the consumed QR token is never reactivated. Persistence is committed before in-memory mutation and failures stop the transition. The coordinator is currently a pure local component: no Android Keystore adapter, UI integration, socket transport or automatic trusted-device registration is included.
