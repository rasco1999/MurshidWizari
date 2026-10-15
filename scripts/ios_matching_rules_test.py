"""Execute the exact iPhone matching rules on the macOS build runner."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'patches/ExamViewV33.swift').read_text()
rules=source[source.index('enum V53MatchingRules {'):source.index('\nstruct V53MatchingInput:')]
tests=r'''
let def: JSON = ["left": ["1": "x-ray", "2": "security"], "right": ["a": "camera", "b": "machine"]]
assert(!V53MatchingRules.complete(def, V53MatchingRules.pairs("1-b")))
assert(!V53MatchingRules.complete(def, V53MatchingRules.pairs("1-b, 2-b")))
assert(!V53MatchingRules.complete(def, V53MatchingRules.pairs("1-b, 1-a")))
assert(!V53MatchingRules.complete(def, V53MatchingRules.pairs("1-b, 2-z")))
assert(!V53MatchingRules.complete(def, V53MatchingRules.pairs("1-b, 2-a junk")))
assert(V53MatchingRules.complete(def, V53MatchingRules.pairs("2-a, 1-b")))
assert(V53MatchingRules.encode(V53MatchingRules.pairs("2-a, 1-b")) == "1-b, 2-a")
let changed = V53MatchingRules.assign("2", "b", to: ["1":"b", "2":"a"])
assert(changed == ["2":"b"])
assert(!V53MatchingRules.complete(def, changed))
print("PASS 9 actual iPhone matching rule assertions")
'''
with tempfile.TemporaryDirectory() as tmp:
    file=Path(tmp)/'matching.swift'
    file.write_text('import Foundation\ntypealias JSON = [String: Any]\nfunc jString(_ value: Any?) -> String { value as? String ?? "" }\n'+rules+tests)
    subprocess.run(['swift',str(file)],check=True)
