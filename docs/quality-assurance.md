# SYNTRA Quality Assurance (QA)

**Document version:** 1.0.0  
**Status:** Initial standard  
**Applies to:** SYNTRA OS, Syntra Files, and future SYNTRA products

This document defines a shared quality-assurance process. A status of PASS means the checks listed for that change were completed and recorded; it does not imply that every device, configuration, or use case has been tested.

## 1. QA objectives

- Detect defects before a change is published.
- Protect user data, device stability, and application reliability.
- Keep test results reproducible and traceable.
- Make release decisions based on evidence rather than assumptions.

## 2. Quality gates

Every change should pass the gates relevant to its scope.

| Gate | Required check | PASS condition |
|---|---|---|
| G1: Scope | Requirements and acceptance criteria | Expected behavior is written down |
| G2: Review | Code, configuration, and documentation review | No known critical issue remains |
| G3: Functional | Main feature and regression checks | Required scenarios behave as expected |
| G4: Compatibility | Supported devices, OS versions, and screen sizes | Tested target environments are recorded |
| G5: Security | Permissions, data handling, and dependency review | No unresolved high-risk finding |
| G6: Release | Version, changelog, and rollback plan | Release record is complete |

A gate may be marked **N/A** only with a short explanation. Do not mark a gate PASS when it was not tested.

## 3. Result statuses

- **GREEN / PASS:** Required checks completed successfully; no release-blocking defect found.
- **RED / FAIL:** A required check failed or a release-blocking defect exists.
- **YELLOW / NEEDS REVIEW:** Evidence is incomplete, a non-blocking issue remains, or a decision is required.
- **N/A:** The check does not apply, with a recorded reason.

Overall status is RED if any required gate is RED. Otherwise it is YELLOW if any required gate is YELLOW. It is GREEN only when all required gates are PASS or justified N/A.

## 4. Defect severity

| Severity | Meaning | Expected action |
|---|---|---|
| S0 – Critical | Data loss, serious security exposure, or product unusable | Stop release; investigate immediately |
| S1 – High | Core workflow broken or major instability | Fix before release |
| S2 – Medium | Important feature impaired; workaround may exist | Fix before release where practical; owner approval required to defer |
| S3 – Low | Minor visual, wording, or low-impact issue | Record and schedule |

Never include passwords, access tokens, private keys, or personal data in bug reports, screenshots, or logs.

## 5. Standard test workflow

1. Record product, version/build, device, OS version, and test date.
2. State the feature or change being tested.
3. List prerequisites and exact reproduction steps.
4. Record expected and actual results.
5. Capture safe evidence, removing personal or secret information.
6. Assign severity and status to each defect.
7. Retest the fix and run relevant regression checks.
8. Record the final gate results and release decision.

## 6. Product-specific test areas

### SYNTRA OS

- Installation, startup, shutdown, and recovery behavior.
- Core navigation and system settings.
- App and service compatibility within the declared scope.
- Permission boundaries and protected data handling.
- Resource usage, responsiveness, and stability.
- Update, interruption, and rollback behavior where implemented.

Only test capabilities that exist in the current build. Mark unimplemented areas as NOT IMPLEMENTED, not PASS.

### Syntra Files

- File and folder browsing, sorting, and search.
- Opening supported file types with the correct handler.
- Create, rename, copy, move, and delete operations.
- Permission-denied, missing-file, and insufficient-storage handling.
- Large directories, long filenames, and unusual characters.
- Image and video preview behavior where supported.
- APK or app-package handling: clearly distinguish viewing package information from installing; installation must use the platform's normal user-confirmed system flow.
- Recovery from interrupted operations and safe handling of user files.

## 7. Test case template

Use this structure for each test:

| Field | Value |
|---|---|
| Test ID | QA-XXX |
| Product / version | Product name and build |
| Environment | Device and OS version |
| Preconditions | Required setup |
| Steps | Reproducible actions |
| Expected result | Intended behavior |
| Actual result | Observed behavior |
| Status | PASS / FAIL / NEEDS REVIEW / N/A |
| Evidence | Safe screenshot or log reference |
| Tester / date | Responsible person and date |

## 8. Release decision

A release candidate may proceed only when:

- All release-required gates are GREEN.
- No unresolved S0 or S1 defect remains.
- Known S2/S3 issues are documented with an owner and decision.
- Version and changelog entries are accurate.
- Backup, recovery, or rollback considerations are documented where applicable.
- The release owner approves the result.

If any required gate is RED, do not publish the release candidate. If evidence is incomplete, keep the status YELLOW and continue testing.

## 9. QA record template

```text
Product:
Version / build:
Test date:
Device / OS:
Change under test:

G1 Scope: PASS / FAIL / NEEDS REVIEW / N/A
G2 Review: PASS / FAIL / NEEDS REVIEW / N/A
G3 Functional: PASS / FAIL / NEEDS REVIEW / N/A
G4 Compatibility: PASS / FAIL / NEEDS REVIEW / N/A
G5 Security: PASS / FAIL / NEEDS REVIEW / N/A
G6 Release: PASS / FAIL / NEEDS REVIEW / N/A

Defects:
Overall status:
Release decision:
Evidence:
```

## 10. Product evidence record

Use the [Product Build and QA Evidence Template](product-evidence-template.md) for each build or test session. Attach verifiable build identifiers, source revisions, test environments, results, and safe evidence links. Keep missing information marked as **Not recorded** and keep the overall status YELLOW until required evidence exists.

## 10. Maintenance

Review this standard when product architecture, supported platforms, data handling, or release processes change. Update this document and record the change in the repository changelog.
