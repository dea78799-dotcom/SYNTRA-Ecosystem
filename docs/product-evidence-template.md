# SYNTRA Product Build and QA Evidence Template

Use one copy of this record for each product build or test session. Complete only fields supported by direct evidence. Write **Not recorded** when information is unavailable; do not infer versions, commits, device compatibility, or test results.

## 1. Build identity

- **Product:** SYNTRA OS / Syntra Files
- **Product repository:**
- **Build or version:** Not recorded
- **Source commit / revision:** Not recorded
- **Build date:** Not recorded
- **Build artifact URL:** Not recorded
- **Artifact integrity hash:** Not recorded
- **Build procedure / workflow URL:**

## 2. Test environment

- **Device / model:**
- **Operating system and version:**
- **Screen size / architecture (if relevant):**
- **Installation method:**
- **Tester:**
- **Test date and timezone:**

## 3. Scope

- **Features included in this build:**
- **Features not implemented:**
- **Acceptance criteria:**

## 4. QA results

| Gate | Result (PASS / FAIL / NEEDS REVIEW / N/A) | Evidence or reason |
|---|---|---|
| G1 — Scope | Not tested | |
| G2 — Review | Not tested | |
| G3 — Functional | Not tested | |
| G4 — Compatibility | Not tested | |
| G5 — Security | Not tested | |
| G6 — Release | Not tested | |

Overall result: **YELLOW — evidence not yet recorded**

### Test cases

| Test ID | Feature / operation | Expected result | Actual result | Status | Evidence link |
|---|---|---|---|---|---|
| QA-001 | | | | NEEDS REVIEW | |

## 5. Defects and limitations

| ID | Severity | Description | Reproduction / evidence | Status |
|---|---|---|---|---|
| | | | | |

Do not include credentials, private keys, personal data, or sensitive user files in evidence.

## 6. Review and decision

- **Known issues:**
- **Retest performed:**
- **Reviewer / owner:**
- **Decision:** Continue development / More testing / Approved for release / Rejected
- **Decision date:**
- **Related issue / pull request:**

## 7. Product-specific test checklist

### SYNTRA OS
- Startup and recovery behavior
- Core navigation and settings
- Supported services and application compatibility
- Permissions and protected data handling
- Responsiveness and stability
- Update and rollback behavior, if implemented

Mark unimplemented capabilities as **NOT IMPLEMENTED**, not PASS.

### Syntra Files
- Browse folders and files
- Search and sorting
- Open supported file types
- Create, rename, copy, move, and delete
- Handle missing files and permission errors
- Image and video preview, if supported
- Package information and user-confirmed installation handoff, if supported
- Recovery from interrupted operations

A checklist item is not a test result. Record actual steps, observations, and evidence before assigning PASS.
