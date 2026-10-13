#!/usr/bin/env python3
"""Native Android guest -> login -> registration visual and runtime smoke test."""
from __future__ import annotations
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path("android-from-ios/visual-qa")
ROOT.mkdir(parents=True, exist_ok=True)
PACKAGE = "com.muriq.murshid.iosparity"

def run(*args, check=True, timeout=45):
    return subprocess.run(args, check=check, timeout=timeout,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# Capture each screen once; repeating UIAutomator on headless runners may get
# killed (exit 137) even while the actual student application remains alive.
_ui_cache = {}

def snapshot(name, force=False):
    if not force and name in _ui_cache:
        return _ui_cache[name]
    image = run("adb", "exec-out", "screencap", "-p", timeout=30).stdout
    (ROOT / f"{name}.png").write_bytes(image)
    if not image.startswith(b"\x89PNG\r\n\x1a\n") or len(image) < 3000:
        raise RuntimeError(f"{name}: invalid PNG screenshot")
    last = None
    for attempt in range(2):
        try:
            run("adb", "shell", "uiautomator", "dump", "--compressed",
                "/sdcard/window.xml", timeout=35)
            xml = run("adb", "exec-out", "cat", "/sdcard/window.xml",
                      timeout=18).stdout
            tree = ET.fromstring(xml)
            if not list(tree.iter("node")):
                raise ValueError("no accessibility nodes")
            (ROOT / f"{name}.xml").write_bytes(xml)
            _ui_cache[name] = tree
            return tree
        except (subprocess.SubprocessError, ET.ParseError, ValueError) as error:
            last = error
            if attempt == 0:
                time.sleep(3)
    raise RuntimeError(f"{name}: accessibility extraction failed: {last}")

def find_node(tree, target):
    for node in tree.iter("node"):
        for attr in ("text", "content-desc"):
            label = node.attrib.get(attr, "")
            if target == label or (target in label and len(label) < len(target) + 35):
                bounds = node.attrib.get("bounds", "")
                coords = re.findall(r"\d+", bounds)
                if len(coords) == 4:
                    x1, y1, x2, y2 = map(int, coords)
                    if x2 > x1 and y2 > y1:
                        return (x1+x2)//2, (y1+y2)//2
    return None

def await_node(target, name, timeout=22):
    start = time.monotonic()
    attempt = 0
    while time.monotonic() - start < timeout:
        tree = snapshot(name, force=attempt > 0)
        pos = find_node(tree, target)
        if pos:
            return pos
        attempt += 1
        time.sleep(3)
    raise AssertionError(f"{name}: no accessible native control labeled {target!r}")

def tap(label, snapshot_name):
    x, y = await_node(label, snapshot_name)
    run("adb", "shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)

def no_crash():
    pid = run("adb", "shell", "pidof", PACKAGE, check=False).stdout.decode().strip()
    if not pid:
        raise AssertionError("Student Android process exited unexpectedly")
    logcat = run("adb", "logcat", "-d", "-s", "AndroidRuntime:E",
                 check=False, timeout=35).stdout.decode(errors="replace")
    if "FATAL EXCEPTION" in logcat and f"Process: {PACKAGE}" in logcat:
        (ROOT / "runtime-crash.log").write_text(logcat[-25000:])
        raise AssertionError("AndroidRuntime fatal exception in student process")

def main():
    run("adb", "shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(5)
    try:
        tap("دخول", "guest")
        await_node("تسجيل الدخول", "login")
        tap("إنشاء حساب طالب جديد", "login")
        await_node("بيانات الطالب", "register")
        await_node("الاسم الثلاثي الكامل", "register")
        no_crash()
        (ROOT / "result.txt").write_text(
            "PASS: native guest -> login -> registration; screenshot PNGs; live process; no fatal AndroidRuntime crash.\n"
        )
    except Exception as error:
        (ROOT / "result.txt").write_text("FAIL: "+str(error)+"\n")
        no_crash()
        raise

if __name__ == "__main__":
    main()
