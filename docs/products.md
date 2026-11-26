# SYNTRA Ecosystem — Product Directory

> Document status: Reviewed product catalogue  
> Version: 1.1.0  
> Last updated: 2026-11-26

This document is the central directory for SYNTRA projects. It records purpose, status, scope, and verified maturity. A listing does not mean a product is publicly released, production-ready, or available for download.

Status is based on Founder confirmation and the latest available repository documentation/code snapshot. Where runtime or device evidence is missing, that limitation is stated explicitly.

## 1. Active project overview

| Project | Category | Primary purpose | Current status |
|---|---|---|---|
| [SYNTRA OS](https://github.com/dea78799-dotcom/SyntraOS) | Operating-system project | Independent SYNTRA operating-system development | Early Development 0.1.0; primary development focus |
| [SYNTRA VPN](https://github.com/dea78799-dotcom/SYNTRA-VPN) | Android application / cloud infrastructure | VPN client, cloud control plane, and VPS gateways | Engineering foundation / Android prototype 0.6.5-dev |
| [SYNTRA VM](https://github.com/dea78799-dotcom/SYNTRA-VM) | Cloud platform | Cloud virtual machines, starting with Android VM | Development sandbox 0.1; Phase 1 Billing Foundation |
| [SYNTRA SOCIAL](https://github.com/dea78799-dotcom/SYNTRA-Social) | Social application | Mobile-first posts, media, profiles, and communities | Prototype 2.56; not production-ready |
| [SYNTRA NEXUS](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/tree/main/projects/syntra-nexus) | Android device ecosystem | Opt-in device pairing and trusted-device management | Local-first prototype 0.11.37-dev |
| [SYNTRA Website](https://dea78799-dotcom.github.io/SYNTRA-Website) | Public website | Official information, changelog, docs, support, and downloads | Maintained; version 2.15 |
| [SYNTRA Discord Bridge / AUTO-BOT](https://github.com/dea78799-dotcom/SYNTRA-Discord-Bridge) | Community infrastructure | Discord assistant, slash commands, and server monitoring | Operational infrastructure; verify live runtime before asserting uptime |
| [SYNTRA Ecosystem / SYNTRA GROUP](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem) | Governance and documentation | Shared direction, standards, catalogue, and project coordination | Maintained central documentation |

## 2. SYNTRA OS

**Status:** In development — Early Development 0.1.0  
**Priority:** Primary development focus

SYNTRA OS is the main operating-system project within the ecosystem. Its direction is a microkernel-based architecture targeting ARM64, x86_64, and RISC-V.

The intended engineering areas include bootloader, kernel, drivers, system services, and user interface. The project README and recorded CI evidence are the source of truth for implemented components and supported environments.

**Limitations:** Early development is not a production OS release. CI/QEMU evidence does not establish compatibility with physical devices or general availability.

Repository: https://github.com/dea78799-dotcom/SyntraOS

## 3. SYNTRA VPN

**Status:** In development — Android prototype / engineering foundation  
**Version reference:** 0.6.5 development

SYNTRA VPN is planned as an Android VPN application with a cloud Control Plane and VPN Gateway nodes hosted on VPS infrastructure. The selected architecture includes WireGuard and OpenVPN.

Current work includes Android client foundations, account/device authentication, Supabase Auth integration, and device registration/listing/revocation APIs.

**Limitations:** WireGuard engine remains disabled pending gateway availability and end-to-end validation. Do not claim production VPN connectivity, completed OpenVPN integration, or commercial availability without fresh test evidence.

Repository: https://github.com/dea78799-dotcom/SYNTRA-VPN

## 4. SYNTRA VM

**Status:** In development — Phase 1 Billing Foundation  
**Version reference:** 0.1 development sandbox

SYNTRA VM is a cloud virtual-machine platform, with Android VM prioritized before PC VM. The selected business model is hybrid:
- Recurring Premium subscriptions.
- SYNTRA VM Credits.
- Additional storage purchases.

The engineering foundation includes billing/catalogue concepts and an immutable credit-ledger direction.

**Limitations:** Do not describe real payment processing, production VM provisioning, metering, or a public commercial service as complete unless the repository and deployment evidence confirm it.

Repository: https://github.com/dea78799-dotcom/SYNTRA-VM

## 5. SYNTRA SOCIAL

**Status:** Prototype in development  
**Version reference:** Prototype 2.56

SYNTRA SOCIAL is a mobile-first social platform for posts, photos, videos, profiles, and communities. The current prototype uses Supabase foundations, including authentication and private media synchronization.

**Limitations:** It is not production-ready and must not be described as publicly launched. End-to-end device testing, privacy, moderation, and safety work remain release requirements.

Repository: https://github.com/dea78799-dotcom/SYNTRA-Social

## 6. SYNTRA NEXUS

**Status:** Android local-first prototype  
**Version reference:** 0.11.37-dev

SYNTRA NEXUS focuses on opt-in device management and trusted pairing, with explicit consent and permission boundaries.

**Limitations:** Production LAN transport, remote control, file transfer, and screen streaming are not confirmed as available. Do not present these capabilities as working features. TV support is deferred.

Project directory: https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/tree/main/projects/syntra-nexus

## 7. SYNTRA Website

**Status:** Maintained public website  
**Version reference:** 2.15

The official website provides public ecosystem information, project status, changelog, documentation, support, and a download centre.

The website's active project catalogue must match this directory. Syntra Files is excluded from active product listings.

Website: https://dea78799-dotcom.github.io/SYNTRA-Website  
Repository: https://github.com/dea78799-dotcom/SYNTRA-Website

## 8. SYNTRA Discord Bridge / AUTO-BOT

**Status:** Operational community infrastructure

The Discord bot and backend bridge support the SYNTRA community assistant, slash commands, server planning, persistent project knowledge, and AUTO-BOT monitoring.

**Operational boundary:** A successful Railway deployment is not by itself proof of continuous uptime or a specific replica count. Use current Railway runtime logs and Discord interaction tests before reporting Online, replica count, or command responsiveness.

Repository: https://github.com/dea78799-dotcom/SYNTRA-Discord-Bridge

## 9. SYNTRA Ecosystem / SYNTRA GROUP

**Status:** Maintained governance and documentation layer

This repository provides shared project direction, architecture, standards, product catalogue, roadmap, and governance. It is not a separate end-user software product.

Repository: https://github.com/dea78799-dotcom/SYNTRA-Ecosystem

## 10. Discontinued project — Syntra Files

**Status:** Discontinued — Founder decision, 2026-11-26

Syntra Files was a former file-management application in the SYNTRA ecosystem. Active development has ended.

- Do not list Syntra Files as an active, in-development, testing, or upgrading product.
- Do not plan new features, fixes, releases, or tests for it unless the Founder explicitly reopens the project.
- Retain its repository, source, changelog, and historical CI records as historical reference.
- Historical build/test results do not imply current support or ongoing development.
- The official SYNTRA Website v2.15 has removed it from the active product catalogue.

Historical repository: https://github.com/dea78799-dotcom/Syntra-Files

## 11. Status definitions

| Status | Meaning |
|---|---|
| Proposed | Idea under evaluation; implementation may not have started |
| In development | Active implementation is underway |
| Prototype / Engineering foundation | Early implementation; capabilities and maturity are limited |
| Operational infrastructure | A service is deployed or used; current runtime health still requires live verification |
| Maintained | Existing public website or shared documentation is being maintained |
| Released | A version has been published with release notes and required QA |
| On hold | Work is paused |
| Discontinued | Active development has ended; history is retained |

## 12. Shared product requirements

Every active SYNTRA product should:
1. Use approved SYNTRA brand assets.
2. Provide clear product information and version identification.
3. Document supported devices, platforms, and limitations.
4. Protect user data and avoid unnecessary permissions.
5. Validate inputs and handle errors safely.
6. Include relevant testing before release.
7. Maintain a changelog for meaningful updates.
8. Avoid claiming unsupported features or unverified compatibility.

## 13. Keeping this directory current

Update this document when a product changes purpose, status, platform support, or release state. Consult each product's own repository for implementation details. Founder decisions about project lifecycle must be reflected promptly in the catalogue, roadmap, dashboard, and version tracker.

---

**Current catalogue:** 8 active projects/operational or governance components, plus 1 discontinued historical project. SYNTRA OS remains the primary product-development focus.
