# Capability consent and durable grant storage — 0.11.22-dev

## Implemented

- Add an Android Keystore AES-256-GCM encrypted capability ledger using a dedicated alias and a fresh 96-bit IV for each write.
- Authenticate the file with a versioned AAD domain string; use Android AtomicFile, fsync, and a shared process lock.
- Persist only the exact capability request metadata, the two role-bound public approval signatures, and an optional local revocation timestamp. No private key, QR bearer token, or raw pairing secret is stored.
- Bound the ledger to 128 grants, 256 KiB plaintext and 512 KiB encrypted storage. Validate request fields, capability names, duplicate IDs, signature lengths and revocation timestamps when loading.
- Fail closed on ciphertext authentication errors, malformed data, missing Keystore keys while an encrypted file or backup exists, and persistence failures.
- Make registry mutations durable-first: a failed save does not update the in-memory grant set.
- Add a dashboard card for stored grants, expiry state, local revocation and storage errors.
- Add a reusable consent dialog that shows the requesting device, exact capability, session and expiry. Approval callbacks are invoked only after the user taps “Cho phép”; the future caller must create the role-bound signature inside that callback.
- Add JVM tests for registry recreation, durable revocation and write-failure atomicity, plus Android instrumentation tests for encryption round-trip, revocation restoration, ciphertext tampering, missing keys and AtomicFile recovery.

## Security boundary

- The current app has no authenticated incoming capability-request transport. Therefore no remote request can yet populate the consent dialog, and the dashboard correctly shows that no incoming request is available.
- The consent dialog is a reusable UI component, not a live network flow. Do not create a grant from an unverified QR scan, LAN discovery result, user-entered peer name, or unsigned request.
- Every later mayUse call must still revalidate the exact transcript-bound pairing session and current trusted-device registry. Persistence alone never authorizes use.
- Existing grants are short-lived and session-bound. Restarting the process restores their records, but does not bypass transcript expiry, signature verification, or device revocation.
- No sockets, TLS handshakes, file transfer, screen capture, screen transmission, remote actions, or TV control are enabled by this milestone.

## Verification status

Source and tests are committed. The preceding CI run passed JVM unit tests but the Android emulator failed to boot before instrumentation tests could run; the APK assembly step was skipped in that run. A new CI run is required to validate this milestone. Do not treat authored instrumentation tests as passed until the emulator job reports success.
