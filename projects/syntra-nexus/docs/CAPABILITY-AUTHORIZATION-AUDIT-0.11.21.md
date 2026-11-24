# Capability Authorization Audit — 0.11.21-dev

## Scope

This milestone adds a local, fail-closed capability-grant foundation. It does not add transport messages, sockets, file access, screen capture, remote control, or data transfer.

## Capability model

The first explicit capabilities are:
- `usage_summary_read`
- `shared_files_read`
- `file_receive`
- `screen_share`

There is no blanket device-control capability. Unimplemented remote actions are intentionally absent.

## Grant binding

Every request is bound to:
- one grant ID;
- the peer's exact SHA-256 public-key fingerprint;
- the original pairing request ID and transcript hash;
- one logical session ID;
- exactly one capability;
- issue and expiry timestamps (maximum five minutes, and never beyond the pairing transcript expiry).

The local and peer devices must each sign the exact canonical request using ECDSA/SHA-256 and their own role-bound P-256 identity. The signatures use a dedicated capability-grant domain separator and include the initiator/responder role, preventing a signature from one side being reused as the other's approval. Boolean approval flags are not accepted.

## Enforcement model

- Grant issuance rechecks the current paired handshake, exact transaction and live trusted-device registry.
- Every future use must call `CapabilityGrantRegistry.mayUse`; it rechecks the exact grant, capability, session ID, expiry, both signatures and current device revocation state.
- A capability grant can be revoked independently. A device-level revocation invalidates every grant for that device at the next authorization check.
- The registry is memory-only in this milestone. Process death drops all grants, which is fail-closed. Encrypted durable grant storage is deferred until its schema, recovery and revocation semantics receive a separate audit.
- The current pairing transaction model treats the local device as INITIATOR and the peer as RESPONDER. Role-symmetric session authorization is not implemented.

## Explicit limitations

The signatures can only be collected after the respective device owners explicitly approve the exact request in a future UI/protocol flow. This milestone provides cryptographic verification and policy enforcement primitives, not a user-consent UI. It is not wired to TLS, LAN transport, file APIs, MediaProjection, UsageStats export, or any remote operation. No capability is currently usable for an actual device action.

## Verification

Seven JVM tests cover successful dual approval, wrong-role signature rejection, session replay rejection, independent grant revocation, device revocation, expiry and rejection of new grants from revoked devices. Tests are authored and must be confirmed by GitHub Actions before being considered passed.
