# Syntra-Files — CI Build Evidence

**Record date:** 2026-11-23 (Asia/Ho_Chi_Minh)  
**Product:** Syntra-Files  
**Product repository:** https://github.com/dea78799-dotcom/Syntra-Files  
**Recorded app version:** 0.69.0  
**Android version code:** 690  
**Application ID:** `com.syntra.files`  
**Branch:** `main`  
**Source commit:** [`bd4f54f78edd2f27b9845e19050a4173525d4692`](https://github.com/dea78799-dotcom/Syntra-Files/commit/bd4f54f78edd2f27b9845e19050a4173525d4692)  
**Workflow:** [Android CI run #45111079923](https://github.com/dea78799-dotcom/Syntra-Files/actions/runs/45111079923)  
**Trigger:** Push to `main`  
**Run time:** 2026-11-23 11:18:46–11:20:25 ICT  
**Workflow conclusion:** SUCCESS

## Build configuration verified

- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle used by CI: 8.9
- Java: Temurin JDK 17
- Compile SDK: 35
- Minimum SDK: 26
- Target SDK: 35
- Build variant: Debug

## Verified CI result

The single `build-debug` job completed successfully:

1. Checked out the source revision.
2. Set up JDK 17.
3. Set up Gradle 8.9.
4. Ran `gradle --no-daemon assembleDebug` successfully.
5. Uploaded the generated debug APK successfully.

**Artifact:** `Syntra-Files-debug-v0.69.0`  
**Artifact size:** 16,187,456 bytes  
**Artifact access:** Open the workflow run above and use its Artifacts section.  
**Retention:** 14 days; the artifact is temporary and is not a permanent release download.

## QA interpretation

**CI result: GREEN. Product release QA: YELLOW — more evidence required.**

This run proves that the Debug APK was generated and uploaded for the recorded source revision. The workflow does not execute instrumentation tests, install the APK on a physical phone, or validate the file manager's runtime behavior.

| QA area | Current result | Evidence |
|---|---|---|
| Debug APK compilation | PASS | `build-debug` job |
| APK artifact upload | PASS | Workflow artifact |
| Physical-device installation and launch | NEEDS REVIEW | Not recorded |
| SAF folder access and permission persistence | NEEDS REVIEW | Not recorded |
| Browse, search, sort, open, create, rename, copy, move, delete | NEEDS REVIEW | Not recorded on a device |
| Large-file and storage-provider compatibility | NEEDS REVIEW | Not recorded |
| Security and release signing review | NEEDS REVIEW | Not recorded |

**Device / model:** Not recorded  
**Android version on a test device:** Not recorded  
**Physical installation:** Not performed / not recorded  
**Known runtime defects:** Not assessed by this CI record  
**Release decision:** Continue development; no software release is declared by this record.

The successful debug build must not be interpreted as a full functional, compatibility, security, or release QA pass. Run device-level tests and record their actual observations before changing those gates to PASS.
