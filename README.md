# SYNTRA Ecosystem

<p align="center">
  <img src="https://github.com/dea78799-dotcom/Hinh-anh/blob/main/file_0000000076408209a2b7b2c33b416b28.png?raw=true" alt="SYNTRA company logo" width="220">
</p>

<p align="center">
  <strong>A unified software ecosystem by SYNTRA</strong><br>
  Building useful, consistent, and reliable software products.
</p>

---

## About SYNTRA

SYNTRA is a software development initiative focused on building a connected ecosystem of applications and digital services. Our goal is to make each product useful on its own while keeping a consistent experience across the ecosystem.

This repository is the central starting point for the SYNTRA Ecosystem: its architecture, product directory, development roadmap, and shared standards.

## Products

**Current catalogue reviewed:** 2026-11-26. See [Product Directory](docs/products.md) for detailed status, scope, and limitations. Syntra Files is discontinued and excluded from the active product list.

| Product | Purpose | Current status |
|---|---|---|
| **SYNTRA OS** | Operating-system project and primary development focus | In development |
| **SYNTRA NEXUS** | Local-first Android device-management prototype | Prototype 0.11.37-dev; production transport and remote-control capabilities not yet available |
| **SYNTRA VPN** | Android VPN client and cloud control plane | Engineering foundation / Android prototype; production VPN gateway not verified |
| **SYNTRA VM** | Cloud virtual-machine platform | Development sandbox; Phase 1 billing foundation |
| **SYNTRA SOCIAL** | Mobile-first social platform | Prototype 2.56; not production-ready |
| **SYNTRA Website** | Public ecosystem website | Maintained; version 2.15 |
| **SYNTRA Discord Bridge / AUTO-BOT** | Discord community bot and backend bridge | Operational infrastructure; runtime state must be verified from live logs |
| **SYNTRA Ecosystem / SYNTRA GROUP** | Shared governance and documentation | Maintained central ecosystem layer |

Syntra Files is discontinued and retained only as a historical project. Other products may be introduced when the current products are sufficiently stable. Planned ideas are not considered released products.

## Ecosystem principles

- **Consistent identity:** use the official SYNTRA brand identity across products.
- **Usability first:** prioritize clear interfaces and practical features, including mobile-friendly experiences where appropriate.
- **Quality before release:** test changes, record defects, and resolve critical issues before publishing updates.
- **Transparent versions:** document releases and meaningful changes.
- **Security and privacy:** avoid committing passwords, access tokens, private keys, or personal information.

## Repository structure

The repository will grow in stages. The intended structure is:

```text
SYNTRA-Ecosystem/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   ├── feature_request.yml
│   │   └── config.yml
│   ├── pull_request_template.md
│   └── release_template.md
├── README.md
├── CHANGELOG.md
├── LICENSE
└── docs/
    ├── architecture.md
    ├── products.md
    ├── roadmap.md
    ├── development-standards.md
    ├── quality-assurance.md
    ├── release-management.md
    ├── security-policy.md
    ├── contribution-guide.md
    ├── branding-guidelines.md
    └── project-governance.md
```

Files and folders will be added as their contents are ready; this outline does not mean every component has been implemented.

## Development Dashboard

- [SYNTRA Development Dashboard](docs/development-dashboard.md) — progress overview and current tracking limitations.
- [SYNTRA Version Tracker](docs/version-tracker.md) — product versions, release channels, revisions, and release records.
- [SYNTRA Task Registry](docs/task-registry.md) — prioritized tasks and GitHub issue links.
- [Release notes template](.github/release_template.md) — consistent release announcements.
- [Repository Checks workflow](.github/workflows/repository-checks.yml) — automated structural and local-link checks.

## Documentation

- [Ecosystem architecture](docs/architecture.md)
- [Product directory](docs/products.md)
- [Development roadmap](docs/roadmap.md)
- [Development standards](docs/development-standards.md)
- [Quality assurance standard](docs/quality-assurance.md)
- [Release management standard](docs/release-management.md)
- [Security policy](docs/security-policy.md)
- [Contribution guide](docs/contribution-guide.md)
- [Branding guidelines](docs/branding-guidelines.md)
- [Project governance](docs/project-governance.md)
- [Issue tracking standard](docs/issue-tracking.md)
- [Changelog](CHANGELOG.md)
- [Repository rights notice](LICENSE)

## Development workflow

1. Define the scope and acceptance criteria for a change.
2. Implement the change in the appropriate project or documentation.
3. Review the code and check for errors.
4. Test the result on the intended device or environment.
5. Record the result and update the relevant documentation.
6. Publish only after the change passes its required checks.

### Status definitions

- **In development** — implementation is ongoing.
- **Testing** — the feature or product is being tested.
- **Upgrading** — existing functionality or design is being improved.
- **Released** — a usable version has been published.
- **On hold** — work is paused.
- **Discontinued** — active development has ended; repository and history may be retained for reference.

## Roadmap

- [x] Document the overall ecosystem architecture.
- [x] Create the official product directory.
- [x] Define shared design and development standards.
- [x] Establish versioning and changelog practices.
- [ ] Plan a unified product and download website.
- [ ] Add quality checks for each product release.

Roadmap items are plans, not claims that the work is already complete.

## Project status

**SYNTRA Ecosystem maintains shared direction and project documentation across multiple development stages.** Individual product maturity varies; prototype, operational infrastructure, and released products must not be conflated. Syntra Files was discontinued on 2026-11-26 and is not an active product.

---

<p align="center">
  <strong>SYNTRA</strong><br>
  <sub>One ecosystem. Multiple products.</sub>
</p>
