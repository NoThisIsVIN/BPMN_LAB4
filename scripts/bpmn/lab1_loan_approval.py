"""Generates lab1-loan-approval/src/main/resources/models/loan-approval.bpmn"""
from pathlib import Path

from bpmn_builder import (Diagram, decision, error_def, job, rest_attrs,
                          rest_connector, timer_def, user_task)

SYS, OFF = "Lane_LoanSystem", "Lane_LoanOfficer"

d = Diagram(
    "loan-approval", "Loan Approval", "Loan Approval – Bank (educational scenario)",
    lanes=[(SYS, "Loan system (automated)", 2), (OFF, "Loan officer", 2)],
    doc="End-to-end loan approval: validation worker, credit-bureau REST connector, "
        "DMN loan decision, manual review in Tasklist. Synthetic data only.",
    version_tag="1.0.0",
)
d.error("Error_CreditApi", "Credit bureau API error", "CREDIT_API_ERROR")

# ---------------- main (happy) line: lane SYS, row 1 ----------------
d.start("StartEvent_ApplicationReceived", "Loan application received", SYS, 0, 1,
        doc="Instance variables: applicationId, applicantId, fullName, email, age, employmentStatus, "
            "monthlyIncome, monthlyDebtPayments, loanAmount, loanTermMonths, loanPurpose.")

d.task("Task_ValidateApplicant", "Validate applicant data", SYS, 1, 1,
       ext=job("loan.validate-applicant", retries=3),
       doc="Java job worker. Checks mandatory fields and ranges, computes monthlyInstallment, "
           "debtToIncomeRatio and loanToIncomeRatio. Outputs validationPassed, validationErrors.")

d.xor("Gateway_DataValid", "Applicant data valid?", SYS, 2, 1, label="bottom")

d.task("Task_CreditAssessment", "Request credit assessment", SYS, 3, 1, color="rest",
       attrs=rest_attrs(),
       ext=rest_connector(
           "POST", "http://localhost:8081/mock/credit-bureau/assessments",
           body='={applicationId: applicationId, applicantId: applicantId, fullName: fullName, '
                'monthlyIncome: monthlyIncome, monthlyDebtPayments: monthlyDebtPayments, loanAmount: loanAmount}',
           result_expression='={creditCheckStatus: "COMPLETED", creditScore: response.body.creditScore, '
                             'creditRiskBand: response.body.riskBand, creditBureauReference: response.body.bureauReference, '
                             'activeCreditAccounts: response.body.activeCreditAccounts}',
           error_expression='=if error != null then bpmnError("CREDIT_API_ERROR", "Credit bureau call failed", '
                            '{creditApiErrorCode: string(error.code), creditCheckError: "HTTP " + string(error.code) + ": " + '
                            '(if error.variables.response.body.error != null then string(error.variables.response.body.error) '
                            'else string(error.message))}) else null',
           retries=1, read_timeout=5, connect_timeout=5),
       doc="REST Outbound Connector -> POST /mock/credit-bureau/assessments (mock credit bureau). "
           "Any HTTP error or timeout (5 s) is mapped to BPMN error CREDIT_API_ERROR.")

d.boundary("Boundary_CreditApiError", "Credit bureau unavailable", "Task_CreditAssessment",
           error_def("Error_CreditApi"), side="bottom", offset=0, label="right", label_w=90, label_dy=36,
           ext='<zeebe:ioMapping>\n<zeebe:output source="=&quot;UNAVAILABLE&quot;" target="creditCheckStatus" />\n'
               '<zeebe:output source="=creditCheckError" target="creditCheckError" />\n</zeebe:ioMapping>')

d.task("Task_LoanDecision", "Determine loan decision", SYS, 4, 1, kind="rule",
       ext=decision("loan-decision", "loanDecision", outputs={
           "loanDecision": "=loanDecision",
           "finalDecision": "=loanDecision.outcome",
           "decisionReason": "=loanDecision.reason",
           "decisionSource": '="AUTOMATED (DMN)"'}),
       doc="Business rule task -> DMN decision 'loan-decision' (hit policy FIRST).")

d.xor("Gateway_DecisionOutcome", "Decision outcome?", SYS, 5, 1, label="topleft", label_w=70, label_dx=-4)

d.xor("Gateway_JoinApproval", None, SYS, 7, 1)
d.xor("Gateway_LoanApproved", "Loan approved?", SYS, 8, 1, label="bottom")

d.task("Task_GenerateOffer", "Generate loan offer", SYS, 9, 1,
       ext=job("loan.generate-offer"),
       doc="Java job worker. Produces offerId, interestRate, monthlyInstallment and offerValidUntil.")
d.task("Task_NotifyApproved", "Notify applicant: loan approved", SYS, 10, 1, kind="send",
       ext=job("loan.notify-applicant", headers={"template": "LOAN_APPROVED"}))
d.end("EndEvent_LoanApproved", "Loan approved", SYS, 11, 1, color="success")

# ---------------- top row of SYS: invalid data + rejection track ----------------
d.task("Task_NotifyIncomplete", "Notify applicant: application incomplete", SYS, 3, 0, kind="send",
       ext=job("loan.notify-applicant", headers={"template": "APPLICATION_INCOMPLETE"}))
d.end("EndEvent_InvalidData", "Application rejected – invalid data", SYS, 4, 0, color="failure", label_w=100)

d.xor("Gateway_JoinRejection", None, SYS, 8, 0)
d.task("Task_NotifyRejected", "Send rejection notice", SYS, 9, 0, kind="send",
       ext=job("loan.notify-applicant", headers={"template": "LOAN_REJECTED"}))
d.end("EndEvent_LoanRejected", "Loan rejected", SYS, 10, 0, color="failure")

# ---------------- loan officer lane ----------------
d.xor("Gateway_JoinReview", None, OFF, 5, 0)
d.task("Task_ManualReview", "Review loan application", OFF, 6, 0, kind="user",
       ext=user_task("loan-review-form", "loan-officers", priority=70, outputs={
           "officerDecision": "=officerDecision",
           "officerComment": "=officerComment",
           "finalDecision": "=officerDecision",
           "decisionReason": "=officerComment",
           "decisionSource": '="LOAN OFFICER"'}),
       doc="Camunda user task (Tasklist), candidate group loan-officers. Form: loan-review-form.")
d.boundary("Boundary_ReviewOverdue", "Review pending 24 h", "Task_ManualReview",
           timer_def("PT24H"), side="bottom", offset=0, interrupting=False, label="right", label_w=70, label_dy=26,
           color="alert")
d.task("Task_RemindOfficers", "Send reminder to loan officers", OFF, 7, 1, kind="send",
       ext=job("loan.notify-officers", headers={"template": "REVIEW_OVERDUE"}))
d.end("EndEvent_ReminderSent", "Reminder sent", OFF, 8, 1, color="neutral")

# ---------------- sequence flows ----------------
d.flow("Flow_Start_Validate", "StartEvent_ApplicationReceived", "Task_ValidateApplicant")
d.flow("Flow_Validate_Gw", "Task_ValidateApplicant", "Gateway_DataValid")
d.flow("Flow_DataValid_Yes", "Gateway_DataValid", "Task_CreditAssessment", "Yes",
       condition="=validationPassed = true")
d.flow("Flow_DataValid_No", "Gateway_DataValid", "Task_NotifyIncomplete", "No", default=True)
d.flow("Flow_Incomplete_End", "Task_NotifyIncomplete", "EndEvent_InvalidData")
d.flow("Flow_Credit_Decision", "Task_CreditAssessment", "Task_LoanDecision")
d.flow("Flow_Decision_Gw", "Task_LoanDecision", "Gateway_DecisionOutcome")
d.flow("Flow_Outcome_Approved", "Gateway_DecisionOutcome", "Gateway_JoinApproval", "Approved",
       condition='=loanDecision.outcome = "APPROVED"')
d.flow("Flow_Outcome_Rejected", "Gateway_DecisionOutcome", "Gateway_JoinRejection", "Rejected",
       condition='=loanDecision.outcome = "REJECTED"', exit="top", entry="left",
       via=[(d.x(5), d.y(SYS, 0))])
d.flow("Flow_Outcome_Manual", "Gateway_DecisionOutcome", "Gateway_JoinReview", "Manual review",
       default=True, exit="bottom", entry="top")
d.flow("Flow_CreditError_Review", "Boundary_CreditApiError", "Gateway_JoinReview",
       label_at=(d.x(3) + 8, d.y(OFF, 0) - 20))
d.flow("Flow_JoinReview_Task", "Gateway_JoinReview", "Task_ManualReview")
d.flow("Flow_Review_JoinApproval", "Task_ManualReview", "Gateway_JoinApproval", exit="right", entry="bottom")
d.flow("Flow_Overdue_Remind", "Boundary_ReviewOverdue", "Task_RemindOfficers")
d.flow("Flow_Remind_End", "Task_RemindOfficers", "EndEvent_ReminderSent")
d.flow("Flow_JoinApproval_Gw", "Gateway_JoinApproval", "Gateway_LoanApproved")
d.flow("Flow_Approved_Yes", "Gateway_LoanApproved", "Task_GenerateOffer", "Yes",
       condition='=finalDecision = "APPROVED"')
d.flow("Flow_Approved_No", "Gateway_LoanApproved", "Gateway_JoinRejection", "No", default=True,
       exit="top", entry="bottom")
d.flow("Flow_JoinRejection_Notify", "Gateway_JoinRejection", "Task_NotifyRejected")
d.flow("Flow_NotifyRejected_End", "Task_NotifyRejected", "EndEvent_LoanRejected")
d.flow("Flow_Offer_Notify", "Task_GenerateOffer", "Task_NotifyApproved")
d.flow("Flow_NotifyApproved_End", "Task_NotifyApproved", "EndEvent_LoanApproved")

# ---------------- annotations (rule summaries) ----------------
d.annotate("Annotation_DmnRules",
           "DMN loan-decision (FIRST hit):\n"
           "• score < 550 or DTI > 50% or unemployed → Rejected\n"
           "• amount > 100 000 → Manual review\n"
           "• score ≥ 700, DTI ≤ 35%, LTI ≤ 0.5 → Approved\n"
           "• otherwise → Manual review",
           d.x(1) - 50, d.y(OFF, 1) - 40, w=340)
d.annotate("Annotation_CreditError",
           "HTTP error or 5 s timeout\n→ BPMN error CREDIT_API_ERROR\n→ officer decides without score",
           d.x(9) + 10, d.y(OFF, 0) - 40, w=210)

d.annotate("Annotation_Legend",
           "Legend\n"
           "green task = Java job worker\n"
           "blue task = REST connector\n"
           "purple task = DMN decision\n"
           "orange task = human task (Tasklist)\n"
           "grey task = notification",
           d.x(9) + 10, d.y(OFF, 1) - 56, w=220)

out = Path(__file__).resolve().parents[2] / "lab1-loan-approval/src/main/resources/models/loan-approval.bpmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(d.to_xml(), encoding="utf-8")
print("wrote", out)
