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
# QA builds add an applicationId suffix, but MainActivity remains in the base
# Kotlin namespace.  Keep the component's class name independent of PACKAGE.
MAIN_ACTIVITY = "com.murshidwizari.student.MainActivity"
DEVICE_XML = "/sdcard/murshid-anonymous-hierarchy.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args: str) -> bytes:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=True, timeout=30)
    return result.stdout


def ui_tree() -> tuple[ET.Element, bytes]:
    for attempt in range(6):
        adb("shell", "uiautomator", "dump", DEVICE_XML)
        raw = adb("exec-out", "cat", DEVICE_XML)
        root = ET.fromstring(raw)
        # On a fresh emulator the OS sometimes displays its one-time immersive
        # navigation tutorial *after* our Activity has already become visible.
        # Dismiss only that known Android overlay, never an app-owned dialog.
        hint = find(root, "Viewing full screen")
        dismiss = find(root, "Got it")
        if hint is not None and dismiss is not None:
            tap_node(dismiss)
            print(f"RECOVER dismissed Android immersive-screen tutorial (attempt {attempt + 1})", flush=True)
            time.sleep(0.55)
            continue
        return root, raw
    raise AssertionError("Android emulator kept displaying the system immersive-screen tutorial after six attempts")


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


def reveal(text: str, width: int, height: int):
    for attempt in range(6):
        root, _ = ui_tree()
        node = find(root, text)
        if node is not None:
            match = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if match is not None:
                left, top, right, bottom = map(int, match.groups())
                # The app's fixed 5-tab bar reaches 24dp above the display
                # bottom on some API levels. Test the actual tap *centre*,
                # not the outer edge of its visible accessibility bounds.
                if (24 <= (top + bottom) // 2 <= height - 24 and
                        0 <= (left + right) // 2 <= width):
                    return node
        # Scroll the native login form to reach buttons below the viewport.
        if attempt < 5:
            adb("shell", "input", "swipe", str(width // 2), str(height * 4 // 5),
                str(width // 2), str(height // 4), "350")
            time.sleep(0.5)
    raise AssertionError(f"Cannot reach {text!r}; visible={visible_texts(root)!r}")


def tap_visible(text: str, width: int, height: int):
    tap_node(reveal(text, width, height))


def capture(directory: Path, name: str, marker: str = "منصة المرشد الوزاري") -> None:
    root, raw = ui_tree()
    if find(root, marker) is None:
        raise AssertionError(f"Screen marker {marker!r} not visible at {name}: {visible_texts(root)!r}")
    (directory / f"{name}.xml").write_bytes(raw)
    (directory / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    print(f"PASS captured {name}: {visible_texts(root)[:7]}", flush=True)


def check_controls(directory: Path, name: str, width: int, height: int) -> None:
    # The iPhone login uses an eye affordance inside the password field, not
    # the old Android checkbox. Verify both transitions through accessibility.
    for label, desired in (("إظهار كلمة المرور", "إخفاء كلمة المرور"),
                           ("إخفاء كلمة المرور", "إظهار كلمة المرور")):
        for attempt in range(2):
            tap_visible(label, width, height)
            try:
                wait_for(desired, seconds=8)
                break
            except AssertionError:
                if attempt == 1:
                    raise
                print(f"RETRY emulator input for {label!r} -> {desired!r}", flush=True)
    print("PASS password visibility toggle", flush=True)

    tap_visible("نسيت كلمة المرور؟", width, height)
    wait_for("استعادة كلمة المرور")
    wait_for("متابعة الاستعادة")
    capture(directory, name + "-recovery", "استعادة كلمة المرور")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for("إظهار كلمة المرور")
    print("PASS recovery screen and Android back", flush=True)

    tap_visible("إنشاء حساب طالب جديد", width, height)
    wait_for("إنشاء حساب")
    wait_for("بيانات الطالب")
    wait_for("الخطوة 1 من 3")
    capture(directory, name + "-register-top", "إنشاء حساب")
    # Build 498 uses RegisterV3View: step one alone should contain name, grade,
    # gender and 'التالي', not the contact and password fields until later.
    root, _ = ui_tree()
    assert find(root, "طريقة التسجيل") is None, "Step 1 must not show step 2"
    assert find(root, "حماية الحساب") is None, "Step 1 must not show step 3"
    capture(directory, name + "-register", "إنشاء حساب")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for("إظهار كلمة المرور")
    print("PASS registration screen and Android back", flush=True)


def enter_login(width: int, height: int) -> None:
    # A first synthetic tap can be dropped on a freshly booted Android 15
    # emulator. Retry *only* if we can still observe the original guest page.
    for attempt in range(3):
        tap_visible("تسجيل الدخول", width, height)
        try:
            wait_for("سجّل الدخول وواصل دراستك من حيث توقفت", seconds=8)
            return
        except AssertionError:
            root, _ = ui_tree()
            if find(root, "تعلّم بتركيز. تقدّم بثقة.") is None:
                raise
            print(f"RETRY guest-to-login emulator tap, attempt={attempt + 1}", flush=True)
    raise AssertionError("Guest login tap did not open the native login form")


def launch_native() -> None:
    for attempt in range(2):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-n", f"{PACKAGE}/{MAIN_ACTIVITY}")
        try:
            wait_for("تعلّم بتركيز. تقدّم بثقة.")
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
    capture(directory, name + "-guest", "تعلّم بتركيز. تقدّم بثقة.")
    # The iOS GuestLandingView is the true entry screen after the animated welcome.
    enter_login(width, height)
    capture(directory, name, "المرشد الوزاري")
    check_controls(directory, name, width, height)


def capture_additional_layout(directory: Path, name: str, width: int, height: int,
                              scale: float) -> None:
    """Extra layout evidence only; the full tap flows are exercised in test_size."""
    adb("shell", "settings", "put", "system", "font_scale", str(scale))
    adb("shell", "wm", "size", f"{width}x{height}")
    launch_native()
    if not adb("shell", "pidof", PACKAGE).strip():
        raise AssertionError(f"Native process exited at extra layout {name}")
    capture(directory, name + "-guest", "تعلّم بتركيز. تقدّم بثقة.")
    enter_login(width, height)
    capture(directory, name, "المرشد الوزاري")


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
