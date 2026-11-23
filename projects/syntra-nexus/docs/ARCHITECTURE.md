# SYNTRA NEXUS — Architecture 0.1

## Product boundary

NEXUS is an opt-in device management system. A device is manageable only after its owner or authorized user approves pairing and grants specific capabilities. No stealth monitoring or bypass of Android permissions is part of the design.

## Local-first topology

```text
Android Controller
       |
  Pairing + Trust
       |
 Local Device Registry
       |
 LAN Discovery / Secure Session
       |
 Approved NEXUS Agent (future)
```

The Android application is the first controller. A TV client is deferred until the target TV platform is selected. The MVP does not depend on cloud services.

## Components

- **Controller UI:** device dashboard, pairing entry point, permissions and connection state.
- **Pairing:** QR-assisted exchange of a short-lived pairing request, followed by explicit approval on both devices.
- **Trust store:** locally stored device identity and revocable pairing state. Future secrets must use Android Keystore-backed protection.
- **LAN transport:** future discovery through Android Network Service Discovery (mDNS/DNS-SD) and an authenticated TLS channel.
- **Device agent:** future companion installed on each participating device. It exposes only explicitly granted actions.
- **Capability policy:** allow-list of operations per paired device; deny by default.

## Pairing protocol direction

1. Controller creates a short-lived pairing request with a random nonce.
2. The receiving device scans the QR or enters a one-time code.
3. Both devices display the device identity and requested capabilities.
4. Each side explicitly approves.
5. Devices establish an authenticated session and persist the pairing.
6. Either side can revoke the relationship.

Do not place long-lived secrets in QR codes. Do not trust a device based only on its IP address or local network presence.

## Network behavior

- MVP 0.1 is a UI-only prototype and performs no network discovery.
- MVP 0.2 may add local discovery on the same Wi-Fi network.
- Discovery results are untrusted until authenticated pairing completes.
- Connections must fail closed when authentication or authorization fails.
- No port forwarding or public Internet exposure is required for the local-first milestone.

## Android constraints

Android does not allow a normal app to silently control arbitrary apps or inspect private app data. Remote capabilities must use public Android APIs, explicit runtime permissions, or a companion app's own supported interface. Accessibility services must not be used as a general-purpose hidden-control bypass.

## TV strategy

Keep the protocol and capability model platform-neutral. Implement a TV client only after confirming whether the target is Android TV / Google TV, Samsung Tizen, LG webOS, or a TV box. Different platforms expose different APIs and permissions.

## Milestones

- **0.1 Foundation:** Compose dashboard and truthful prototype states.
- **0.2 Pairing:** mutual approval and local trust registry.
- **0.3 LAN Link:** authenticated discovery and connection status.
- **0.4 Capabilities:** small, safe allow-list of device actions.
- **0.5 TV Client:** platform-specific client after target selection.
