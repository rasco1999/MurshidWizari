"""Compile the actual offline rules, not a reimplementation, on the macOS runner."""
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] / "patches/V50OfflineExams.swift").read_text()
rules = source.split("\n@MainActor", 1)[0].replace("import SwiftUI\n", "").replace("import Security\n", "")
tests = r'''
func question(_ text: String = "ما ناتج الجمع؟", answer: String = "8", type: String = "text") -> V50OfflineQuestion {
    V50OfflineQuestion(id: 101, type: type, text: text, meaningWord: "", options: [],
        correctAnswer: answer, explanation: "", submitted: false, serverIndex: 1)
}
assert(V50OfflineRules.supports("mcq", optionCount: 2))
assert(!V50OfflineRules.supports("mcq", optionCount: 1))
assert(!V50OfflineRules.supports("genetics", optionCount: 0))
assert(V50OfflineRules.feedback(question(), answer: "٨.٠") == .correct)
assert(V50OfflineRules.feedback(question(), answer: "9") == .incorrect)
assert(V50OfflineRules.feedback(question(answer: "علي"), answer: "عَلِيّ.") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "ان الله"), answer: "إن الله") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "2*x"), answer: "x+x") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "23"), answer: "2+3") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "102"), answer: "10^2") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "15"), answer: "1,5") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "p"), answer: "P") == .needsServer)
assert(V50OfflineRules.feedback(question(answer: "عَلَم"), answer: "عِلْم") == .needsServer)
assert(V50OfflineRules.revision(question()) == V50OfflineRules.revision(question()))
assert(V50OfflineRules.revision(question()) != V50OfflineRules.revision(question("سؤال تغيّر")))
assert(V50OfflineRules.revision(question()) != V50OfflineRules.revision(question(answer: "9")))
let answer = V50OfflineAnswer(chapterID: 1, questionID: 101, serverIndex: 1,
    text: "8", optionID: 0, createdAt: Date(), revision: V50OfflineRules.revision(question()))
let encoded = try JSONEncoder().encode(answer)
let decoded = try JSONDecoder().decode(V50OfflineAnswer.self, from: encoded)
assert(decoded.revision == answer.revision)
print("PASS 17 iOS offline eligibility, feedback, exact-revision and durable-answer checks")
'''
with tempfile.TemporaryDirectory() as directory:
    file = Path(directory) / "offline_rules.swift"
    file.write_text(rules + tests)
    subprocess.run(["swift", str(file)], check=True)
