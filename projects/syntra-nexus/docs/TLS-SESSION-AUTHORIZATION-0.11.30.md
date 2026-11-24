# TLS session authorization — 0.11.30-dev

## What changed

- Added `TlsSessionAuthorization`, a short-lived handle for one completed JSSE mutual-TLS session.
- Capture requires both the expected local and peer P-256 certificate identities, TLS 1.2/1.3, valid device certificates, and an active trusted-device record with the exact pairing request ID and transcript hash.
- Every authorization check re-reads the current trusted-device registry and revalidates the JSSE session metadata and both certificate identities.
- Revoking the peer or changing its pairing provenance causes subsequent checks to fail closed.
- Capability consent now retains the TLS authorization handle supplied by authenticated ingress and rechecks it when the local owner presses Allow. This closes the gap between showing a consent prompt and approving it.
- TLS ingress derives the expected local fingerprint from the initiator identity already bound to the pairing transcript.

## Tests authored

- Capture succeeds for the exact local identity, peer identity and pairing provenance.
- A previously captured authorization fails after local trust revocation.
- Wrong local identity, unsupported TLS protocol and an invalidated JSSE session are rejected.

## Security boundary

This remains a policy component, not a transport implementation. It does not open sockets, establish a handshake, transmit a QR bearer token, transfer files, capture or transmit screen frames, or execute remote actions.

An `SSLSession` does not prove that its underlying socket is still open. Any future transport must also check its concrete socket/channel state immediately before I/O and must call this authorization gate before each protected operation. No production LAN connection is enabled by this milestone.

## Verification

Tests have been added but are not considered passed until GitHub Actions or a local Gradle run reports success. No successful build, test or APK result is claimed.
