import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from review_question_bank import guarded_update

question = {'id': 424242, 'question_version': 3}
changes = {'question_text': {'before': 'الدور األول', 'after': 'الدور الأول'},
           'explanation': {'before': '   ', 'after': ''}}
print(json.dumps({'forward': guarded_update(question, changes),
                  'reverse': guarded_update(question, changes, reverse=True)}, ensure_ascii=False))
