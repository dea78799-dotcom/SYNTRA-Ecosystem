# TLS packet session driver — 0.11.35-dev

## Included
- Fix the existing Kotlin compilation error in `CapabilityRequestEnvelope`: it uses a regular class because its defensive-copy byte-array constructor parameter is intentionally not a public property.
- Add `BoundedPacketChannel` and adapt `TlsBoundedPacketChannel` to expose only bounded packet read/write, open state and close.
- Add a caller-driven `TlsPacketSessionDriver` that coordinates one outbound drain, one inbound packet read, supervisor state transitions and a second outbound drain for control replies.
- Preserve idle read timeouts only when the channel remains open. A partial-frame timeout is terminal because the bounded channel closes itself; EOF, I/O failure, malformed data and authorization failure also disconnect the session.
- Keep close non-blocking relative to a pending read: the driver does not hold a monitor around `pumpOnce`, and channel close delegates to the lifecycle manager's interrupting socket close.
- Add deterministic fake-channel tests for outbound ordering, correlated PONG, idle timeout, partial-frame closure, I/O failure, EOF, invalid PONG and close behavior.

## Execution contract
- The owner must call `start()` and schedule `pumpOnce(readTimeoutMillis)` itself.
- No background thread, executor, automatic reconnect, socket creation, LAN listener or service discovery is introduced.
- The channel must already represent a mutually authenticated, currently authorized TLS socket. The driver does not establish or authenticate a connection.
- Application packets are returned to the caller; this driver does not execute remote commands or grant capabilities.
- A successful PING/PONG is liveness evidence only, not authorization.

## Verification
- Unit tests are added but must be confirmed by GitHub Actions before the milestone is marked verified.
- No APK is claimed until the Android assemble job succeeds.
