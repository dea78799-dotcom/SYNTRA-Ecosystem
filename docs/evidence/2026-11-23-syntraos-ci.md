# SYNTRA OS — CI Build Evidence

**Record date:** 2026-11-23 (Asia/Ho_Chi_Minh)  
**Product:** SyntraOS  
**Product repository:** https://github.com/dea78799-dotcom/SyntraOS  
**Recorded product version:** 0.1.0 (from `VERSION` and `README.md`)  
**Branch:** `main`  
**Source commit:** [`11db17032c7de96a9ad4aed7fc541542d352be60`](https://github.com/dea78799-dotcom/SyntraOS/commit/11db17032c7de96a9ad4aed7fc541542d352be60)  
**Workflow:** [SyntraOS CI run #45151413981](https://github.com/dea78799-dotcom/SyntraOS/actions/runs/45151413981)  
**Trigger:** Scheduled run on `main`  
**Run time:** 2026-11-23 16:55:42–16:56:47 ICT  
**Workflow conclusion:** SUCCESS

## Verified CI results

Both workflow jobs completed successfully:

1. **Build and Test SyntraOS**
   - Verified host build tools.
   - Configured and built the Debug preset.
   - Ran CTest successfully.
   - Configured and built the Release preset.
   - Ran Release CTest successfully.
   - The checked-in `CMakeLists.txt` registers 53 CTest tests.

2. **Build ARM64 Kernel ELF**
   - Installed/verified the ARM64 cross-compilation toolchain.
   - Configured and built the ARM64 kernel ELF.
   - Validated diagnostic shell-script syntax.
   - Verified the ELF header reports AArch64.
   - Ran the EL0 no-GDB verification successfully.
   - Booted the ELF in QEMU.
   - Verified QEMU RAM framebuffer desktop pixels.
   - Generated and uploaded the mobile-viewable desktop preview.

## CI artifacts

The workflow run contains these non-expired artifacts at the time this record was written:

- `syntraos-arm64-qemu-11db17032c7de96a9ad4aed7fc541542d352be60` — ARM64 ELF, 31,110 bytes.
- `syntraos-el0-no-gdb-logs-11db17032c7de96a9ad4aed7fc541542d352be60` — diagnostic logs.
- `syntraos-desktop-preview-11db17032c7de96a9ad4aed7fc541542d352be60` — QEMU desktop preview assets.

Artifacts are temporary GitHub Actions artifacts with a 14-day retention period. Open the workflow run above to access them; do not treat them as a permanent release download.

## QA interpretation

**CI result: GREEN. Product release QA: YELLOW — more evidence required.**

The successful run proves that the recorded host builds, registered automated tests, ARM64 cross-build, and QEMU checks passed for this source revision.

The run does **not** prove:
- Boot compatibility on a physical phone or other real hardware.
- Driver compatibility across mobile devices.
- Completion of all planned operating-system features.
- Production readiness or approval for installation on a primary device.

| QA area | Current result | Evidence |
|---|---|---|
| Host Debug build and CTest | PASS | Workflow job steps |
| Host Release build and CTest | PASS | Workflow job steps |
| ARM64 cross-build and ELF validation | PASS | Workflow job steps |
| EL0 and QEMU boot checks | PASS | Workflow job steps |
| QEMU desktop framebuffer verification | PASS | Workflow job steps |
| Physical-device compatibility | NEEDS REVIEW | Not recorded |
| Full product security/release review | NEEDS REVIEW | Not recorded |

**Device / model:** Not recorded  
**Physical installation:** Not performed / not recorded  
**Known product defects:** Not assessed by this CI record  
**Release decision:** Continue development; no software release is declared by this record.

This is a CI evidence snapshot, not a complete product QA sign-off. Re-run and update the record whenever the source revision changes materially.
