#!/usr/bin/env python3
"""Validate an admin-authored CSV batch without touching the live database.

This is the offline validation stage. The website's actual admin import route
must enforce admin authorization, CSRF, grade ownership and database validation
before writing any row; none of those can be safely assumed here.
"""
import csv
import json
import sys
from pathlib import Path

REQUIRED = ("grade_id", "subject_id", "chapter_id", "type", "question_text", "correct_answer")
ALLOWED = {"mcq", "true_false", "text", "fill", "meaning"}


def prepare(path: Path) -> dict:
    problems = []
    rows = []
    seen = set()
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = sorted(set(REQUIRED).difference(reader.fieldnames or []))
        if missing:
            raise ValueError("أعمدة مفقودة: " + ", ".join(missing))
        for line_number, row in enumerate(reader, start=2):
            values = {key: (value or "").strip() for key, value in row.items() if key is not None}
            try:
                identifiers = {key: int(values.get(key, "")) for key in ("grade_id", "subject_id", "chapter_id")}
                if any(number <= 0 for number in identifiers.values()):
                    raise ValueError("يجب أن تكون معرّفات الصف والمادة والموضوع أرقامًا موجبة")
                kind = values.get("type", "")
                if kind not in ALLOWED:
                    raise ValueError("نوع سؤال غير مدعوم")
                question = values.get("question_text", "")
                answer = values.get("correct_answer", "")
                if len(question) < 8 or len(question) > 2500:
                    raise ValueError("نص السؤال يجب أن يكون بين 8 و2500 حرف")
                if not answer or len(answer) > 1200:
                    raise ValueError("الإجابة الصحيحة مطلوبة وبحد أقصى 1200 حرف")
                options = [x.strip() for x in values.get("options", "").split("|") if x.strip()]
                if kind == "mcq" and (len(options) < 2 or len(set(options)) != len(options) or answer not in options):
                    raise ValueError("سؤال الاختيارات يحتاج خيارين مختلفين على الأقل وأن تكون الإجابة ضمن الخيارات")
                key = (identifiers["grade_id"], identifiers["chapter_id"], question.casefold())
                if key in seen:
                    raise ValueError("سؤال مكرر في الدفعة")
                seen.add(key)
                rows.append({
                    **identifiers,
                    "type": kind,
                    "text": question,
                    "correct_answer": answer,
                    "options": options,
                    "explanation": values.get("explanation", "")[:4000],
                    "exam_year": values.get("exam_year", "")[:16],
                    "exam_round": values.get("exam_round", "")[:50],
                    "source_label": values.get("source_label", "")[:250],
                    "verified": False,
                    "status": "pending_admin_review",
                })
            except ValueError as exc:
                problems.append({"line": line_number, "error": str(exc)})
    return {"ready_for_admin_review": not problems, "valid_rows": len(rows), "errors": problems, "questions": rows if not problems else []}


def main() -> int:
    if len(sys.argv) != 2:
        print("الاستخدام: python3 prepare_question_import.py ملف_الأسئلة.csv", file=sys.stderr)
        return 2
    try:
        result = prepare(Path(sys.argv[1]))
    except (OSError, UnicodeError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["ready_for_admin_review"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
