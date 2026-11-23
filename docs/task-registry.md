# SYNTRA Task Registry

**Registry version:** 1.1.0  
**Last updated:** 2026-11-23  
**Scope:** Shared ecosystem-level task tracking. Product-specific work should be tracked in its own repository when one exists.

## Status key

- **OPEN:** Not started or still requires work.
- **IN PROGRESS:** Work has started and is being actively handled.
- **BLOCKED:** Cannot proceed; explain the blocker in the issue.
- **REVIEW:** Awaiting review or validation.
- **DONE:** Acceptance criteria met and evidence recorded.

Priority:
- **P0:** Urgent / blocks critical work.
- **P1:** High priority.
- **P2:** Normal priority.
- **P3:** Low priority / future improvement.

## Active tasks

| ID | Priority | Status | Task | Tracking issue |
|---|---|---|---|---|
| SYN-002 | P1 | OPEN | Create issue labels and development project board | [#2](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/issues/2) |
| SYN-003 | P1 | IN PROGRESS | Record product build and QA evidence; physical-device QA remains pending | [#3](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/issues/3) |

## Completed tasks

| ID | Priority | Status | Task | Evidence |
|---|---|---|---|---|
| SYN-001 | P1 | DONE | Verify Repository Checks workflow on GitHub Actions | [Passing run #45154179133](https://github.com/dea78799-dotcom/SYNTRA-Ecosystem/actions/runs/45154179133) |
| SYN-004 | P2 | DONE | Add development dashboard | [Dashboard](development-dashboard.md) |
| SYN-005 | P2 | DONE | Add version tracker | [Version Tracker](version-tracker.md) |
| SYN-006 | P2 | DONE | Add release notes template | [Template](../.github/release_template.md) |
| SYN-007 | P2 | DONE | Add repository checks script and workflow | [Workflow](../.github/workflows/repository-checks.yml) |

## Task intake rules

1. Create a task only when the goal and acceptance criteria are clear.
2. Assign a priority based on impact, not excitement.
3. Use one tracking issue per independently deliverable task.
4. Record blockers in the issue and mark the task BLOCKED.
5. Close tasks only after checking the acceptance criteria and recording evidence.
6. Avoid storing secrets, personal data, or credentials in issues.

## Task record template

- **ID:**
- **Title:**
- **Priority:** P0 / P1 / P2 / P3
- **Status:** OPEN / IN PROGRESS / BLOCKED / REVIEW / DONE
- **Product:** Ecosystem / SYNTRA OS / Syntra Files
- **Goal:**
- **Acceptance criteria:**
- **Issue URL:**
- **Evidence:**
- **Blocker:**
- **Updated:** YYYY-MM-DD

## Board

The GitHub Projects board is not created yet. See [Issue Tracking](issue-tracking.md) for the planned columns and labels. This registry provides a written list until a board is created.
