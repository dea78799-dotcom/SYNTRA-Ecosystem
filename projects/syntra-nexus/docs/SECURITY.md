# SYNTRA NEXUS — Security Baseline

## Required safeguards

- Pairing is visible and requires approval on both devices.
- Each device receives only the minimum capabilities needed.
- The owner can inspect and revoke every pairing.
- No hidden collection of messages, photos, screen content, passwords, or app activity.
- No bypass of Android sandboxing, permissions, or platform restrictions.
- Network sessions must authenticate the peer and use modern TLS.
- Long-lived private keys must be generated and protected with Android Keystore where supported.
- QR pairing payloads contain only short-lived, single-use pairing material.
- Logs must not contain tokens, private keys, message content, or sensitive personal data.
- Sample devices and simulated states must be clearly labeled as demo data.

## Threats to address before LAN pairing ships

- QR code photographed or replayed by an unintended party.
- Rogue device advertising a forged discovery record.
- Reuse of expired pairing requests.
- Unauthorized commands from a paired but restricted device.
- Local network interception or downgrade.
- Lost or shared phones retaining trusted-device access.

## Release gate

Do not label a build as a functional remote controller until pairing, mutual authentication, capability enforcement, revocation, and failure cases have been tested on real devices.
