"""Non-network checks supplement, but never replace, simulator/Xcode tests."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
patch = root / "patches"
ads = (patch / "V498LiveSchoolAds.swift").read_text()
auth = (patch / "AuthViews.swift").read_text()
home = (patch / "V31Features.swift").read_text()
api = (patch / "APIClient.swift").read_text()
workflow = (root / ".github/workflows/build-ios.yml").read_text()
checks = [
    (ads, "15_000_000_000", "Foreground ad polling"),
    (ads, "generation == expectedGeneration", "Account-switch response guard"),
    (ads, 'request("mobile/bootstrap.php", fresh: true)', "Uncached canonical active ads"),
    (api, ".reloadIgnoringLocalCacheData", "Bypass HTTP cache on live ad reads"),
    (auth, ".task(id: adsMonitorKey)", "Cancel/restart polling with scene/account/connectivity"),
    (auth, "V498UnreadAdBadge(count: schoolAds.unreadCount)", "Red badge attached to Ads button"),
    (auth, "if selection == index && !keyboardVisible { bottomBar }", "Only selected pane exposes tab controls"),
    (home, "V498SocialLinksFooter()", "Compact branded student footer"),
    (home, "StudentPalette.allCases", "Build506 color themes preserved"),
    (workflow, "V498Final.xcresult", "Simulator QA gates the IPA"),
    (workflow, "Match simulator launch metadata", "QA uses production fullscreen layout"),
]
for source, needle, title in checks:
    assert needle in source, title
    print("PASS", title)
assert all("V49QuestionSpeech" not in (patch / x).read_text() for x in ["AuthViews.swift","V31Features.swift","V49StudyIntelligence.swift","V3Exam.swift"])
assert "V498SpeechButton" not in auth
assert 'method: "POST"' not in ads
for name in ["Instagram", "Facebook", "Telegram"]:
    directory = patch / "SocialAssets.xcassets" / f"Social{name}.imageset"
    manifest = json.loads((directory / "Contents.json").read_text())
    ET.parse(directory / manifest["images"][0]["filename"])
    print("PASS bundled vector", name)
print(f"{len(checks) + 5} local invariants passed; runtime verification remains separate.")

