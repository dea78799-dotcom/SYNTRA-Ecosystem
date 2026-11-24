# TLS socket lifecycle manager — 0.11.32-dev

## Scope

- Add a single owner for an already-connected, mutually authenticated SSLSocket.
- Serialize protected I/O callbacks with a fair lock so two callers cannot interleave socket operations.
- Recheck the exact socket binding, TLS session, certificate identities, transcript provenance and current trusted-device registry immediately before and after each callback.
- Revoke the manager and close the socket when the authorization check fails.
- Explicit close() changes the lifecycle to CLOSED and closes the socket without waiting for the I/O lock. This is intentional: closing the socket can unblock a pending read.
- Reject all future operations after REVOKED or CLOSED.
- Add JSSE loopback tests for serialized operations, trust revocation and explicit close.

## Lifecycle states

- ACTIVE: operations may proceed only after a fresh authorization check.
- REVOKED: the authorization check failed; the socket is closed and the manager cannot be reused.
- CLOSED: the owner explicitly closed the socket; the manager cannot be reused.

## Concurrency contract

All future transport code must use withAuthorizedIo for every read/write and must not retain the socket beyond its callback. close() may run concurrently and closes the underlying socket immediately; an in-flight operation may therefore fail with an I/O exception. The manager serializes callbacks, but cannot make an external registry mutation and a socket write one atomic transaction. Authorization is rechecked at the operation boundary and after the callback. The transport must still handle I/O failures and network loss.

## Verification boundary

The manager takes ownership of an already-connected socket. It does not create listeners, discover peers, connect to LAN devices, perform TLS handshakes, exchange pairing tokens, transfer files, capture screens or execute remote-control actions. JVM loopback tests are authored but must not be considered passed until Gradle/CI reports success. No production LAN transport is enabled.
