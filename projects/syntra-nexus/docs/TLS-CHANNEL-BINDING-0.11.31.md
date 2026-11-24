# TLS channel binding — 0.11.31-dev

## Changes

- Add `TlsSocketChannelBinding` for a completed, already-connected `SSLSocket`.
- Snapshot the exact socket object, local/remote socket addresses and ports, negotiated TLS protocol, JSSE session creation time and bounded session identifier.
- Recheck socket connectivity, close/shutdown state, endpoint values, current JSSE session identity, certificate identities and current trusted-device provenance before each protected operation.
- Capability ingress now accepts an `SSLSocket`, not a detached `SSLSession`, and captures the channel binding before preparing a consent request.
- The pending consent object retains the socket binding; the explicit Allow path rechecks the same live socket and current trust before creating the local approval signature.
- A different socket object, a changed TLS session, a closed/shutdown socket, or a revoked peer fails closed.

## Important concurrency boundary

A socket can be closed by another thread immediately after any authorization check. A future transport must own the socket lifecycle, serialize close and protected I/O on one transport lock, and invoke the authorization check immediately before every operation. This helper does not itself perform I/O, manage socket ownership, or eliminate races caused by external code concurrently closing or using the socket.

## Not enabled

No production socket is created, accepted or connected by this milestone. No QR bearer token, file, screen frame or remote-control command is transmitted. The helper only evaluates an SSLSocket supplied by a future caller after a completed mutual-TLS handshake.

## Verification

JSSE loopback tests are authored for the exact socket, unrelated socket rejection and invalidation after close. They are not considered passed until Gradle/GitHub Actions reports success.
