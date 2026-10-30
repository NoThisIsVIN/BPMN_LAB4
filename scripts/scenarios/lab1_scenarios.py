"""Writes the Lab 1 test scenarios to lab1-loan-approval/test-data/*.json (see scripts/run_scenarios.py)."""
import json
from pathlib import Path

d = Path(__file__).resolve().parents[2] / "lab1-loan-approval/test-data"
d.mkdir(parents=True, exist_ok=True)
APP = "http://localhost:8081"
RV = ["validationPassed", "validationErrors", "creditCheckStatus", "creditScore", "creditRiskBand", "creditCheckError",
      "debtToIncomeRatio", "loanToIncomeRatio", "loanDecision", "finalDecision", "decisionSource", "decisionReason",
      "offerId", "interestRate"]


def app(n, applicant, name, **kw):
    v = {"applicationId": f"LA-2026-{n:04d}", "applicantId": applicant, "fullName": name,
         "email": name.lower().replace(" ", ".") + "@example.org", "age": 35, "employmentStatus": "EMPLOYED",
         "monthlyIncome": 6200, "monthlyDebtPayments": 400, "loanAmount": 25000, "loanTermMonths": 60,
         "loanPurpose": "Home renovation"}
    v.update(kw)
    return v


def sc(fname, id, title, category, end, route, variables, steps=(), extra_rv=()):
    s = {"id": id, "title": title, "category": category, "processId": "loan-approval", "app": APP,
         "expected": {"endEvent": end, "route": route}, "variables": variables, "steps": list(steps),
         "reportVariables": RV + list(extra_rv)}
    (d / f"{fname}.json").write_text(json.dumps(s, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


sc("L1-01-auto-approved", "L1-01", "Strong applicant is approved automatically", "success",
   "EndEvent_LoanApproved", "Validate -> credit score 762 -> DMN LD-5 Approved -> offer -> notify -> Loan approved",
   app(1, "APP-1001", "Synthetic Applicant Alpha"),
   [{"do": "wait"}, {"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-01-approved"}])

sc("L1-02-dmn-rejected", "L1-02", "Low credit score is rejected by the DMN", "rejection",
   "EndEvent_LoanRejected", "Validate -> credit score 512 -> DMN LD-1 Rejected -> rejection notice -> Loan rejected",
   app(2, "APP-1002", "Synthetic Applicant Bravo", monthlyIncome=4100, monthlyDebtPayments=650, loanAmount=18000,
       loanTermMonths=48),
   [{"do": "wait"}, {"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-02-rejected"}])

sc("L1-03-manual-review-approved", "L1-03", "Borderline score goes to manual review, officer approves in Tasklist",
   "manual-review", "EndEvent_LoanApproved",
   "Validate -> score 648 -> DMN LD-6 Manual review -> officer approves -> offer -> Loan approved",
   app(3, "APP-1003", "Synthetic Applicant Charlie", monthlyIncome=5400, monthlyDebtPayments=900, loanAmount=30000,
       loanTermMonths=72, loanPurpose="Debt consolidation"),
   [{"do": "screenshot", "view": "tasklist-list", "name": "05-tasklist-task-list", "waitFor": "parked"},
    {"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-03-waiting-for-review"},
    {"do": "completeTask", "elementId": "Task_ManualReview",
     "variables": {"officerDecision": "APPROVED",
                   "officerComment": "Stable employment, affordable instalment; approved with standard terms."},
     "ui": {"radio": "Approve loan",
            "textarea": "Stable employment, affordable instalment; approved with standard terms."},
     "screenshot": "06-tasklist-task-form"},
    {"do": "wait"},
    {"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-03-approved-after-review"}],
   ["officerDecision", "officerComment"])

sc("L1-04-large-loan-officer-rejects", "L1-04", "Loan above 100 000 goes to manual review, officer rejects",
   "manual-review", "EndEvent_LoanRejected",
   "Validate -> score 731 -> DMN LD-4 Manual review -> officer rejects -> rejection notice -> Loan rejected",
   app(4, "APP-1004", "Synthetic Applicant Delta", monthlyIncome=15000, monthlyDebtPayments=500, loanAmount=150000,
       loanTermMonths=120, loanPurpose="Commercial property"),
   [{"do": "completeTask", "elementId": "Task_ManualReview",
     "variables": {"officerDecision": "REJECTED",
                   "officerComment": "Loan-to-income ratio 0.83 is too high for an unsecured loan."}},
    {"do": "wait"}],
   ["officerDecision", "officerComment"])

invalid = app(5, "APP-1005", "Synthetic Applicant Echo", age=16)
del invalid["email"]
sc("L1-05-invalid-data", "L1-05", "Missing e-mail and under-age applicant fail validation", "validation",
   "EndEvent_InvalidData",
   "Validate -> validationPassed=false -> notify incomplete -> Application rejected (invalid data)",
   invalid,
   [{"do": "wait"}, {"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-05-invalid-data"}])

sc("L1-06-credit-api-error", "L1-06", "Credit bureau returns HTTP 500 -> manual review -> officer approves",
   "failure-recovery", "EndEvent_LoanApproved",
   "Validate -> REST HTTP 500 -> error boundary CREDIT_API_ERROR -> manual review -> approve -> Loan approved",
   app(6, "APP-ERR-500", "Synthetic Applicant Foxtrot", monthlyIncome=5000, monthlyDebtPayments=300,
       loanAmount=20000, loanTermMonths=48, loanPurpose="Car purchase"),
   [{"do": "screenshot", "view": "operate-instance", "name": "04-operate-instance-L1-06-credit-api-error",
     "waitFor": "parked"},
    {"do": "completeTask", "elementId": "Task_ManualReview",
     "variables": {"officerDecision": "APPROVED",
                   "officerComment": "Bureau unavailable; payslips and bank statements verified manually."}},
    {"do": "wait"}],
   ["creditApiErrorCode", "officerDecision"])

sc("L1-07-credit-api-timeout", "L1-07", "Credit bureau times out (8 s > 5 s limit) -> manual review -> officer rejects",
   "failure-recovery", "EndEvent_LoanRejected",
   "Validate -> REST timeout (HTTP 408) -> error boundary -> manual review -> reject -> Loan rejected",
   app(7, "APP-TIMEOUT", "Synthetic Applicant Golf", monthlyIncome=3900, monthlyDebtPayments=800,
       loanAmount=22000, loanTermMonths=36, loanPurpose="Holiday"),
   [{"do": "completeTask", "elementId": "Task_ManualReview",
     "variables": {"officerDecision": "REJECTED",
                   "officerComment": "No credit data and instalment too high relative to income."}},
    {"do": "wait"}],
   ["creditApiErrorCode", "officerDecision"])

sc("L1-08-worker-outage-recovery", "L1-08",
   "Validation worker dependency down -> incident -> repaired -> retried -> approved", "failure-recovery",
   "EndEvent_LoanApproved",
   "Validate fails 3x (5 s back-off) -> INCIDENT -> fault switched off -> job retried -> Loan approved",
   app(8, "APP-1005", "Synthetic Applicant Hotel", monthlyIncome=7000, monthlyDebtPayments=200, loanAmount=20000,
       loanTermMonths=48),
   [{"do": "fault", "name": "validation-reference-data", "enabled": True, "before": True},
    {"do": "expectIncident", "elementId": "Task_ValidateApplicant"},
    {"do": "screenshot", "view": "operate-instance", "name": "07-operate-incident-L1-08"},
    {"do": "screenshot", "view": "operate-incident", "name": "08-operate-incident-detail-L1-08"},
    {"do": "screenshot", "view": "operate-incident-message", "name": "08-operate-incident-message-L1-08"},
    {"do": "fault", "name": "validation-reference-data", "enabled": False},
    {"do": "retryIncident"},
    {"do": "wait"},
    {"do": "screenshot", "view": "operate-instance", "name": "09-operate-recovered-L1-08"}])

sc("L1-09-malformed-variable-recovery", "L1-09",
   "Non-numeric income -> incident -> variable corrected -> retried -> approved", "failure-recovery",
   "EndEvent_LoanApproved",
   "Validate rejects monthlyIncome=\"five thousand\" -> INCIDENT (retries 0) -> variable set to 6800 -> retry -> Loan approved",
   app(9, "APP-1008", "Synthetic Applicant India", monthlyIncome="five thousand", monthlyDebtPayments=250,
       loanAmount=24000, loanTermMonths=60),
   [{"do": "expectIncident", "elementId": "Task_ValidateApplicant"},
    {"do": "screenshot", "view": "operate-incident", "name": "08-operate-incident-detail-L1-09-malformed-variable"},
    {"do": "screenshot", "view": "operate-incident-message", "name": "08-operate-incident-message-L1-09"},
    {"do": "setVars", "variables": {"monthlyIncome": 6800}},
    {"do": "retryIncident"},
    {"do": "wait"},
    {"do": "screenshot", "view": "operate-instance", "name": "09-operate-recovered-L1-09"}])

sc("L1-10-high-dti-rejected", "L1-10", "Debt-to-income above 50% is rejected by the DMN", "rejection",
   "EndEvent_LoanRejected", "Validate -> DTI 0.61 -> DMN LD-2 Rejected -> rejection notice -> Loan rejected",
   app(10, "APP-1001", "Synthetic Applicant Juliett", monthlyIncome=3000, monthlyDebtPayments=1200, loanAmount=15000,
       loanTermMonths=36),
   [{"do": "wait"}, {"do": "screenshot", "view": "operate-instance", "name": "10-operate-variables-L1-10"}])

print("wrote", sorted(p.name for p in d.glob("*.json")))
