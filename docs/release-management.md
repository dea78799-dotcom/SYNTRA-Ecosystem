# SYNTRA Release Management

**Document version:** 1.0.0  
**Status:** Initial standard  
**Applies to:** SYNTRA OS, Syntra Files, and future SYNTRA products

This document defines how SYNTRA versions, prepares, approves, publishes, and maintains product releases. A documentation version is not a software release, and a planned release is not considered published until the release artifacts are available.

## 1. Release principles

- Publish only builds that have passed the applicable quality gates.
- Keep release artifacts and source changes traceable to a version and commit.
- Communicate known issues and compatibility limits.
- Never describe an untested build as stable.
- Preserve a recovery path appropriate to the product and its data.

## 2. Version numbering

Use Semantic Versioning where a product has a stable public interface:

`MAJOR.MINOR.PATCH`

- **MAJOR:** incompatible changes to established behavior or interfaces.
- **MINOR:** new backward-compatible functionality.
- **PATCH:** backward-compatible fixes and maintenance.

Examples: `1.0.0`, `1.1.0`, `1.1.1`.

Before the first stable release, use a prerelease identifier, such as:

- `0.1.0-alpha.1`
- `0.2.0-beta.1`
- `1.0.0-rc.1`

The project owner approves version numbers. Do not increment a product version simply because a documentation file changed.

## 3. Release channels

| Channel | Purpose | Expectations |
|---|---|---|
| Development | Active implementation builds | May be incomplete; internal use |
| Alpha | Early feature validation | Limited scope; known issues expected |
| Beta | Broader testing and feedback | Main workflows should work; issues documented |
| Release Candidate (RC) | Final pre-release verification | No known release-blocking defects |
| Stable | Public recommended release | Required QA gates passed and release approved |
| Hotfix | Urgent correction to a published version | Focused fix with regression checks |

A channel label must match the actual test evidence. Do not label an untested build as Beta, RC, or Stable.

## 4. Release lifecycle

1. **Plan:** define scope, target platforms, acceptance criteria, and owner.
2. **Build:** produce the candidate from a known source revision.
3. **Review:** inspect code, configuration, dependencies, and documentation.
4. **Test:** complete applicable checks in `docs/quality-assurance.md`.
5. **Triage:** resolve release-blocking defects and document remaining issues.
6. **Approve:** record the release decision and approver.
7. **Publish:** upload artifacts and publish release notes.
8. **Monitor:** collect reports and decide whether a hotfix or rollback is needed.

## 5. Release checklist

- [ ] Product name and version are correct.
- [ ] Release scope and supported environments are documented.
- [ ] Source revision or commit is recorded.
- [ ] Required QA gates are GREEN.
- [ ] No unresolved S0 or S1 defect remains.
- [ ] Remaining known issues are documented.
- [ ] Permissions and data-handling behavior have been reviewed.
- [ ] Release notes and changelog are accurate.
- [ ] Artifact integrity information is recorded where available.
- [ ] Backup, recovery, or rollback plan is documented where applicable.
- [ ] Release owner has approved publication.

Do not publish if any required item is incomplete. Record justified N/A items with a reason.

## 6. Release notes

Use the repository template at [`.github/release_template.md`](../.github/release_template.md) when preparing a GitHub release. Complete every applicable section and remove unused placeholders before publishing.



Every published release should state:

- Product name and version.
- Release channel and publication date.
- Summary of changes.
- Added, changed, and fixed functionality.
- Known issues and limitations.
- Supported platforms and requirements.
- Upgrade or migration notes.
- Recovery or rollback guidance, where applicable.
- Source revision and artifact checksums, when available.

Do not include passwords, tokens, private keys, personal information, or confidential debugging data.

## 7. Rollback and recovery

A rollback plan must be product-specific.

- Identify the last known-good version.
- Preserve user data before migration or update when the product supports it.
- Document how to stop or reverse a faulty update.
- Verify that recovery does not silently delete or corrupt user files.
- Record the incident, affected versions, and corrective action.

If safe rollback is not technically supported, state that limitation clearly and document an alternative recovery procedure before release.

## 8. Hotfix policy

A hotfix should address a specific issue with the smallest practical change.

- Link the hotfix to the reported defect.
- Reproduce the issue where possible.
- Run targeted tests and relevant regression checks.
- Increment the PATCH version when using Semantic Versioning.
- Publish clear notes describing the correction and any remaining risk.

## 9. Product status versus release status

Product development status and release channel are separate concepts.

For example, a product may be **In development** while producing internal Development builds. A product marked **Testing and upgrading** is not automatically a Beta or Stable release.

Current product records:

- SYNTRA OS: In development.
- Syntra Files: Testing and upgrading.

No stable release is implied by this document.

## 10. Release record template

```text
Product:
Version:
Channel:
Release date:
Source commit:
Supported platforms:

Summary:
Added:
Changed:
Fixed:
Known issues:

QA report:
Required gates:
Release decision:
Approver:

Artifact location:
Checksum:
Upgrade notes:
Rollback / recovery notes:
```

## 11. Maintenance

Review this policy when product delivery, platform support, update mechanisms, or recovery capabilities change. Record policy changes in the repository changelog.
