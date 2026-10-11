"""Anonymous Android emulator UI smoke test. No account, secrets or POST requests.

The workflow already built the QA-only debug APK and booted an ephemeral emulator.
This script exercises on-screen native controls and captures evidence at phone and
tablet sizes, without relying on fixed tap coordinates.
"""

import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.murshidwizari.student"
DEVICE_XML = "/sdcard/murshid-anonymous-hierarchy.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args: str) -> bytes:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=True, timeout=30)
    return result.stdout


def ui_tree() -> tuple[ET.Element, bytes]:
    for attempt in range(2):
        adb("shell", "uiautomator", "dump", DEVICE_XML)
        raw = adb("exec-out", "cat", DEVICE_XML)
        root = ET.fromstring(raw)
        # On a fresh emulator the OS sometimes displays its one-time immersive
        # navigation tutorial *after* our Activity has already become visible.
        # Dismiss only that known Android overlay, never an app-owned dialog.
        hint = find(root, "Viewing full screen")
        dismiss = find(root, "Got it")
        if attempt == 0 and hint is not None and dismiss is not None:
            tap_node(dismiss)
            print("RECOVER dismissed Android immersive-screen tutorial", flush=True)
            time.sleep(0.4)
            continue
        return root, raw
    raise AssertionError("Could not read Android UI hierarchy")


def find(root: ET.Element, target: str):
    for node in root.iter("node"):
        if node.attrib.get("text") == target or node.attrib.get("content-desc") == target:
            return node
    return None


def visible_texts(root: ET.Element) -> list[str]:
    return [node.attrib["text"] for node in root.iter("node")
            if node.attrib.get("text")][:24]


def wait_for(text: str, seconds: float = 28, checked: str | None = None):
    deadline = time.monotonic() + seconds
    last = None
    last_checked = None
    while time.monotonic() < deadline:
        try:
            root, raw = ui_tree()
            last = visible_texts(root)
            node = find(root, text)
            if node is not None:
                last_checked = node.attrib.get("checked")
            if node is not None and (checked is None or last_checked == checked):
                return node, raw
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
            pass
        time.sleep(1.2)
    raise AssertionError(f"Timed out waiting for {text!r}, checked={checked!r}; "
                         f"last_checked={last_checked!r}, visible={last!r}")


def tap_node(node: ET.Element) -> None:
    bounds = node.attrib.get("bounds", "")
    match = BOUNDS.fullmatch(bounds)
    if match is None:
        raise AssertionError(f"No bounds for {node.attrib.get('text')!r}")
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise AssertionError(f"Invalid touch bounds: {bounds}")
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def tap_visible(text: str, width: int, height: int):
    for attempt in range(6):
        root, _ = ui_tree()
        node = find(root, text)
        if node is not None:
            match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if match is not None:
                _, top, _, bottom = map(int, match.groups())
                # UIAutomator can expose partly clipped buttons over the system navbar.
                if top >= 24 and bottom <= height - 32:
                    tap_node(node)
                    return
        # Scroll the native login form to reach buttons below the viewport.
        if attempt < 5:
            adb("shell", "input", "swipe", str(width // 2), str(height * 4 // 5),
                str(width // 2), str(height // 4), "350")
            time.sleep(0.5)
    raise AssertionError(f"Cannot reach {text!r}; visible={visible_texts(root)!r}")


def capture(directory: Path, name: str, marker: str = "منصة المرشد الوزاري") -> None:
    root, raw = ui_tree()
    if find(root, marker) is None:
        raise AssertionError(f"Screen marker {marker!r} not visible at {name}: {visible_texts(root)!r}")
    (directory / f"{name}.xml").write_bytes(raw)
    (directory / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    print(f"PASS captured {name}: {visible_texts(root)[:7]}", flush=True)


def check_controls(directory: Path, name: str, width: int, height: int) -> None:
    label = "إظهار كلمة المرور"
    # Each tap must produce an observable state change. A newly booted emulator
    # occasionally drops its first synthetic input event; retry only when the
    # hierarchy still proves the old state, and surface the retry in CI logs.
    wait_for(label, checked="false")
    for desired in ("true", "false"):
        for attempt in range(2):
            node, _ = wait_for(label)
            if node.attrib.get("checked") == desired:
                break
            tap_visible(label, width, height)
            try:
                wait_for(label, seconds=8, checked=desired)
                break
            except AssertionError:
                if attempt == 1:
                    raise
                print(f"RETRY emulator input for {label!r}, desired={desired}", flush=True)
    print("PASS password visibility toggle", flush=True)

    tap_visible("نسيت كلمة المرور؟", width, height)
    wait_for("استعادة كلمة المرور")
    wait_for("متابعة الاستعادة")
    capture(directory, name + "-recovery", "استعادة كلمة المرور")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for("منصة المرشد الوزاري")
    print("PASS recovery screen and Android back", flush=True)

    tap_visible("إنشاء حساب طالب جديد", width, height)
    wait_for("إنشاء حساب جديد")
    wait_for("طريقة التسجيل")
    capture(directory, name + "-register", "إنشاء حساب جديد")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for("منصة المرشد الوزاري")
    print("PASS registration screen and Android back", flush=True)


def launch_native() -> None:
    for attempt in range(2):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
        try:
            wait_for("منصة المرشد الوزاري")
            return
        except AssertionError:
            # A freshly booted API-33 emulator may show a *system launcher* ANR
            # over our Activity. Dismiss only this known OS dialog, once.
            if attempt != 0:
                raise
            root, _ = ui_tree()
            launcher = find(root, "Pixel Launcher isn't responding")
            close = find(root, "Close app")
            if launcher is None or close is None:
                raise
            tap_node(close)
            print("RECOVER dismissed emulator Pixel Launcher ANR before app launch", flush=True)
            time.sleep(1.5)


def test_size(directory: Path, name: str, width: int, height: int, scale: float) -> None:
    adb("shell", "settings", "put", "system", "font_scale", str(scale))
    adb("shell", "wm", "size", f"{width}x{height}")
    launch_native()
    if not adb("shell", "pidof", PACKAGE).strip():
        raise AssertionError("Native app process did not survive launch")
    capture(directory, name)
    check_controls(directory, name, width, height)


def capture_additional_layout(directory: Path, name: str, width: int, height: int,
                              scale: float) -> None:
    """Extra layout evidence only; the full tap flows are exercised in test_size."""
    adb("shell", "settings", "put", "system", "font_scale", str(scale))
    adb("shell", "wm", "size", f"{width}x{height}")
    launch_native()
    if not adb("shell", "pidof", PACKAGE).strip():
        raise AssertionError(f"Native process exited at extra layout {name}")
    capture(directory, name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb("install", "-r", "android-native/app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "wm", "density", "160")
    test_size(args.output, "login-phone-360x640", 360, 640, 1.0)
    test_size(args.output, "login-tablet-800x1280-font1.3", 800, 1280, 1.3)
    # API 35 provides additional small-phone and compact-tablet viewport evidence.
    api_level = int(adb("shell", "getprop", "ro.build.version.sdk").decode().strip())
    if api_level == 35:
        capture_additional_layout(args.output, "login-phone-393x852-font1.3", 393, 852, 1.3)
        capture_additional_layout(args.output, "login-compact-tablet-600x960", 600, 960, 1.0)
    print("PASS all anonymous device UI checks; no authentication or POST requests", flush=True)


if __name__ == "__main__":
    main()
