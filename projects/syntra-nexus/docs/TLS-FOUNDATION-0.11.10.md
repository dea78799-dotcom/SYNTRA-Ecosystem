# SYNTRA NEXUS 0.11.10-dev — TLS identity foundation

## Added

- An X.509 key manager backed by the existing Android Keystore identity alias.
- The local certificate chain is checked for validity, leaf-certificate role, EC public key and digital-signature usage.
- The key manager signs a fresh random challenge and verifies it with the certificate public key. This proves that the local Keystore private key corresponds to the certificate without exporting the private key.
- TLS context construction requires one exact active peer fingerprint and rejects an unknown, revoked or self-referential peer.
- TLS 1.3 is preferred; TLS 1.2 is the minimum accepted protocol. Older protocols are not enabled.
- Server socket configuration requires client certificates, providing the mutual-authentication setting.

## Explicit limits

This is a TLS configuration foundation only. It does not create sockets, advertise a server, connect devices, redeem invitations, exchange application packets, or transmit screen content. The pairing protocol still needs a complete, tested network handshake and replay protection before this context is used in production.

The pinned trust manager validates the exact expected peer identity rather than accepting any device in the local registry. It is an identity-pin policy, not public CA or DNS-name validation. The application must select the intended peer by its approved fingerprint and must not treat an IP address or NSD record as identity.

No build, unit-test execution, physical-device test or APK publication is claimed by this change.
