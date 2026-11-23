# SYNTRA Issue Tracking Standard

**Document version:** 1.0.0  
**Status:** Initial tracking standard  
**Applies to:** SYNTRA Ecosystem, SYNTRA OS, and Syntra Files

This document defines a consistent issue-label vocabulary and a recommended GitHub Projects workflow. It is a configuration specification; labels and a Projects board are not considered active until they have been created and verified in GitHub.

## 1. Issue types

Use one primary type label per issue:

| Label | Meaning |
|---|---|
| `type: bug` | Existing behavior is incorrect |
| `type: feature` | New user-facing capability |
| `type: docs` | Documentation change |
| `type: task` | Planned maintenance or implementation work |
| `type: security` | Security-related work; do not put sensitive vulnerability details in a public issue |

## 2. Priority labels

Use one priority label when impact is understood:

| Label | Meaning |
|---|---|
| `priority: critical` | Immediate security, data-integrity, or essential-operation risk |
| `priority: high` | Major defect or essential capability |
| `priority: normal` | Standard planned work |
| `priority: low` | Optional or cosmetic improvement |

Do not assign a priority based only on urgency wording. Consider impact, likelihood, and affected users.

## 3. Status labels

Use status labels only when they add information beyond the GitHub Projects workflow:

| Label | Meaning |
|---|---|
| `status: triage` | Needs initial review |
| `status: accepted` | Approved for planning |
| `status: in progress` | Work has started |
| `status: blocked` | Cannot proceed due to a dependency |
| `status: needs review` | Ready for maintainer review |

Remove or update a status label when the issue changes state.

## 4. Product and area labels

Suggested product labels:

- `product: ecosystem`
- `product: syntra-os`
- `product: syntra-files`

Suggested area labels:

- `area: ui`
- `area: backend`
- `area: mobile`
- `area: files`
- `area: performance`
- `area: documentation`
- `area: build-release`

Apply only labels that accurately describe the issue. Avoid creating multiple near-duplicate labels.

## 5. Recommended GitHub Projects board

Use a board view with these workflow columns:

1. **Backlog** — accepted work that is not scheduled.
2. **Ready** — requirements are clear and dependencies are understood.
3. **In progress** — active implementation.
4. **Review** — awaiting code, design, or documentation review.
5. **Testing** — changes are being verified.
6. **Done** — acceptance criteria are met and the work is complete.
7. **Blocked** — work cannot proceed; record the blocking reason.

A blocked item should retain its underlying workflow context in its description or activity history.

## 6. Recommended project fields

Where supported by the GitHub Projects configuration, use:

- **Status:** one of the workflow columns above.
- **Priority:** Critical, High, Normal, Low.
- **Product:** Ecosystem, SYNTRA OS, Syntra Files.
- **Target version:** optional release or milestone.
- **Owner:** the person responsible for the next action.

Do not assign an owner or target version without agreement from the relevant maintainer.

## 7. Triage workflow

1. Confirm that the issue is understandable and not a duplicate.
2. Check for sensitive information; move security disclosures to a private reporting channel.
3. Identify the affected product and issue type.
4. Assess impact and set priority.
5. Request missing reproduction details or acceptance criteria.
6. Decide whether to accept, defer, close, or request clarification.
7. Add accepted work to the project board and assign a responsible maintainer when appropriate.

## 8. Completion criteria

An issue may be moved to **Done** when:

- The agreed acceptance criteria are satisfied.
- Relevant tests or checks have been run and recorded.
- Documentation is updated where needed.
- Review is complete where required.
- Known limitations or follow-up work are recorded.

Closing an issue is not proof that an entire product or release is stable.

## 9. Configuration status

This document defines the intended labels and board workflow only. A maintainer must create the labels and GitHub Projects board in repository or organization settings, then verify the actual configuration. Do not describe these labels or board as active before that verification.


## 10. Maintainer setup checklist

The repository integration used for this project can manage issues and apply existing labels, but it does not currently expose actions to create repository labels or GitHub Projects boards. Complete these steps in the GitHub web interface:

### Create repository labels

1. Open the repository and select **Issues**.
2. Open **Labels** and choose **New label**.
3. Create the labels listed in sections 1–4. Keep spelling and punctuation exact so issue templates and automation can reuse them consistently.
4. Re-open the Labels page and verify the full set exists before changing the dashboard status.

### Create the development board

1. Open the repository **Projects** tab, or create a project from the organization profile if repository Projects are unavailable.
2. Create a board named **SYNTRA Development**.
3. Add the workflow statuses from section 5: Backlog, Ready, In progress, Review, Testing, Done, Blocked.
4. Add the project fields from section 6 where supported.
5. Add issues #1, #2, and #3 to the board and assign their current workflow status accurately.
6. Copy the board URL into the Development Dashboard and verify that the board is accessible to the maintainers.

### Activation evidence

Do not mark labels or the board as active based only on this checklist. Record the verification date and link to the Labels page and board in the Development Dashboard after both are created and checked.
