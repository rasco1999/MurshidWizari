#!/usr/bin/env python3
"""Small, asserted build-time iOS UI refinements. Never modifies unrelated views."""
from pathlib import Path
import sys

def exact(s, old, new):
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one insertion point, found {count}: {old[:75]!r}")
    return s.replace(old, new, 1)

def student():
    p = Path("source/MurshidWizari/AuthViews.swift")
    s = p.read_text()
    s = exact(s,
        "        content()\n            .opacity(selection == index ? 1 : 0)\n            .allowsHitTesting(selection == index)",
        "        content()\n            .opacity(selection == index ? 1 : 0)\n            .animation(.easeOut(duration: 0.12), value: selection)\n            .allowsHitTesting(selection == index)")
    p.write_text(s)

def admin():
    p = Path("admin_ios/App/MurshidAdminApp.swift")
    s = p.read_text()
    s = exact(s, "    func refresh(_ area:String,search:String?=nil) async {",
        """    private var recentlyLoaded: [String: Date] = [:]
    // Native tabs stay responsive: re-entry within 12 seconds reuses displayed data.
    // Search, pull-to-refresh and explicit mutations still use the network.
    func refreshIfStale(_ area:String) async {
        if let last = recentlyLoaded[area], Date().timeIntervalSince(last) < 12 { return }
        await refresh(area)
    }
    func refresh(_ area:String,search:String?=nil) async {""")
    s = exact(s, '            default: break\n            }\n        } catch { self.error=error.localizedDescription }\n    }\n    func refreshPredictions',
        '            default: break\n            }\n            if search == nil || search == "" { recentlyLoaded[area] = Date() }\n        } catch { self.error=error.localizedDescription }\n    }\n    func refreshPredictions')
    for area in ("dashboard", "codes", "ads", "students", "subscriptions", "questions", "curriculum"):
        s = s.replace('.task{await state.refresh("'+area+'")}', '.task{await state.refreshIfStale("'+area+'")}')
        s = s.replace('.task {await state.refresh("'+area+'")}', '.task {await state.refreshIfStale("'+area+'")}')
    s = exact(s, '    func refreshPredictions(subjectID:Int) async {',
      '    private var currentPredictionRequestSubject = 0\n    func refreshPredictions(subjectID:Int) async {')
    s = exact(s, '        guard subjectID>0 else {predictionRows=[];return}',
      '        currentPredictionRequestSubject = subjectID\n        guard subjectID>0 else {predictionRows=[];return}')
    s = exact(s,
      '            let result=try await call("predictions",extras:[URLQueryItem(name:"subject_id",value:String(subjectID))])\n            predictionRows=list(result)',
      '            let result=try await call("predictions",extras:[URLQueryItem(name:"subject_id",value:String(subjectID))])\n            guard currentPredictionRequestSubject == subjectID else { return }\n            predictionRows=list(result)')
    # Preserve the actual error for the latest subject instead of a stale request.
    s = exact(s, '        } catch {self.error=error.localizedDescription}\n    }\n    func mutation',
      '        } catch {\n            if currentPredictionRequestSubject == subjectID { self.error=error.localizedDescription }\n        }\n    }\n    func mutation')
    p.write_text(s)

if __name__ == "__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--student","--admin"): raise SystemExit("Use --student or --admin")
    (student if sys.argv[1]=="--student" else admin)()
