# SYNTRA Ecosystem — Product Directory

> Document status: Initial product catalogue  
> Version: 1.0.0  
> Last updated: 2026-11-23

This document is the central directory for products in the SYNTRA Ecosystem. It records each product's purpose, scope, current status, and documentation entry points.

Product status must reflect verified development progress. A listing here does not mean that a product is publicly released, production-ready, or available for download.

## 1. Product overview

| Product | Category | Primary purpose | Current status |
|---|---|---|---|
| [SYNTRA OS](#2-syntra-os) | Operating-system project | Develop the SYNTRA operating-system experience | In development |
| [Syntra Files](#3-syntra-files) | File-management application | Browse and manage files | Testing and upgrading |

## 2. SYNTRA OS

**Category:** Operating-system project  
**Status:** In development  
**Priority:** Primary development focus

### Purpose

SYNTRA OS is the main operating-system project within the SYNTRA Ecosystem. Its goal is to develop a coherent operating-system experience under the SYNTRA identity.

### Scope

The detailed feature set, supported devices, technical base, and release targets must be maintained in the SYNTRA OS project repository and its own documentation. This central catalogue does not assume features or compatibility that have not been verified.

### Development expectations

- Maintain a clear feature and version history.
- Document supported environments and known limitations.
- Test core workflows on the intended target devices.
- Review permissions, data handling, and update behavior before release.
- Mark experimental features clearly.

### Release status

SYNTRA OS is in development. No production release or general availability is asserted by this document.

## 3. Syntra Files

**Category:** File-management application  
**Status:** Testing and upgrading

### Purpose

Syntra Files is a file-management application in the SYNTRA Ecosystem. It is intended to help users browse and work with files through a clear, consistent interface.

### Scope

The application may provide file browsing and file-type-aware access. Specific operations, supported formats, installation behavior, and device compatibility must be confirmed in the product implementation and tests before being advertised as supported.

### Development expectations

- Handle file and folder access using platform-approved permissions.
- Clearly distinguish viewing, editing, sharing, installing, and deleting actions.
- Confirm potentially destructive actions before they are performed.
- Handle unsupported formats and access errors with clear messages.
- Test on supported Android versions and target devices.
- Keep installation and update behavior transparent.

### Release status

Syntra Files is currently being tested and upgraded. It must not be described as a finished or stable release until the required checks have passed and a release is published.

## 4. Product status definitions

| Status | Meaning |
|---|---|
| Proposed | An idea being evaluated; implementation has not necessarily started |
| In development | Active implementation is underway |
| Testing | A build or feature is undergoing defined tests |
| Testing and upgrading | Testing is ongoing while fixes or improvements are being made |
| Released | A version has been published with release notes |
| On hold | Work is paused |

A status change should be supported by a repository update, test record, or release record.

## 5. Shared product requirements

Every SYNTRA product should:

1. Use approved SYNTRA brand assets.
2. Provide clear product information and version identification.
3. Document supported devices, platforms, and limitations.
4. Protect user data and avoid unnecessary permissions.
5. Validate inputs and handle errors safely.
6. Include relevant testing before release.
7. Maintain a changelog for meaningful updates.
8. Avoid claiming unsupported features or unverified compatibility.

## 6. Product independence

Each product should own its source code, technical decisions, tests, and release lifecycle. The ecosystem repository provides shared direction and discovery; it does not replace each product's own source repository.

Products may integrate when there is a documented user benefit, a defined interface, and an appropriate privacy and security review.

## 7. Future product proposals

New product ideas should be listed separately as **proposed** only after their purpose and scope have been reviewed. They must not be mixed with active products or presented as available services.

## 8. Keeping this directory current

Update this document when a product changes its purpose, status, platform support, or release state. For detailed implementation progress, consult the product's own repository and release notes.

---

**Current catalogue:** 2 active product projects. SYNTRA OS is the primary development focus; Syntra Files remains in testing and upgrading.
