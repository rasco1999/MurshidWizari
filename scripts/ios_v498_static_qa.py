"""Non-network checks supplement, but never replace, simulator/Xcode tests."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
patch = root / "patches"
speech = (patch / "V498QuestionSpeech.swift").read_text()
ads = (patch / "V498LiveSchoolAds.swift").read_text()
auth = (patch / "AuthViews.swift").read_text()
home = (patch / "V31Features.swift").read_text()
api = (patch / "APIClient.swift").read_text()
workflow = (root / ".github/workflows/build-ios.yml").read_text()
checks = [
    (speech, "setCategory(.playback, mode: .spokenAudio", "Silent-mode-safe playback category"),
    (speech, "try session.setActive(true)", "Explicit activation, not swallowed errors"),
    (speech, "AVSpeechSynthesizerDelegate", "Playback lifecycle and cancellation"),
    (speech, "self.utterance === utterance", "Reject stale speech callbacks"),
    (speech, "8_000_000_000", "Bounded start and one recovery attempt"),
    (speech, "oldDeviceUnavailable", "Headphone unplug privacy"),
    (speech, "didEnterBackgroundNotification", "Stop private speech in background"),
    (ads, "15_000_000_000", "Foreground ad polling"),
    (ads, "generation == expectedGeneration", "Account-switch response guard"),
    (ads, 'request("mobile/bootstrap.php", fresh: true)', "Uncached canonical active ads"),
    (api, ".reloadIgnoringLocalCacheData", "Bypass HTTP cache on live ad reads"),
    (auth, ".task(id: adsMonitorKey)", "Cancel/restart polling with scene/account/connectivity"),
    (auth, "V498UnreadAdBadge(count: schoolAds.unreadCount)", "Red badge attached to Ads button"),
    (home, "V498SocialLinksFooter()", "Compact branded student footer"),
    (home, "StudentPalette.allCases", "Build506 color themes preserved"),
    (workflow, "V498Final.xcresult", "Simulator QA gates the IPA"),
]
for source, needle, title in checks:
    assert needle in source, title
    print("PASS", title)
assert "prefix(3200)" not in speech
assert 'method: "POST"' not in ads
for name in ["Instagram", "Facebook", "Telegram"]:
    directory = patch / "SocialAssets.xcassets" / f"Social{name}.imageset"
    manifest = json.loads((directory / "Contents.json").read_text())
    ET.parse(directory / manifest["images"][0]["filename"])
    print("PASS bundled vector", name)
print(f"{len(checks) + 5} local invariants passed; runtime verification remains separate.")
