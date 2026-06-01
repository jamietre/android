# Android Auto Wireless Bug — Investigation Notes

## The Bug

wgtunnel kills Android Auto Wireless (via AA Wireless 2+ dongle) the instant the VPN tunnel is
started. wireguard-android with an identical WireGuard config works fine under the same conditions.

**Device:** Pixel 10  
**VPN config:** `AllowedIPs = 172.16.0.0/22` (split tunnel, home subnet only)

## What Has Been Ruled Out

- **Split tunneling config** — tested both exclude-AA-Wireless mode and include-only-Symfonium
  mode; both kill AA the moment the tunnel starts. The issue is triggered by tunnel establishment
  itself, not routing rules.
- **Foreground service type crash** — the original `systemExempted` foreground service type caused
  a `SecurityException` crash on launch. Changed to `specialUse` (see Changes Made below). This
  fixed the crash but not the underlying Android Auto bug.

## Changes Made So Far (branch `fix/android-auto-vpn-service-type`)

**`tunnel/src/main/AndroidManifest.xml`:**
- `VpnService`: changed `foregroundServiceType` from `systemExempted` → `specialUse`, removed
  `android:persistent="true"`
- Replaced `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` permission with `FOREGROUND_SERVICE_SPECIAL_USE`
- Added `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property to both services

**`tunnel/src/main/java/com/zaneschepke/tunnel/service/VpnService.kt`:**
- Changed `SYSTEM_EXEMPT_SERVICE_TYPE_ID = 1 shl 10` → `SPECIAL_USE_SERVICE_TYPE_ID = 1 shl 30`

## Key Differences vs wireguard-android

These are the remaining behavioral differences that may be causing the Android Auto failure:

### 1. Service start method

| App | How VPN service is started |
|-----|---------------------------|
| wireguard-android | `context.startService()` — plain service, never calls `startForeground()` |
| wgtunnel | `context.startForegroundService()` — foreground from the start, calls `startForeground()` in both `onCreate()` and `onStartCommand()` |

Android's `ConnectivityService` may react differently when a foreground service establishes a VPN
interface vs. a plain background service.

### 2. `setUnderlyingNetworks(null)` timing

| App | When called |
|-----|-------------|
| wireguard-android | Called on the service object **after** `establish()` returns |
| wgtunnel | Inside `builder.apply {}` — due to Kotlin implicit receiver scoping, this calls it on the enclosing `VpnService` instance **before** `establish()` |

In wgtunnel's `createTunInterface()`:
```kotlin
return builder
    .apply {
        // ...
        setUnderlyingNetworks(null)   // ← calls VpnService.setUnderlyingNetworks, not Builder
        // ...
    }
    .establish()
```

This may be a no-op (no VPN exists yet), but it is a real behavioral difference.

## Next Step

Capture wgtunnel's own Timber logs during the failure. A persistent logcat process is already
running on the device writing to `/data/local/tmp/wgt.txt` (survives WiFi/ADB disconnection).

After next car test, retrieve with:
```bash
~/android-sdk/platform-tools/adb pull /data/local/tmp/wgt.txt /tmp/wgt_car_test.txt
```

Look for:
- wgtunnel process logs (PID of `com.zaneschepke.wireguardautotunnel.debug`)
- `ConnectivityService` VPN establishment events
- `isAppeared=[false]` for AA-Wireless network association
- Relative timing between tunnel start and AA disconnect
