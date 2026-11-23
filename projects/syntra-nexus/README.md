# SYNTRA NEXUS

**Version:** 0.1.0-dev  
**Status:** Foundation / prototype  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is a device-management concept for devices that the user owns or is authorized to manage. The first milestone is an Android application shell with a local dashboard. TV support is intentionally deferred until the target TV platform is known.

## MVP 0.1 scope

- Android dashboard shell.
- Device list and connection-state presentation.
- Clear pairing entry point (pairing transport is not implemented yet).
- Local-first architecture; no cloud account or remote-control service.
- Explicitly label demo devices as sample data.

## Not implemented yet

- QR pairing and mutual approval.
- Device discovery or LAN transport.
- Remote actions.
- TV client.
- Cloud sync.
- Access to another person's private files, messages, screen, or app activity.

## Build

Open this directory as an Android project in Android Studio, or build it with Gradle 8.9 and JDK 17:

```bash
gradle :app:assembleDebug
```

The Android app uses Kotlin and Jetpack Compose. The current dashboard is a UI prototype, not a functioning device controller.

## Security baseline

Pairing must require explicit confirmation on both devices. Every future remote action must be permission-scoped, visible to the device owner, and revocable. Never store passwords, pairing secrets, or private keys in source control.
