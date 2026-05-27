---
name: android-emulator-run
description: Use when installing, launching, or interacting with the Liftorium debug APK on an Android emulator or attached device for manual review or runtime-evidence screenshots. Do not use for Paparazzi/Robolectric host-side snapshots (use android-verification) or for any non-Android run.
---

# Android Emulator Run

Living skill — update this file every time something new is learned about running Liftorium on a real Android runtime. Paparazzi covers most UI evidence; this skill is for the cases where the agent or user needs the actual app running (real DB, real lifecycle, real navigation).

## When to use

- User asks "run it in the emulator" / "open the app" / "show me on a device".
- Slice acceptance requires real runtime evidence (process death, cold-start recovery, permission prompts, real-system back stack) that Paparazzi/Robolectric cannot provide.
- Verifying a manual flow end-to-end before committing a slice.

## Environment (THIS machine — Windows)

The default Liftorium dev machine has TWO Android SDK locations and only ONE of them is usable for emulators:

| Path | Contents | Use for emulator? |
| --- | --- | --- |
| `C:\Users\berse\AppData\Local\Android\Sdk` | platform-tools, some system-images (android-29, chromeos-67) | ❌ AVDs are not registered against it |
| `C:\Program Files (x86)\Android\android-sdk` | platform-tools, emulator, system-images for android-28/29/30/31/32/36 | ✅ **Use this one** |

**Required env per shell** (do not rely on user env):

```powershell
$env:ANDROID_SDK_ROOT = "C:\Program Files (x86)\Android\android-sdk"
$env:ANDROID_HOME     = $env:ANDROID_SDK_ROOT
$env:JAVA_HOME        = "C:\Program Files\Android\openjdk\jdk-21.0.8"
```

Tool paths:

- `adb`      → `$env:ANDROID_SDK_ROOT\platform-tools\adb.exe`
- `emulator` → `$env:ANDROID_SDK_ROOT\emulator\emulator.exe`

## Hypervisor (resolved 2026-05-25)

This machine uses **Windows Hypervisor Platform (WHPX)** for x86_64 emulation. The emulator log line that confirms it is working:

> WHPX on Windows 10.0.26220 detected.
> Windows Hypervisor Platform accelerator is operational

### How WHPX was enabled

1. BIOS virtualization (Intel VT-x / AMD-V) must be ON. User-side action.
2. From an elevated PowerShell:
   ```powershell
   Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All -NoRestart
   Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -All -NoRestart
   ```
3. No reboot was needed on this machine (Windows 11, 10.0.26220). If a reboot IS requested, do it before retrying the emulator.

### Why NOT AEHD on this machine

The Android Emulator Hypervisor Driver (AEHD) installer reports:

> [SC] StartService FAILED with error 4294967201 (0xFFFFFFA1)

This is caused by **HVCI / Memory Integrity / Core Isolation** being enabled
(`HKLM:\SYSTEM\CurrentControlSet\Control\DeviceGuard\Scenarios\HypervisorEnforcedCodeIntegrity` → `Enabled = 1`).
HVCI takes over the bare-metal hypervisor and prevents AEHD's `aehd.sys` kernel driver from starting.
**Use WHPX instead** — it coexists with HVCI. Do not chase AEHD on this box unless the user explicitly disables Memory Integrity in Windows Security.

### If neither WHPX nor AEHD works

Fallbacks in priority order:

1. **Physical device over USB** — `adb devices -l` after enabling USB debugging on the phone. Skip emulator entirely.
2. **Defer to Paparazzi/Robolectric** — sufficient for most UI changes; not sufficient for process-death/recovery acceptance evidence.

If hypervisor acceleration is unavailable, STOP and surface it to the user — do not loop on `adb devices`.

## AVDs

```text
pixel_5_-_api_32        — Google APIs (no Play Store), API 32, x86_64. Smaller, faster cold boot. Default for this skill.
pixel_9_pro_-_api_36_0  — Google Play, API 36, x86_64. Larger, slower cold boot (observed 6+ min). Use only if API-36 behavior is needed.
```

Both AVDs are registered against `C:\Program Files (x86)\Android\android-sdk`. The `~/AppData/Local/Android/Sdk` location does NOT have the matching system-images and will fail with `PANIC: Broken AVD system path` or `Cannot find AVD system path`.

## Workflow

### 1. Build the debug APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
cd I:\Programs\Liftorium\android
.\gradlew.bat :app:assembleDebug --no-daemon
```

Output: `android\app\build\outputs\apk\debug\app-debug.apk`.

### 2. Launch the emulator (if no physical device)

Always set `ANDROID_SDK_ROOT` first or the emulator panics. Run in a background async shell so the agent can continue working:

```powershell
$env:ANDROID_SDK_ROOT = "C:\Program Files (x86)\Android\android-sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
& "$env:ANDROID_SDK_ROOT\emulator\emulator.exe" -avd pixel_5_-_api_32 -no-snapshot-save -no-boot-anim
```

- `-no-snapshot-save` keeps the AVD reproducible across runs (slower cold boot but no stale state). Drop it for faster iterative reruns.
- `-no-boot-anim` shaves a few seconds.
- Do NOT use `-WindowStyle Hidden` with `Start-Process` — observed silent failure with no diagnostics. Use an `async` PowerShell shell instead so the agent can `read_powershell` the emulator log for `PANIC:` lines.

### 3. Wait for boot (bounded, never bare `while`)

The naive `adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'` hangs silently for many minutes with no output and is hard to interrupt. Use a bounded polling loop with progress:

```powershell
$adb = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
& $adb wait-for-device
for ($i = 0; $i -lt 36; $i++) {
  $b = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
  if ($b -eq "1") { Write-Host "BOOTED at ${i}x5s"; break }
  Start-Sleep -Seconds 5
  Write-Host "  waiting boot... ${i}x5s (got '$b')"
}
```

With WHPX + `pixel_5_-_api_32`, observed boot time is ~25s. If 3 minutes elapse without boot, dump the emulator log via `read_powershell` and stop — do not keep waiting.

The first emulator launch after `adb` is started will print `Unable to connect to adb daemon on port: 5037` and `device offline` warnings; these are harmless — the daemon auto-starts and the device comes online by the time the boot poll runs.

### 4. Install + launch

```powershell
& $adb install -r "I:\Programs\Liftorium\android\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -n dev.liftorium.app/.MainActivity
```

Package id is `dev.liftorium.app` (see `android/app/build.gradle.kts` → `applicationId`).

### 5. Capture runtime evidence

```powershell
$out = "I:\Programs\Liftorium\.copilot\runtime-evidence\$(Get-Date -Format yyyyMMdd-HHmmss).png"
New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
& $adb exec-out screencap -p > $out
```

Then `view` the PNG. The `> $out` redirect in PowerShell preserves binary bytes for `exec-out screencap -p` (verified 2026-05-25: 70 KB PNG opens correctly). Prefer this over `screencap /sdcard/...; adb pull` — fewer steps, no on-device file left behind.

For process-death evidence:

```powershell
& $adb shell am kill dev.liftorium.app                 # graceful
& $adb shell am force-stop dev.liftorium.app           # harder
# relaunch and screenshot to assert cold-start recovery
```

### 5a. Find tap coordinates with uiautomator (don't eyeball the screenshot)

`adb input tap X Y` uses device pixel coordinates. Eyeballing a screenshot is unreliable — Compose buttons often have padding/elevation that makes the visible area smaller than the click target, and the click region's *Y* may be 100-200 px off from where the text appears. Always dump the live hierarchy and grep for the visible label:

```powershell
& $adb shell uiautomator dump /sdcard/ui.xml
& $adb pull /sdcard/ui.xml .copilot/runtime-evidence/ui.xml
# Then grep the file for the text/contentDesc of the element; read `bounds="[L,T][R,B]"`
# and tap at the center: X=(L+R)/2, Y=(T+B)/2.
```

Observed 2026-05-25: a Compose Button labeled "Activate program" displayed text-center at y≈1228 with click bounds `[44,1162][1036,1294]`. Eyeballing put it at y≈1050 — every tap there silently missed.

### 5b. Avoid Java 9+ time APIs (no core-library desugaring)

This project does NOT enable `coreLibraryDesugaring`. Domain code MUST stick to Java 8 java.time methods. The recurring trap is `LocalDate.ofInstant(Instant, ZoneId)` — added in JDK 9, present on the host JVM (so unit tests pass), absent on pre-desugar Android runtime → `NoSuchMethodError` at the first call. Replace with `instant.atZone(zoneId).toLocalDate()`. If a new ADR opts in to desugaring, drop this guidance.

### 6. Cleanup

```powershell
& $adb -s emulator-5554 emu kill   # if emulator was used
```

Do NOT call `Stop-Process -Name`. Use the emulator's own `emu kill` or `Stop-Process -Id <PID>` against a specific PID.

## Anti-patterns (observed and discarded)

- **`Start-Process emulator.exe -WindowStyle Hidden`** — silently exited, no log, looked like a slow boot for 6+ minutes. Always launch through an `async` PowerShell shell so stderr/stdout are readable.
- **Trusting `ANDROID_HOME`/`ANDROID_SDK_ROOT` env from the user shell** — the user env points at the wrong SDK on this machine. Set explicitly in every shell.
- **Bare `adb shell 'while ...'` boot poll** — no progress signal, hangs the agent. Always use a bounded loop with an echo per iteration.
- **Assuming hypervisor is installed** — check the first emulator launch's stderr for the hypervisor error and surface it immediately.

## Outputs

- A confirmed-running APK on a real Android runtime (emulator or device).
- One or more screenshots saved under `.copilot/runtime-evidence/` (gitignored — do not commit).
- A short summary message stating what flow was exercised, where the screenshots are, and any deviations from expected behavior.

## Maintenance

When the workflow surprises you (new error, slow path, missing tool), update this file in the same PR. The skill description is what triggers auto-discovery — keep it accurate.
