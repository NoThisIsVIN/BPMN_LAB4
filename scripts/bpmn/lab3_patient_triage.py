"""Generates lab3-hospital-triage/src/main/resources/models/patient-appointment-triage.bpmn"""
from pathlib import Path

from bpmn_builder import (Diagram, decision, job, rest_attrs, rest_connector,
                          timer_def, user_task)

SCHED, SYS, CLIN = "Lane_SchedulingStaff", "Lane_AppointmentSystem", "Lane_ClinicalStaff"

d = Diagram(
    "patient-appointment-triage", "Patient Appointment & Triage",
    "Hospital Appointment & Patient Triage (synthetic educational scenario)",
    lanes=[(SCHED, "Scheduling staff", 1),
           (SYS, "Appointment system (automated)", 2),
           (CLIN, "Clinical staff", 3)],
    doc="Registers a synthetic appointment request, classifies it with educational triage rules (DMN), "
        "routes priority/urgent cases to clinical staff in Tasklist, retrieves slots from a mock scheduling "
        "API via the REST Connector and books an appointment. Not clinical guidance; synthetic data only.",
    version_tag="1.0.0",
)

NOTIFY_PATIENT = "triage.notify-patient"
NOTIFY_STAFF = "triage.notify-staff"

# ---------------- main (happy) line: lane SYS, row 1 ----------------
d.start("StartEvent_RequestReceived", "Appointment request received", SYS, 0, 1,
        doc="Variables: patientReference (SYN-P-####), patientAge, appointmentPreference {department, "
            "preferredDate, timeOfDay}, symptomsSummary, painLevel, chestPain, breathingDifficulty, "
            "highFever, symptomDurationDays, chronicCondition.")

d.task("Task_ValidateRegistration", "Validate registration", SYS, 1, 1,
       ext=job("triage.validate-registration"),
       doc="Java job worker. Checks mandatory registration fields and value ranges, normalises urgency "
           "indicators (missing flags -> false) and flattens the appointment preference. "
           "Outputs registrationValid, registrationErrors, department, preferredDate, timeOfDay.")

d.xor("Gateway_RegistrationComplete", "Registration complete?", SYS, 2, 1, label="bottom")

d.task("Task_ClassifyTriage", "Classify triage category", SYS, 3, 1, kind="rule",
       ext=decision("triage-category", "triageResult", outputs={
           "triageResult": "=triageResult",
           "urgencyCategory": "=triageResult.urgencyCategory",
           "triageReason": "=triageResult.triageReason",
           "maxWaitDays": "=triageResult.maxWaitDays"}),
       doc="Business rule task -> DMN decision 'triage-category' (hit policy FIRST). Educational rules only.")

d.xor("Gateway_UrgentNeeded", "Urgent assessment needed?", SYS, 4, 1, label="top", label_w=100)
d.xor("Gateway_PriorityNeeded", "Priority review needed?", SYS, 5, 1, label="top", label_w=100)

d.xor("Gateway_JoinPriority", None, SYS, 8, 1)
d.xor("Gateway_JoinUrgent", None, SYS, 9, 1)

d.task("Task_RequestAvailability", "Request appointment availability", SYS, 10, 1,
       ext=job("triage.request-availability"),
       doc="Java job worker. Builds the search window (today .. today + maxWaitDays, starting at the "
           "preferred date when it lies inside the window) and registers an availability request with the "
           "mock scheduling API. Output: availabilityRequestId, searchFromDate, searchToDate.")

d.task("Task_RetrieveSlots", "Retrieve available slots", SYS, 11, 1, color="rest",
       attrs=rest_attrs(),
       ext=rest_connector(
           "GET",
           '="http://localhost:8083/mock/scheduling/availability-requests/" + availabilityRequestId + "/slots"',
           result_expression="={availableSlots: response.body.slots, slotCount: count(response.body.slots)}",
           retries=3, retry_backoff="PT5S", read_timeout=10, connect_timeout=5),
       doc="REST Outbound Connector -> GET /mock/scheduling/availability-requests/{id}/slots. No error "
           "expression: an outage (HTTP 503) is retried 3x with 5 s back-off and then raises an incident.")

d.xor("Gateway_SlotAvailable", "Suitable slot available?", SYS, 12, 1, label="bottom", label_w=100)

d.task("Task_BookAppointment", "Book appointment", SYS, 13, 1,
       ext=job("triage.book-appointment"),
       doc="Java job worker. Books the first available slot through the mock scheduling API. "
           "Outputs bookingStatus, bookingReference, appointmentSlot.")
d.xor("Gateway_JoinBooked", None, SYS, 14, 1)
d.task("Task_SendConfirmation", "Send appointment confirmation", SYS, 15, 1, kind="send",
       ext=job(NOTIFY_PATIENT, headers={"template": "APPOINTMENT_CONFIRMED"}))
d.end("EndEvent_AppointmentBooked", "Appointment booked", SYS, 16, 1, color="success")

# ---------------- SYS row 0: incomplete registration ----------------
d.task("Task_RequestMissingDetails", "Request missing details", SYS, 3, 0, kind="send",
       ext=job(NOTIFY_PATIENT, headers={"template": "MISSING_DETAILS"}))
d.end("EndEvent_RequestIncomplete", "Request returned – incomplete", SYS, 4, 0, color="failure",
      label="right", label_w=100)

# ---------------- clinical staff lane ----------------
# row 0: priority review
d.task("Task_PriorityReview", "Priority staff review", CLIN, 6, 0, kind="user",
       ext=user_task("priority-review-form", "clinical-staff", priority=70, outputs={
           "reviewOutcome": "=reviewOutcome",
           "reviewNotes": "=reviewNotes",
           "urgencyCategory": '=if reviewOutcome = "ESCALATE_URGENT" then "URGENT_ASSESSMENT" else urgencyCategory',
           "maxWaitDays": '=if reviewOutcome = "ESCALATE_URGENT" then 0 else maxWaitDays'}),
       doc="Camunda user task, candidate group clinical-staff. Form: priority-review-form.")
d.xor("Gateway_ReviewOutcome", "Review outcome?", CLIN, 7, 0, label="top", label_w=80)

# row 1: urgent assessment
d.xor("Gateway_JoinAssessment", None, CLIN, 7, 1)
d.task("Task_UrgentAssessment", "Immediate clinical assessment", CLIN, 8, 1, kind="user",
       ext=user_task("urgent-assessment-form", "urgent-care-team", priority=100, outputs={
           "assessmentOutcome": "=assessmentOutcome",
           "assessmentNotes": "=assessmentNotes"}),
       doc="Camunda user task, candidate group urgent-care-team, highest priority. Form: urgent-assessment-form.")
d.boundary("Boundary_NotPickedUp", "Not picked up in 15 min", "Task_UrgentAssessment",
           timer_def("PT15M"), side="bottom", offset=0, interrupting=False, label="right", label_w=80,
           label_dy=30)
d.xor("Gateway_AssessmentOutcome", "Assessment outcome?", CLIN, 9, 1, label="bottom", label_w=90)
d.task("Task_HandOverED", "Hand over to emergency department", CLIN, 11, 1, kind="send",
       ext=job(NOTIFY_STAFF, headers={"template": "ED_HANDOVER"}))
d.end("EndEvent_EmergencyReferral", "Referred to emergency care", CLIN, 12, 1, color="neutral")

# row 2: escalation timer
d.task("Task_EscalateDutyManager", "Escalate to duty manager", CLIN, 9, 2, kind="send",
       ext=job(NOTIFY_STAFF, headers={"template": "DUTY_MANAGER_ESCALATION"}))
d.end("EndEvent_DutyManagerAlerted", "Duty manager alerted", CLIN, 10, 2, color="neutral")

# ---------------- scheduling staff lane ----------------
d.task("Task_ResolveScheduling", "Resolve scheduling – no suitable slot", SCHED, 13, 0, kind="user",
       ext=user_task("scheduling-resolution-form", "scheduling-staff", priority=60, outputs={
           "schedulingOutcome": "=schedulingOutcome",
           "schedulingNotes": "=schedulingNotes",
           "manualSlot": "=manualSlot",
           "bookingStatus": '=if schedulingOutcome = "MANUAL_SLOT" then "CONFIRMED_MANUAL" else "WAITLISTED"',
           "appointmentSlot": '=if schedulingOutcome = "MANUAL_SLOT" then manualSlot else null'}),
       doc="Camunda user task, candidate group scheduling-staff. Form: scheduling-resolution-form.")
d.xor("Gateway_SchedulingOutcome", "Scheduling outcome?", SCHED, 14, 0, label="right", label_w=76,
      label_dy=-26)
d.task("Task_NotifyWaitlisted", "Notify patient: added to waiting list", SCHED, 15, 0, kind="send",
       ext=job(NOTIFY_PATIENT, headers={"template": "WAITLISTED"}))
d.end("EndEvent_Waitlisted", "Patient waitlisted", SCHED, 16, 0, color="failure")

# ---------------- sequence flows ----------------
d.flow("Flow_Start_Validate", "StartEvent_RequestReceived", "Task_ValidateRegistration")
d.flow("Flow_Validate_Gw", "Task_ValidateRegistration", "Gateway_RegistrationComplete")
d.flow("Flow_Registration_Yes", "Gateway_RegistrationComplete", "Task_ClassifyTriage", "Yes",
       condition="=registrationValid = true")
d.flow("Flow_Registration_No", "Gateway_RegistrationComplete", "Task_RequestMissingDetails", "No", default=True)
d.flow("Flow_Missing_End", "Task_RequestMissingDetails", "EndEvent_RequestIncomplete")
d.flow("Flow_Triage_Urgent", "Task_ClassifyTriage", "Gateway_UrgentNeeded")

d.flow("Flow_Urgent_Yes", "Gateway_UrgentNeeded", "Gateway_JoinAssessment", "Yes – urgent",
       condition='=urgencyCategory = "URGENT_ASSESSMENT"')
d.flow("Flow_Urgent_No", "Gateway_UrgentNeeded", "Gateway_PriorityNeeded", "No", default=True)
d.flow("Flow_Priority_Yes", "Gateway_PriorityNeeded", "Task_PriorityReview", "Yes – priority",
       condition='=urgencyCategory = "PRIORITY_REVIEW"')
d.flow("Flow_Priority_No", "Gateway_PriorityNeeded", "Gateway_JoinPriority", "No – routine", default=True)

d.flow("Flow_Review_Gw", "Task_PriorityReview", "Gateway_ReviewOutcome")
d.flow("Flow_Review_Book", "Gateway_ReviewOutcome", "Gateway_JoinPriority", "Book appointment",
       default=True, exit="right", entry="bottom", label_at=(d.x(7) + 30, d.y(CLIN, 0) - 20))
d.flow("Flow_Review_Escalate", "Gateway_ReviewOutcome", "Gateway_JoinAssessment", "Escalate",
       condition='=reviewOutcome = "ESCALATE_URGENT"', exit="bottom", entry="top")

d.flow("Flow_JoinAssessment_Task", "Gateway_JoinAssessment", "Task_UrgentAssessment")
d.flow("Flow_Assessment_Gw", "Task_UrgentAssessment", "Gateway_AssessmentOutcome")
d.flow("Flow_Assessment_Book", "Gateway_AssessmentOutcome", "Gateway_JoinUrgent", "Book urgent slot",
       condition='=assessmentOutcome = "BOOK_URGENT_SLOT"', exit="top", entry="bottom")
d.flow("Flow_Assessment_ED", "Gateway_AssessmentOutcome", "Task_HandOverED", "Emergency referral",
       default=True)
d.flow("Flow_HandOver_End", "Task_HandOverED", "EndEvent_EmergencyReferral")
d.flow("Flow_Timer_Escalate", "Boundary_NotPickedUp", "Task_EscalateDutyManager")
d.flow("Flow_Escalate_End", "Task_EscalateDutyManager", "EndEvent_DutyManagerAlerted")

d.flow("Flow_JoinPriority_JoinUrgent", "Gateway_JoinPriority", "Gateway_JoinUrgent")
d.flow("Flow_JoinUrgent_Request", "Gateway_JoinUrgent", "Task_RequestAvailability")
d.flow("Flow_Request_Slots", "Task_RequestAvailability", "Task_RetrieveSlots")
d.flow("Flow_Slots_Gw", "Task_RetrieveSlots", "Gateway_SlotAvailable")
d.flow("Flow_Slot_Yes", "Gateway_SlotAvailable", "Task_BookAppointment", "Yes", condition="=slotCount > 0")
d.flow("Flow_Slot_No", "Gateway_SlotAvailable", "Task_ResolveScheduling", "No", default=True)
d.flow("Flow_Book_JoinBooked", "Task_BookAppointment", "Gateway_JoinBooked")
d.flow("Flow_Resolve_Gw", "Task_ResolveScheduling", "Gateway_SchedulingOutcome")
d.flow("Flow_Scheduling_Manual", "Gateway_SchedulingOutcome", "Gateway_JoinBooked", "Manual slot",
       condition='=schedulingOutcome = "MANUAL_SLOT"', exit="bottom", entry="top")
d.flow("Flow_Scheduling_Waitlist", "Gateway_SchedulingOutcome", "Task_NotifyWaitlisted", "Waiting list",
       default=True, label_at=(d.x(14) + 30, d.y(SCHED, 0) + 6))
d.flow("Flow_Waitlist_End", "Task_NotifyWaitlisted", "EndEvent_Waitlisted")
d.flow("Flow_JoinBooked_Confirm", "Gateway_JoinBooked", "Task_SendConfirmation")
d.flow("Flow_Confirm_End", "Task_SendConfirmation", "EndEvent_AppointmentBooked")

# ---------------- annotations ----------------
d.annotate("Annotation_DmnRules",
           "DMN triage-category (FIRST hit):\n"
           "• chest pain / breathing difficulty / pain ≥ 8 → Urgent\n"
           "• high fever and age ≥ 65 → Priority review\n"
           "• pain 5–7 or symptoms > 14 days → Priority review\n"
           "• otherwise → Routine (max wait 14 days)",
           d.x(6) - 40, d.y(SYS, 0) - 44, w=340)
d.annotate("Annotation_SchedulingOutage",
           "Scheduling API outage (HTTP 503)\n→ 3 retries, 5 s back-off\n→ incident in Operate → fix → retry",
           d.x(9) - 30, d.y(SYS, 0) - 30, w=230)
d.annotate("Annotation_Disclaimer",
           "Educational rules only – not clinical guidance.\nAll patient data is synthetic (SYN-P-####).",
           d.x(10) - 20, d.y(CLIN, 0) - 22, w=300)
d.annotate("Annotation_Legend",
           "Legend\n"
           "green task = Java job worker\n"
           "blue task = REST connector\n"
           "purple task = DMN decision\n"
           "orange task = human task (Tasklist)\n"
           "grey task = notification",
           d.x(12) + 10, d.y(CLIN, 2) - 56, w=220)

out = Path(__file__).resolve().parents[2] / "lab3-hospital-triage/src/main/resources/models/patient-appointment-triage.bpmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(d.to_xml(), encoding="utf-8")
print("wrote", out)
