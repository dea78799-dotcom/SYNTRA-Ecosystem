# SYNTRA NEXUS — Device Capabilities (Proposed)

## 1. Daily activity overview

The paired device may voluntarily grant access to Android Usage Access. The controller dashboard should show:

- Screen-on / screen-off session timeline, based on available system events.
- Daily screen-on total.
- App usage totals, with optional category grouping.
- Current foreground app only when the device grants usage access and Android's data is available; represent the state as **Online / active app** only when data is fresh.
- A separate status for **Offline / data unavailable / permission revoked**. Never treat stale data as current.
- Daily game-use total derived from an explicit, user-reviewed game category list; users can remove apps from that list.
- No keystroke, message, private-app content, notification content, or covert capture.

Android UsageStats is permission-gated through Settings. Screen activity history may be incomplete across reboot, OS versions, or permission changes; reports must disclose that.

## 2. Screen sharing (optional, user-initiated)

Use Android MediaProjection only after the device owner taps **Start screen sharing** and accepts the system confirmation. The sharing device must show an ongoing foreground-service notification and an in-app sharing indicator with a clear **Stop** control. Stop automatically on lock, session timeout, disconnect, or revocation where practical. Do not persist or record video by default. The controller must display that a live stream is being shared. This capability is for a consensual support session, not stealth monitoring.

## 3. Website and content restrictions

A future optional local filtering module may use Android VPNService with a transparent, user-visible local DNS/domain filter. It should support:

- User/guardian-configured domain blocklists and allowlists.
- Adult-domain category blocking from a maintained, reputable list; list source and update date visible.
- Safe-search guidance/configuration where supported by a service.
- A local keyword filter only inside NEXUS features that process text (e.g., search labels or app names). It must not inspect HTTPS page contents, messages, keyboard input, or private app data.
- A clear VPN indicator, filtering status, settings, and an off switch.
- Local processing by default; no browsing-history upload.
- Avoid false-positive lockouts: show a reason, provide an appeal/temporary unblock flow appropriate to the device owner or guardian, and allow essential services through.
- Filtering cannot guarantee that all adult material is blocked. DNS/domain filtering cannot see the full path of encrypted websites and cannot reliably filter content within apps.

## 4. Consent, roles, and safety

- Pairing is mutual and capability-based. A paired device does not automatically receive every permission.
- Each feature has a separate grant and can be revoked at the managed device.
- Screen sharing requires a fresh OS consent flow and a visible active state.
- Activity summaries should be aggregate and visible on the monitored device.
- Child/teen accounts require a responsible guardian's participation for family controls; NEXUS must not enable hidden monitoring or coercive control.
- A controller must not bypass Android permissions, accessibility protections, or device-owner restrictions.

## 5. Implementation sequence

- **0.9.0-dev:** encrypted local token persistence (AES-GCM; Android Keystore key; AtomicFile) and registry integration with QR issuance.
- **0.10:** persistent registry tests and CI verification; connect the redemption protocol only after LAN transport and mutual-authentication review.
- **0.11:** opt-in activity summary using UsageStats and screen-state events; no remote activity feed until the pairing/capability layer is real.
- **0.12:** consented MediaProjection screen-sharing prototype, foreground indicator, stop controls and timeout; do not record by default.
- **0.13:** local DNS/domain filtering prototype with transparent VPN UI, domain lists, test fixtures, false-positive review and privacy documentation.
