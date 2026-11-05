from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = ROOT / "app/src/main/AndroidManifest.xml"
source = ROOT / "app/src/main/java/com/fpsplus/vector"
workflow = ROOT / ".github/workflows/build-apk.yml"

ET.parse(manifest)
manifest_text = manifest.read_text(encoding="utf-8")
required_manifest_items = [
    "SYSTEM_ALERT_WINDOW",
    "POST_NOTIFICATIONS",
    "FOREGROUND_SERVICE_SPECIAL_USE",
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "rikka.shizuku.ShizukuProvider",
    "foregroundServiceType=\"specialUse\"",
]
for item in required_manifest_items:
    assert item in manifest_text, f"Missing manifest declaration: {item}"

main = (source / "MainActivity.java").read_text(encoding="utf-8")
service = (source / "OverlayService.java").read_text(encoding="utf-8")
reader = (source / "SurfaceFlingerReader.java").read_text(encoding="utf-8")
bridge = (source / "ShizukuShellBridge.java").read_text(encoding="utf-8")
user_service = (source / "ShellUserService.java").read_text(encoding="utf-8")
aidl = (ROOT / "app/src/main/aidl/com/fpsplus/vector/ICommandService.aidl").read_text(encoding="utf-8")
checks = {
    "nine-second splash": "setDuration(9000L)" in main and "postDelayed" in main,
    "overlay permission flow": "ACTION_MANAGE_OVERLAY_PERMISSION" in main,
    "battery background flow": "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in main,
    "Shizuku permission request": "Shizuku.requestPermission" in main,
    "official Shizuku UserService": "bindUserService" in bridge and "Shizuku.UserServiceArgs" in bridge,
    "AIDL command bridge": "interface ICommandService" in aidl,
    "privileged shell service": "extends ICommandService.Stub" in user_service,
    "no deprecated Shizuku process call": "newProcess" not in reader and "newProcess" not in bridge,
    "live overlay window": "TYPE_APPLICATION_OVERLAY" in service,
    "drag support": "DragTouchListener" in service,
    "resize support": "ResizeTouchListener" in service,
    "SurfaceFlinger sampling": "--timestats -dump" in reader,
    "no fabricated FPS fallback": "return null;" in reader,
    "RAM, battery and temperature": all(x in service for x in ["DeviceMetrics", "BAT TEMP", "RAM"]),
}
for name, passed in checks.items():
    assert passed, f"Static check failed: {name}"

workflow_text = workflow.read_text(encoding="utf-8")
assert "gradle --no-daemon :app:assembleDebug" in workflow_text
assert "actions/upload-artifact@v4" in workflow_text

print(f"Static checks passed: {len(checks) + len(required_manifest_items) + 3} checks")
