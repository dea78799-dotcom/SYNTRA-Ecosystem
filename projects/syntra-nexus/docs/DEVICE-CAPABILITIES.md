# SYNTRA NEXUS — Device Capabilities

## 1. Daily activity overview — 0.10 prototype

The current local Activity Monitor requests no runtime dialog; the user must open Android's Usage Access settings and explicitly enable SYNTRA NEXUS. It currently:

- Reads today's per-app foreground duration through UsageStats.
- Reads SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE events on supported Android versions and estimates today's screen-interactive duration.
- Displays the six apps with the highest foreground time.
- Keeps the report on the same device. No LAN transfer or cloud upload exists.
- Shows a missing-data state when the event history is unavailable.

Limitations: Android may omit screen events after reboot or due to OS/vendor behavior. UsageStats totals are not a precise screen-on measurement. The prototype does not identify games automatically, does not guarantee a real-time current-app signal, and does not label another device online. A future game total must use a user-reviewed app list. Permission can be revoked at any time in Settings.

## 2. Screen sharing — 0.11 consent foundation

The Android prototype now provides a **Xin quyền** action that opens the system MediaProjection confirmation. The user must accept it. On Android 13+, notification permission is also required so the session has a visible notification. The non-exported foreground service holds the granted projection session, displays an ongoing notification with a **Dừng** action and ends automatically after 30 minutes.

Important limitations of 0.11:

- This is a permission-flow foundation only. It does not create a VirtualDisplay, capture frames, record video or transmit screen content.
- No remote viewer exists because authenticated LAN pairing and transport are not implemented.
- The user can stop from the app or the ongoing notification; Android can also revoke the projection.
- No background or hidden capture is performed. Actual live streaming requires a later milestone with mutual device authentication, a visible sharing state on both devices and an immediate stop control.

## 3. Website and content restrictions (future 0.12)

A future optional local filtering module may use Android VPNService with a transparent, user-visible local DNS/domain filter. It should support user/guardian-configured domain blocklists and allowlists, adult-domain categories from a maintained list with source/update date, Safe Search guidance where supported, visible VPN state and an off switch. A keyword filter is limited to NEXUS-owned text fields; it must not inspect HTTPS page contents, messages, keyboard input or private app data.

DNS/domain filtering cannot see full encrypted URL paths, cannot reliably filter content inside apps and cannot guarantee that all adult material is blocked. Filtering should be locally processed by default, avoid browsing-history uploads and provide a clear false-positive/unblock process appropriate to the device owner or guardian.

## 4. Consent, roles, and safety

- Pairing is mutual and capability-based. Pairing does not automatically grant Usage Access, screen capture or filtering.
- Each feature has a separate grant and can be revoked on the managed device.
- Activity summaries should be visible on the device being summarized.
- Screen sharing requires fresh OS consent and a visible active state.
- Child/teen family controls require responsible guardian participation; NEXUS must not enable hidden monitoring or coercive control.
- Never bypass Android permissions, accessibility protections or device-owner restrictions.
- Do not collect messages, keystrokes, notification contents, screen recordings or private app data.

## 5. Implementation sequence

- **0.9.0-dev:** encrypted local token persistence and local QR-issuer registration.
- **0.10.0-dev:** local Activity Monitor using UsageStats and screen-state events; unit tests for duration aggregation.
- **0.11.0-dev:** explicit MediaProjection consent foundation, foreground notification, stop action and 30-minute timeout; no frame capture or network streaming.
- **0.12:** local DNS/domain filtering prototype with transparent VPN UI, domain lists, test fixtures and false-positive review.
- LAN pairing, mutual authentication, capability grants and remote summaries remain separate milestones; no remote monitoring until these foundations are implemented and tested.
