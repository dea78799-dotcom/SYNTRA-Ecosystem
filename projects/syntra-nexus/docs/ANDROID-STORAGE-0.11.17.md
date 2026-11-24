# Android Storage Recovery Verification — 0.11.17-dev

## Changes

- All load, save and clear operations in `EncryptedPairingTransactionPersistence` now use one shared process lock. Android's AtomicFile documentation explicitly requires callers to serialize access.
- Added Android instrumentation tests using the real Android Keystore and Android `AtomicFile` APIs.
- Added emulator execution to the NEXUS Android GitHub Actions workflow (API 35, Google APIs, x86_64).

## Instrumentation coverage

1. Save a snapshot and reopen it through a new persistence instance; verify the decrypted value and fixed 200-byte ciphertext.
2. Modify ciphertext and verify AES-GCM authentication fails while the file remains present.
3. Delete the dedicated Android Keystore alias while encrypted data remains; verify loading fails closed and the ciphertext is unchanged.
4. Stage an AtomicFile backup and a partial replacement; verify `openRead()` restores the previous committed snapshot.

## Commands

From `projects/syntra-nexus`:

```bash
gradle :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
gradle :app:assembleDebug
```

## Important limits

- These tests require an Android emulator or physical device; they are not plain JVM unit tests.
- Emulator success is not proof of behavior on every manufacturer's Keystore implementation.
- Real hardware-backed key invalidation, OEM-specific behavior and power-loss testing still require device-specific verification.
- No real LAN socket, TLS handshake, QR bearer-token transmission, screen capture or remote-control operation is introduced.
- A missing or invalid key is an error, never a reason to silently reset the encrypted transaction file.

## CI status

The workflow is configured to execute the Android instrumentation suite. A passing result must be read from the actual GitHub Actions run; code upload alone is not a test result.
