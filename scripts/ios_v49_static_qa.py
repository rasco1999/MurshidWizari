#!/usr/bin/env python3
"""Fast, non-network preflight for the iPhone offline-review guardrails.

This does not replace Swift compilation, simulator QA, or human testing.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PATCH = ROOT / "patches"


def require(path: Path, needle: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    if needle not in source:
        raise AssertionError(f"{label}: missing {needle!r} in {path.name}")
    print(f"PASS {label}")


def main() -> None:
    study = PATCH / "V49StudyIntelligence.swift"
    auth = PATCH / "AuthViews.swift"
    api = PATCH / "APIClient.swift"
    app = PATCH / "MurshidWizariApp.swift"
    home = PATCH / "V31Features.swift"
    checks = [
        (study, "final class V49StudyStore", "Persistent per-student review store"),
        (study, "completeFileProtectionUntilFirstUserAuthentication", "Protected local storage"),
        (study, "let recentUserKey", "Isolated recent-account identity"),
        (study, "func eraseOnLogout", "Local deletion on logout"),
        (study, "func rememberGraded", "Cache only explicitly accepted grades"),
        (study, "func markRecall", "Local spaced recall"),
        (study, "struct V49OfflineAccessView", "Dedicated offline entry"),
        (study, ".deviceOwnerAuthentication", "Offline device authentication"),
        (study, "var subjectMastery", "Private subject mastery estimates"),
        (study, "var suggestedDailyTarget", "Adaptive study goal"),
        (study, "isExcludedFromBackup", "Exclude cached study content from cloud backup"),
        (study, "ShareLink(item:", "Opt-in shareable accomplishment"),
        (auth, 'grade["is_correct"] != nil', "Server grading is required before caching"),
        (auth, "V49StudyStore.shared.rememberGraded", "Official exam saves reviewed questions"),
        (api, "V49StudyStore.shared.bind(to: user)", "Cache bound to authenticated student"),
        (api, "V49StudyStore.shared.eraseOnLogout", "Logout erases cached review"),
        (app, "showingOfflineReview", "Offline entry available without bootstrap success"),
        (home, "NavigationLink(destination: V49StudyHubView())", "Home/account route to coach"),
    ]
    for path, needle, label in checks:
        require(path, needle, label)

    isolated = study.read_text(encoding="utf-8")
    forbidden = ['APIClient.shared.request(', '"api/progress.php"', 'subscribed = true',
                 'AVSpeechSynthesizer', 'V49QuestionSpeech']
    for needle in forbidden:
        if needle in isolated:
            raise AssertionError(f"Offline module must not change network entitlements: {needle}")
    print("PASS No API writes, quota increments, or fabricated subscription state in offline module")
    print(f"Passed {len(checks) + 1} static checks; Xcode build and simulator QA still required.")


if __name__ == "__main__":
    main()
