# SYNTRA Contribution Guide

**Document version:** 1.0.0  
**Status:** Initial contributor guide  
**Applies to:** Contributions to SYNTRA repositories and products

This guide explains the expected workflow for proposing documentation, code, fixes, and product improvements. Repository-specific instructions take priority when they are more specific.

## 1. Before contributing

- Read the repository README and relevant project documentation.
- Check existing issues and pull requests to avoid duplicating work.
- Confirm that the change fits the project's scope and roadmap.
- Do not begin work that requires access you have not been granted.
- Ask the project owner for clarification when requirements are unclear.

## 2. Contribution types

Contributions may include:

- Bug reports and reproducible test cases.
- Documentation corrections and translations.
- Code improvements and reviewed feature proposals.
- Accessibility, usability, and performance improvements.
- Test coverage and quality-assurance reports.
- Dependency and security maintenance.

A proposal is not automatically accepted. The project owner may request changes, defer it, or decline it.

## 3. Branch and change workflow

Use the following workflow when the repository supports branches and pull requests:

1. Start from the latest approved base branch.
2. Create a short, descriptive working branch.
3. Make one focused change per branch where practical.
4. Run relevant checks and inspect the final diff.
5. Open a pull request with a clear summary and test evidence.
6. Address review feedback before merging.

Do not force-push, rewrite shared history, or merge directly into a protected branch unless the repository owner has explicitly authorized it.

## 4. Commit message convention

Use a short, imperative commit subject. Suggested prefixes:

| Prefix | Purpose |
|---|---|
| `feat:` | Add a user-facing feature |
| `fix:` | Correct a defect |
| `docs:` | Add or update documentation |
| `refactor:` | Restructure code without intended behavior change |
| `test:` | Add or update tests |
| `build:` | Change build or packaging configuration |
| `chore:` | Routine maintenance |

Examples:

- `docs: clarify installation steps`
- `fix: handle missing file metadata`
- `test: cover invalid configuration input`

Do not include passwords, tokens, private user data, or confidential details in commit messages.

## 5. File and naming conventions

- Use clear, descriptive names that match the surrounding repository.
- Follow the language and framework conventions already used by the project.
- Avoid unnecessary renames or unrelated formatting changes.
- Keep documentation headings, links, and examples consistent.
- Do not add generated build output, temporary files, personal backups, or local secrets unless the repository explicitly requires them.
- Use the repository's existing formatting and lint configuration when available.

## 6. Code quality expectations

- Prefer readable, maintainable code over clever or compressed implementations.
- Keep functions and modules focused.
- Validate external input and handle expected errors.
- Avoid unnecessary permissions and dependencies.
- Preserve existing behavior unless the change intentionally modifies it.
- Add or update tests for changed behavior where practical.
- Document non-obvious decisions and public interfaces.
- Do not claim a feature works unless it has been tested in the stated environment.

## 7. Pull request or change proposal

A contribution proposal should state:

- **Purpose:** What problem does this change address?
- **Changes:** What files or behaviors are affected?
- **Testing:** What checks were run, and on which environment?
- **Risks:** Are there compatibility, data, permission, or migration concerns?
- **Screenshots:** Include only when they help explain a UI change; remove personal and sensitive information.

Keep the proposal focused. Split unrelated work into separate changes.

## 8. Bug reports

A useful bug report includes:

- Product and version.
- Device, operating system, and relevant environment details.
- Steps to reproduce.
- Expected behavior.
- Actual behavior.
- Frequency and impact.
- Sanitized logs or screenshots, if useful.

Do not include passwords, access tokens, private files, personal identifiers, or another person's data.

## 9. Security-sensitive contributions

- Follow the [SYNTRA Security Policy](security-policy.md).
- Report suspected vulnerabilities privately.
- Do not publish exploit code, secrets, or sensitive reproduction details in public issues or pull requests.
- Do not test against systems or accounts without explicit authorization.
- Do not add telemetry, data collection, or permission requests without documenting their purpose and obtaining project approval.

## 10. Product-specific considerations

### SYNTRA OS

Changes should consider system stability, supported hardware and environments, permission boundaries, update integrity, and recovery behavior. Clearly identify the target build or environment used for testing.

### Syntra Files

Changes should consider file integrity, safe file operations, permission handling, supported file types, and the risk of accidental data loss. Test with sample data and avoid using private user files.

These are review expectations; they do not imply that every listed control is already implemented.

## 11. Review and acceptance

The project owner or designated reviewer may evaluate:

- Alignment with requirements and roadmap.
- Correctness and maintainability.
- Test evidence and regression risk.
- Security and privacy impact.
- Documentation and compatibility.
- Whether the change is appropriately scoped.

A contribution is accepted only after the authorized maintainer approves and merges it.

## 12. Contributor conduct

- Communicate respectfully and keep discussions focused on the work.
- Give accurate information about testing and limitations.
- Respect project ownership, third-party licenses, and user privacy.
- Do not impersonate maintainers or claim unauthorized approval.
- Do not upload content you do not have permission to share.

## 13. Current project status

SYNTRA OS remains **In development**. Syntra Files remains **Testing and upgrading**. Contributions and documentation do not, by themselves, indicate that either product is ready for stable release.

## 14. Maintenance

This guide may be updated as repository workflows, team roles, and product requirements evolve. Check the current version before contributing.
