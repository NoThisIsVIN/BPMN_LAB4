"""Generates lab3-hospital-triage/src/main/resources/models/triage-category.dmn (+ docs table)."""
from pathlib import Path

from dmn_builder import DecisionTable

t = DecisionTable("triage-category", "Triage category", "FIRST",
                  doc="Predefined EDUCATIONAL triage rules for a synthetic scenario - not clinical guidance. "
                      "FIRST hit policy: the most urgent rules are listed first, the catch-all Routine rule last.")
t.input("chestPain", "Chest pain", "chestPain", "boolean")
t.input("breathing", "Breathing difficulty", "breathingDifficulty", "boolean")
t.input("pain", "Pain level (0-10)", "painLevel", "number")
t.input("fever", "High fever", "highFever", "boolean")
t.input("age", "Patient age", "patientAge", "number")
t.input("duration", "Symptom duration (days)", "symptomDurationDays", "number")
t.output("category", "Urgency category", "urgencyCategory", "string",
         '"ROUTINE","PRIORITY_REVIEW","URGENT_ASSESSMENT"')
t.output("reason", "Triage reason", "triageReason", "string")
t.output("wait", "Max wait (days)", "maxWaitDays", "number")
t.output("ruleId", "Rule ID", "ruleId", "string")

t.rule("Red flag: chest pain always needs immediate human assessment",
       ["true", "-", "-", "-", "-", "-"],
       ['"URGENT_ASSESSMENT"', '"Chest pain reported"', "0", '"TR-1"'])
t.rule("Red flag: breathing difficulty",
       ["-", "true", "-", "-", "-", "-"],
       ['"URGENT_ASSESSMENT"', '"Breathing difficulty reported"', "0", '"TR-2"'])
t.rule("Severe pain (8-10)",
       ["-", "-", ">= 8", "-", "-", "-"],
       ['"URGENT_ASSESSMENT"', '"Severe pain (8 or more out of 10)"', "0", '"TR-3"'])
t.rule("High fever in an older patient (65+) is reviewed by staff within 2 days",
       ["-", "-", "-", "true", ">= 65", "-"],
       ['"PRIORITY_REVIEW"', '"High fever in patient aged 65 or over"', "2", '"TR-4"'])
t.rule("Moderate pain (5-7) is reviewed by staff within 3 days",
       ["-", "-", "[5..7]", "-", "-", "-"],
       ['"PRIORITY_REVIEW"', '"Moderate pain (5-7 out of 10)"', "3", '"TR-5"'])
t.rule("Symptoms lasting more than two weeks are reviewed within 5 days",
       ["-", "-", "-", "-", "-", "> 14"],
       ['"PRIORITY_REVIEW"', '"Symptoms persisting for more than 14 days"', "5", '"TR-6"'])
t.rule("No red flags or priority indicators: routine appointment within 14 days",
       ["-", "-", "-", "-", "-", "-"],
       ['"ROUTINE"', '"No urgency indicators present"', "14", '"TR-7"'])

root = Path(__file__).resolve().parents[2]
out = root / "lab3-hospital-triage/src/main/resources/models/triage-category.dmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(t.to_xml(), encoding="utf-8")
(root / "lab3-hospital-triage/docs").mkdir(parents=True, exist_ok=True)
(root / "lab3-hospital-triage/docs/dmn-triage-category.md").write_text(t.to_markdown(), encoding="utf-8")
print("wrote", out)
