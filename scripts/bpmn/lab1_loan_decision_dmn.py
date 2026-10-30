"""Generates lab1-loan-approval/src/main/resources/models/loan-decision.dmn (+ docs table)."""
from pathlib import Path

from dmn_builder import DecisionTable

t = DecisionTable("loan-decision", "Loan decision", "FIRST",
                  doc="Educational loan rules (not real credit policy). FIRST hit policy: rules are "
                      "evaluated top-down and the first match wins, so hard rejections come first.")
t.input("creditScore", "Credit score", "creditScore", "number")
t.input("dti", "Debt-to-income ratio", "debtToIncomeRatio", "number")
t.input("employment", "Employment status", "employmentStatus", "string")
t.input("amount", "Loan amount", "loanAmount", "number")
t.input("lti", "Loan-to-annual-income ratio", "loanToIncomeRatio", "number")
t.output("outcome", "Outcome", "outcome", "string", '"APPROVED","REJECTED","MANUAL_REVIEW"')
t.output("reason", "Reason", "reason", "string")
t.output("ruleId", "Rule ID", "ruleId", "string")

t.rule("Hard reject: credit score below the minimum of 550",
       ["< 550", "-", "-", "-", "-"], ['"REJECTED"', '"Credit score below minimum of 550"', '"LD-1"'])
t.rule("Hard reject: total monthly debt (incl. new loan) above 50% of income",
       ["-", "> 0.5", "-", "-", "-"], ['"REJECTED"', '"Debt-to-income ratio above 50%"', '"LD-2"'])
t.rule("Hard reject: no employment income to repay the loan",
       ["-", "-", '"UNEMPLOYED"', "-", "-"], ['"REJECTED"', '"No verifiable employment income"', '"LD-3"'])
t.rule("Large loans are never auto-approved",
       ["-", "-", "-", "> 100000", "-"], ['"MANUAL_REVIEW"', '"Amount above automatic approval limit of 100 000"', '"LD-4"'])
t.rule("Auto-approve: strong score, low debt burden, loan at most half of annual income",
       [">= 700", "<= 0.35", "-", "-", "<= 0.5"], ['"APPROVED"', '"Meets all automatic approval criteria"', '"LD-5"'])
t.rule("Everything else is borderline and needs an officer",
       ["-", "-", "-", "-", "-"], ['"MANUAL_REVIEW"', '"Borderline profile - loan officer judgement required"', '"LD-6"'])

root = Path(__file__).resolve().parents[2]
out = root / "lab1-loan-approval/src/main/resources/models/loan-decision.dmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(t.to_xml(), encoding="utf-8")
(root / "lab1-loan-approval/docs").mkdir(parents=True, exist_ok=True)
(root / "lab1-loan-approval/docs/dmn-loan-decision.md").write_text(t.to_markdown(), encoding="utf-8")
print("wrote", out)
