# Explicit capability consent coordinator — 0.11.23-dev

## Added

- Add a consent coordinator that validates an incoming capability request against the exact pairing request ID, transcript hash, responder fingerprint, transcript expiry, current PAIRED handshake session and active trusted-device record.
- Require the peer's role-bound ECDSA/SHA-256 signature to verify before a pending consent object can be created.
- Revalidate all bindings and expiry immediately before local approval, so a request that expires or loses trusted-device authorization while the dialog is open cannot be approved.
- Make the local-signature callback lazy: the coordinator invokes it only from the explicit approve() call. Merely preparing or displaying a consent request does not sign or grant anything.
- Add a DeviceIdentityStore method that signs the exact canonical capability request only when the local key matches the assigned transcript role.
- Keep the grant registry as the final enforcement point: it rechecks both signatures and trusted-device authorization, then commits the encrypted grant before changing memory.
- Add JVM tests for valid peer approval, explicit local signing, invalid peer signature rejection, expiry between display and approval, and failed durable revocation.

## Security boundary

- This is not a live remote consent flow. No authenticated incoming request transport exists yet, so no network peer can currently populate the consent dialog.
- A future caller must obtain the request over the already pinned mutual-TLS peer session, bind it to the exact pairing transcript and only then call prepare().
- The current role mapping remains local INITIATOR and peer RESPONDER. Do not enable role-symmetric or multi-peer operation without a separate review.
- The local signer callback must be invoked only from the visible Allow action. Deny or dismiss must not call it.
- No sockets, file transfer, screen capture, screen transmission, remote actions or TV control are enabled.

## Verification

The code and tests are committed to GitHub. No successful CI result or APK build is claimed until the latest workflow run reports success. The prior emulator startup issue remains a separate environment risk; the workflow now separates debug assembly from emulator instrumentation.
