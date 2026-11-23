#!/usr/bin/env python3
"""Lightweight, dependency-free repository structure and Markdown link checks."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "README.md",
    "CHANGELOG.md",
    "LICENSE",
    "docs/architecture.md",
    "docs/products.md",
    "docs/roadmap.md",
    "docs/development-standards.md",
    "docs/quality-assurance.md",
    "docs/release-management.md",
    "docs/security-policy.md",
    "docs/contribution-guide.md",
    "docs/branding-guidelines.md",
    "docs/project-governance.md",
    "docs/issue-tracking.md",
    "docs/development-dashboard.md",
    "docs/version-tracker.md",
    ".github/ISSUE_TEMPLATE/bug_report.yml",
    ".github/ISSUE_TEMPLATE/feature_request.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    ".github/pull_request_template.md",
    ".github/workflows/repository-checks.yml",
]

errors = []

def fail(message: str) -> None:
    errors.append(message)
    print(f"FAIL: {message}")

def check_files() -> None:
    for relative in REQUIRED_FILES:
        path = ROOT / relative
        if not path.is_file():
            fail(f"Required file missing: {relative}")
        elif path.stat().st_size == 0:
            fail(f"Required file is empty: {relative}")
        else:
            print(f"PASS: file exists: {relative}")

def check_local_markdown_links() -> None:
    link_pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
    for markdown in ROOT.rglob("*.md"):
        if ".git" in markdown.parts:
            continue
        text = markdown.read_text(encoding="utf-8")
        for target in link_pattern.findall(text):
            target = target.strip()
            if not target or target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            target = target.split("#", 1)[0]
            if not target:
                continue
            resolved = (markdown.parent / target).resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                fail(f"Link escapes repository: {markdown.relative_to(ROOT)} -> {target}")
                continue
            if not resolved.exists():
                fail(f"Broken local Markdown link: {markdown.relative_to(ROOT)} -> {target}")
            else:
                print(f"PASS: local link: {markdown.relative_to(ROOT)} -> {target}")

def check_templates() -> None:
    expected = {
        ".github/ISSUE_TEMPLATE/bug_report.yml": ["name:", "description:", "body:"],
        ".github/ISSUE_TEMPLATE/feature_request.yml": ["name:", "description:", "body:"],
        ".github/ISSUE_TEMPLATE/config.yml": ["blank_issues_enabled:"],
    }
    for relative, markers in expected.items():
        content = (ROOT / relative).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in content:
                fail(f"Template marker missing in {relative}: {marker}")
            else:
                print(f"PASS: template marker in {relative}: {marker}")

def main() -> int:
    print("SYNTRA repository checks starting")
    check_files()
    check_local_markdown_links()
    check_templates()
    if errors:
        print(f"\nRED: {len(errors)} check(s) failed.")
        return 1
    print("\nGREEN: all repository checks passed.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
