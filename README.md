# PBJ-2 Practical Assignment – Camunda 8: End-to-End Workflow Automation (Labs 1–3)

Three separate workflow implementations on **Camunda 8** (Zeebe, Operate, Tasklist, Connectors). Each one has a BPMN
process, a DMN decision table, Tasklist forms, a **Java / Spring Boot** job-worker application with a mock REST API, a
**REST Outbound Connector**, test scenarios executed against the running engine, and real Operate/Tasklist screenshots,
including incident investigation and recovery.

| Lab | Process | Report | Scenarios |
|---|---|---|---|
| 1 – Loan approval | `loan-approval` + DMN `loan-decision` | [lab1-loan-approval/REPORT.md](lab1-loan-approval/REPORT.md) | 17 / 17 PASS |
| 2 – E-commerce order fulfilment | `order-fulfilment` + DMN `order-priority` | [lab2-order-fulfilment/REPORT.md](lab2-order-fulfilment/REPORT.md) | 12 / 12 PASS |
| 3 – Hospital appointment & triage | `patient-appointment-triage` + DMN `triage-category` | [lab3-hospital-triage/REPORT.md](lab3-hospital-triage/REPORT.md) | 12 / 12 PASS |

All data is **synthetic**. There are no real payment credentials, secrets or patient data, and the decision rules are educational.

Each process also models **real-world pitfalls and their solutions** rather than only the happy path:

| Lab | Pitfall → solution modelled in BPMN |
|---|---|
| 1 | primary credit bureau (CIBIL-like) down → **backup bureau**; both down → officer decides without a score · incomplete application → **ask the applicant**, re-validate, close after 7 days · identity (KYC) check · officer idle → reminder (24 h) → **senior-officer escalation** (48 h) · offer must be **accepted within 14 days** |
| 2 | primary warehouse short → **secondary warehouse** · primary payment provider down → **backup provider**; both down → incident · card declined → **customer sends another payment method** (30 min, max 2 attempts) · carrier API down → **backup carrier** · stock released on every cancellation |
| 3 | main hospital full → **partner clinic** · slot taken while booking → **automatic re-query** · patient must **confirm**: reminder (12 h), slot **released** after 24 h · urgent case not picked up → **duty-manager escalation** (15 min) |

All timers read their duration from a process variable with the business value as default, so the tests could fire every timer in seconds.

## Results of all experiments

Every experiment below was executed against the running Camunda 8 Run 8.8 engine (Zeebe, Operate, Tasklist,
Connectors) on this machine. The tables are generated from each lab's `evidence/run-log.json`, so the **outputs are the
actual process variables** read back from the engine after each instance finished, not expected values.

| | Experiments | Passed | Human tasks | Incidents caused and recovered | DMN sample evaluations | Unit tests |
|---|---|---|---|---|---|---|
| Lab 1 | 17 | 17 | 4 (1 completed in the Tasklist UI) | 2 | 7 | 4 / 4 |
| Lab 2 | 12 | 12 | 2 (1 completed in the Tasklist UI) | 2 | 7 | 9 / 9 |
| Lab 3 | 12 | 12 | 8 (2 completed in the Tasklist UI) | 1 | 9 | 13 / 13 |
| **Total** | **41** | **41** | **14** | **5** | **23** | **26 / 26** |

How to read the tables: *Actual end event* is where the instance finished. *Output* lists the key variables produced
on the way (validation result, connector response, DMN result, human decision, final status). For failure experiments
it starts with the incident Operate showed, which was then fixed and retried.

### Lab 1 – Loan approval

**What it does:**
1. A Java worker validates the application. Missing details are **requested from the applicant** and validated again; if the applicant never replies, the application is closed.
2. Not-eligible applicants and failed identity (KYC) checks go straight to one shared rejection track.
3. The REST Connector asks the **primary credit bureau** (CIBIL-like) for a score. If it is down or slow, the process **falls back to a backup bureau**. If both are down, a loan officer decides without a score.
4. The DMN `loan-decision` returns Approved, Rejected or Manual review.
5. Reviews send a reminder after 24 h and **escalate to a senior officer** after 48 h.
6. An approved applicant must **accept the offer** within 14 days before the loan is disbursed.

**What the experiments show:**
- Strong applicants are approved, accept the offer and get the loan disbursed.
- Weak ones are rejected by the matching rule, and ineligible or unverified applicants are rejected before any credit data is requested.
- When the primary bureau fails with HTTP 500 or a timeout, the **backup bureau's score** is used and the loan still goes through.
- A missing e-mail is asked for, received as a message and re-validated.
- Every timer fired in a real run (with test durations of a few seconds): application closed, review escalated, offer expired.
- The two worker failures appear as incidents in Operate and finish normally once the cause is fixed and the job retried.

![Lab 1 BPMN](lab1-loan-approval/evidence/screenshots/01-bpmn-design.png)

<!-- RESULTS:lab1:BEGIN -->
| ID | Experiment | Actual end event | Output (real process variables) | Status |
|---|---|---|---|---|
| L1-01 | Strong applicant: auto-approved, offer accepted, loan disbursed | LoanDisbursed | score 762 (band A) from **PRIMARY** bureau · DTI 0.1482 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-01-191544 at 6.5% · offer accepted · disbursed DSB-59B4867F | **PASS** |
| L1-02 | Low credit score is rejected by the DMN | LoanRejected | score 512 (band E) from **PRIMARY** bureau · DTI 0.2678 · DMN LD-1 → REJECTED · final **REJECTED** by AUTOMATED (DMN) | **PASS** |
| L1-03 | Borderline score: officer approves in Tasklist, offer accepted | LoanDisbursed | score 648 (band D) from **PRIMARY** bureau · DTI 0.2668 · DMN LD-6 → MANUAL_REVIEW · final **APPROVED** by LOAN OFFICER · offer OFF-LA-03-191544 at 11.9% · offer accepted · disbursed DSB-8D5112F8 | **PASS** |
| L1-04 | Loan above 100 000 goes to manual review, officer rejects | LoanRejected | score 731 (band B) from **PRIMARY** bureau · DTI 0.16 · DMN LD-4 → MANUAL_REVIEW · final **REJECTED** by LOAN OFFICER | **PASS** |
| L1-05 | Under-age applicant is not eligible: rejected without credit check | LoanRejected | rejected – Not eligible: age must be between 18 and 75 (was 16) | **PASS** |
| L1-06 | Primary bureau returns HTTP 500 -> score taken from the backup bureau -> approved | LoanDisbursed | primary bureau failed (HTTP 500: Internal scoring engine error) · score 741 (band B) from **BACKUP** bureau · DTI 0.1595 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-06-191544 at 7.9% · offer accepted · disbursed DSB-C02FD55B | **PASS** |
| L1-07 | Primary bureau times out (8 s > 5 s) -> backup bureau -> approved | LoanDisbursed | primary bureau failed (HTTP 408: The request timed out. Please try increasing the read and connection timeouts.) · score 726 (band B) from **BACKUP** bureau · DTI 0.1228 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-07-191544 at 7.9% · offer accepted · disbursed DSB-DF439B20 | **PASS** |
| L1-08 | Both credit bureaus down -> loan officer decides without a score -> approves | LoanDisbursed | primary bureau failed (HTTP 503: Primary credit bureau temporarily unavailable) · backup bureau failed too (HTTP 503: Backup credit bureau temporarily unavailable) → no score · DTI 0.1425 · final **APPROVED** by LOAN OFFICER · offer OFF-LA-08-191544 at 10.9% · offer accepted · disbursed DSB-75CE93F2 | **PASS** |
| L1-09 | Validation worker dependency down -> incident -> repaired -> retried -> disbursed | LoanDisbursed | incident `JOB_NO_RETRIES` on `Task_ValidateApplicant`: “Validation reference-data service unavailable (connection refused). Fix: restore the service (POST /admin/f…” → fixed, retried → score 705 (band B) from **PRIMARY** bureau · DTI 0.0997 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-09-191544 at 7.9% · offer accepted · disbursed DSB-D2CDC0F4 | **PASS** |
| L1-10 | Non-numeric income -> incident -> variable corrected in place -> retried -> disbursed | LoanDisbursed | incident `JOB_NO_RETRIES` on `Task_ValidateApplicant`: “Variable 'monthlyIncome' must be numeric but was the String "five thousand". Correct the variable in Operat…” → fixed, retried → score 744 (band B) from **PRIMARY** bureau · DTI 0.11 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-10-191544 at 7.9% · offer accepted · disbursed DSB-9040F878 | **PASS** |
| L1-11 | Missing e-mail -> applicant asked for details -> sends them -> re-validated -> disbursed | LoanDisbursed | score 762 (band A) from **PRIMARY** bureau · DTI 0.1482 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-11-191544 at 6.5% · offer accepted · disbursed DSB-872282C1 | **PASS** |
| L1-12 | Missing e-mail, applicant never replies -> correction window expires -> application closed | ApplicationClosed | incomplete: email is required → applicant did not reply | **PASS** |
| L1-13 | Identity cannot be verified (watch-list hit) -> rejected | LoanRejected | rejected – Identity could not be verified (document mismatch / watch-list hit) | **PASS** |
| L1-14 | Officer does not act -> reminder after 24 h -> escalated to a senior officer after 48 h -> approved | ReminderSent, LoanDisbursed | score 648 (band D) from **PRIMARY** bureau · DTI 0.2668 · DMN LD-6 → MANUAL_REVIEW · final **APPROVED** by SENIOR LOAN OFFICER (escalated) · offer OFF-LA-14-191544 at 11.9% · offer accepted · disbursed DSB-725D0867 | **PASS** |
| L1-15 | Approved, but the applicant never answers the offer -> offer expires | OfferExpired | score 762 (band A) from **PRIMARY** bureau · DTI 0.1482 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-15-191544 at 6.5% | **PASS** |
| L1-16 | Approved, but the applicant declines the offer | OfferDeclined | score 762 (band A) from **PRIMARY** bureau · DTI 0.1482 · DMN LD-5 → APPROVED · final **APPROVED** by AUTOMATED (DMN) · offer OFF-LA-16-191544 at 6.5% · offer **declined** | **PASS** |
| L1-17 | Debt-to-income above 50% is rejected by the DMN | LoanRejected | score 762 (band A) from **PRIMARY** bureau · DTI 0.559 · DMN LD-2 → REJECTED · final **REJECTED** by AUTOMATED (DMN) | **PASS** |

**17 / 17 passed.** Instance keys, every action taken and all variables: [`lab1-loan-approval/evidence/run-log.json`](lab1-loan-approval/evidence/run-log.json).
<!-- RESULTS:lab1:END -->

### Lab 2 – E-commerce order processing and fulfilment

**What it does:**
1. Java workers validate the order and **reserve** stock in the primary warehouse, or in the **secondary warehouse** if the primary is short; then they calculate the total.
2. The DMN `order-priority` sets the delivery priority and SLA.
3. Orders of 2 000 EUR or more, or 1 000 EUR or more from NEW customers, need a reviewer in Tasklist.
4. The REST Connector charges the **primary payment provider**. If it is down (HTTP 5xx / timeout), the **backup provider** is charged. If both are down, an incident is raised.
5. A declined card gives the customer a chance to **send another payment method** (message, at most 2 attempts, 30 min).
6. The shipment is booked with the primary carrier, or the **backup carrier** if its API is down.
7. Every cancellation releases the reserved stock (compensation).

**What the experiments show:**
- Normal orders are paid and shipped.
- A primary warehouse with no stock is covered by the secondary one; when both are short, the order is cancelled before payment.
- With the primary provider down, the card is charged by the backup provider and the order still ships.
- A declined card plus a new token from the customer gets paid on attempt 2. When the customer stays silent, the 8-second test timer releases the stock and cancels the order.
- High-value and NEW-customer orders wait for the reviewer.
- With both payment providers down, and with the inventory service down, an incident is raised; after the service is restored and the job retried, the order is fulfilled.

![Lab 2 BPMN](lab2-order-fulfilment/evidence/screenshots/01-bpmn-design.png)

<!-- RESULTS:lab2:BEGIN -->
| ID | Experiment | Actual end event | Output (real process variables) | Status |
|---|---|---|---|---|
| L2-01 | Stock available and payment approved | OrderFulfilled | customer REGULAR · stock AVAILABLE · total 215.76 EUR · DMN priority LOW (96 h) · payment **APPROVED** by primary provider · stock COMMITTED · shipment SHP-ORD-2001 | **PASS** |
| L2-02 | Both warehouses short – order cannot proceed to fulfilment | OutOfStock | customer REGULAR · stock INSUFFICIENT | **PASS** |
| L2-03 | Payment declined, customer does not send a new payment method in time | PaymentDeclined | customer REGULAR · stock AVAILABLE · total 553.79 EUR · DMN priority HIGH (24 h) · payment **DECLINED** (Insufficient funds) by primary provider · payment attempt 2 · stock RELEASED | **PASS** |
| L2-04 | High-value order routed to manual verification – reviewer approves in Tasklist | OrderFulfilled | customer VIP · stock AVAILABLE · total 2080.91 EUR · DMN priority CRITICAL (8 h) · reviewer APPROVED · payment **APPROVED** by primary provider · stock COMMITTED · shipment SHP-ORD-2004 | **PASS** |
| L2-05 | NEW customer exception order – reviewer rejects | CancelledByReviewer | customer NEW · stock AVAILABLE · total 1077.6 EUR · DMN priority NORMAL (48 h) · reviewer REJECTED · stock RELEASED | **PASS** |
| L2-06 | Invalid product ID – order rejected by validation | OrderRejected | validation failed: productId PRD-9999 does not exist in the catalogue | **PASS** |
| L2-07 | Both payment providers down → incident → backup provider restored → retry → fulfilled | OrderFulfilled | incident `JOB_NO_RETRIES` on `Task_ChargeBackup`: “HTTP 503: Backup payment provider temporarily unavailable” → fixed, retried → customer REGULAR · stock AVAILABLE · total 409.79 EUR · DMN priority HIGH (24 h) · payment **APPROVED** by **BACKUP** provider · stock COMMITTED · shipment SHP-ORD-2007 | **PASS** |
| L2-08 | Inventory service outage → incident → service restored → retry → fulfilled | OrderFulfilled | incident `JOB_NO_RETRIES` on `Task_CheckPrimaryWarehouse`: “Inventory service unavailable (HTTP 503 from inventory-api). Fix: restore the service (POST /admin/faults/i…” → fixed, retried → customer VIP · stock AVAILABLE · total 291.28 EUR · DMN priority HIGH (24 h) · payment **APPROVED** by primary provider · stock COMMITTED · shipment SHP-ORD-2008 | **PASS** |
| L2-09 | Primary payment provider down → backup provider charges → fulfilled | OrderFulfilled | customer REGULAR · stock AVAILABLE · total 538.8 EUR · DMN priority NORMAL (48 h) · payment **APPROVED** by **BACKUP** provider · stock COMMITTED · shipment SHP-ORD-2009 | **PASS** |
| L2-10 | Primary warehouse sold out → secondary warehouse reserves → fulfilled | OrderFulfilled | customer REGULAR · stock AVAILABLE · reserved in **secondary warehouse** WH-REGIONAL · total 658.8 EUR · DMN priority NORMAL (48 h) · payment **APPROVED** by primary provider · stock COMMITTED · shipment SHP-ORD-2010 | **PASS** |
| L2-11 | Card declined → customer sends a new payment method → paid → fulfilled | OrderFulfilled | customer REGULAR · stock AVAILABLE · total 409.79 EUR · DMN priority HIGH (24 h) · payment **APPROVED** by primary provider · payment attempt 2 · stock COMMITTED · shipment SHP-ORD-2011 | **PASS** |
| L2-12 | Primary carrier booking API down → backup carrier → fulfilled | OrderFulfilled | customer VIP · stock AVAILABLE · total 112.08 EUR · DMN priority CRITICAL (12 h) · payment **APPROVED** by primary provider · **backup carrier** · stock COMMITTED · shipment SHP-ORD-2012 | **PASS** |

**12 / 12 passed.** Instance keys, every action taken and all variables: [`lab2-order-fulfilment/evidence/run-log.json`](lab2-order-fulfilment/evidence/run-log.json).
<!-- RESULTS:lab2:END -->

### Lab 3 – Hospital appointment and patient triage

**What it does:**
1. A Java worker validates the registration.
2. The DMN `triage-category` classifies the request as Routine, Priority Review or Urgent Assessment (educational rules, not clinical guidance).
3. Urgent cases go to immediate clinical assessment in Tasklist; if nobody picks the case up, the **duty manager is alerted**. Priority cases go to staff review.
4. The REST Connector retrieves free slots at the main hospital, then at the **partner clinic** if the main hospital has none.
5. If the slot is taken by someone else while booking, the process **re-queries automatically** once. Scheduling staff step in only when both automatic options fail.
6. The patient must **confirm** the appointment. A reminder is sent, and an unconfirmed slot is **released** for other patients.

**What the experiments show:**
- Routine, priority and escalated requests end with a booked **and confirmed** appointment.
- Chest pain goes to immediate assessment and an emergency referral.
- Neurology, which only the partner clinic offers, is booked there.
- A slot conflict is recovered by the re-query.
- Dermatology, available nowhere, goes to staff, who waitlist the patient or assign a manual slot.
- An unconfirmed booking gets a reminder and is then released.
- An urgent case nobody picks up alerts the duty manager.
- A scheduling-API outage (HTTP 503) becomes an incident and recovers after the fix and retry.

![Lab 3 BPMN](lab3-hospital-triage/evidence/screenshots/01-bpmn-design.png)

<!-- RESULTS:lab3:BEGIN -->
| ID | Experiment | Actual end event | Output (real process variables) | Status |
|---|---|---|---|---|
| L3-01 | Routine request, main hospital slot booked, patient confirms | AppointmentConfirmed | DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · 5 slots found · booking **CONFIRMED** BK-5D9EED53 · 2026-11-03 09:00 - GENERAL_PRACTICE · patient CONFIRMED | **PASS** |
| L3-02 | Moderate pain → priority staff review (Tasklist UI) → booked → confirmed | AppointmentConfirmed | DMN **PRIORITY_REVIEW** (Moderate pain (5-7 out of 10), max wait 3 d) · staff review: BOOK_APPOINTMENT · 5 slots found · booking **CONFIRMED** BK-CE5DDFFE · 2026-10-30 14:00 - ORTHOPAEDICS · patient CONFIRMED | **PASS** |
| L3-03 | Chest pain → immediate clinical assessment (Tasklist UI) → ED referral | EmergencyReferral | DMN **URGENT_ASSESSMENT** (Chest pain reported, max wait 0 d) · clinical assessment: EMERGENCY_REFERRAL | **PASS** |
| L3-04 | No slot at main hospital or partner clinic (DERMATOLOGY) → staff waitlists | Waitlisted | DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · scheduling staff: WAITLIST · 0 slots found · booking **WAITLISTED** · at **partner clinic** | **PASS** |
| L3-05 | Incomplete registration returned to patient | RequestIncomplete | registration incomplete: patientReference is required; painLevel is required | **PASS** |
| L3-06 | Scheduling API down → incident → service restored → retried → booked → confirmed | AppointmentConfirmed | incident `JOB_NO_RETRIES` on `Task_RetrieveSlots`: “HTTP 503: Scheduling service temporarily unavailable” → fixed, retried → DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · 5 slots found · booking **CONFIRMED** BK-31E454FE · 2026-11-03 09:00 - RESPIRATORY · patient CONFIRMED | **PASS** |
| L3-07 | Priority review escalated to urgent → same-day slot → confirmed | AppointmentConfirmed | DMN: High fever in patient aged 65 or over → staff escalated to **URGENT_ASSESSMENT** (max wait 0 d) · staff review: ESCALATE_URGENT · clinical assessment: BOOK_URGENT_SLOT · 5 slots found · booking **CONFIRMED** BK-2354A854 · 2026-10-30 09:00 - GENERAL_PRACTICE · patient CONFIRMED | **PASS** |
| L3-08 | No slot anywhere → scheduling staff assigns a manual slot → confirmed | AppointmentConfirmed | DMN **PRIORITY_REVIEW** (Symptoms persisting for more than 14 days, max wait 5 d) · staff review: BOOK_APPOINTMENT · scheduling staff: MANUAL_SLOT · 0 slots found · booking **CONFIRMED_MANUAL** · 2026-11-05 15:00 - Dermatology extra clinic · at **partner clinic** · patient CONFIRMED | **PASS** |
| L3-09 | Main hospital has no NEUROLOGY slot → partner clinic slot booked → confirmed | AppointmentConfirmed | DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · 5 slots found · booking **CONFIRMED** BK-9F627217 · 2026-11-03 09:00 - NEUROLOGY · at **partner clinic** · patient CONFIRMED | **PASS** |
| L3-10 | Slots taken by other patients while booking → re-query → booked → confirmed | AppointmentConfirmed | DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · 5 slots found · booking **CONFIRMED** BK-0906014F · 2026-11-05 09:00 - CARDIOLOGY · after **re-query** (slot taken) · patient CONFIRMED | **PASS** |
| L3-11 | Patient never confirms → reminder timer → timeout timer → slot released | ReminderSent, SlotReleased | DMN **ROUTINE** (No urgency indicators present, max wait 14 d) · 5 slots found · booking **RELEASED** BK-1BD3D4CD · 2026-11-03 08:00 - ORTHOPAEDICS · patient NOT_CONFIRMED | **PASS** |
| L3-12 | Urgent assessment not picked up → duty manager escalated → then assessed → ED referral | DutyManagerAlerted, EmergencyReferral | DMN **URGENT_ASSESSMENT** (Breathing difficulty reported, max wait 0 d) · clinical assessment: EMERGENCY_REFERRAL | **PASS** |

**12 / 12 passed.** Instance keys, every action taken and all variables: [`lab3-hospital-triage/evidence/run-log.json`](lab3-hospital-triage/evidence/run-log.json).
<!-- RESULTS:lab3:END -->

### Other outputs

- **DMN sample evaluations:** every rule of every decision table was evaluated against the *deployed* decision:
  [Lab 1](lab1-loan-approval/docs/dmn-sample-evaluations.md) · [Lab 2](lab2-order-fulfilment/docs/dmn-sample-evaluations.md) ·
  [Lab 3](lab3-hospital-triage/docs/dmn-sample-evaluations.md). Each set includes a FIRST-hit precedence case.
- **Unit tests** (`mvn test`, all passing):

  | Test class | Tests | Checks |
  |---|---|---|
  | `ApplicantValidatorTest` (Lab 1) | 4 | complete + eligible application and ratio maths, incomplete (fixable), not eligible (final), malformed variable |
  | `OrderPricingTest` (Lab 2) | 3 | free shipping above 100 EUR, VIP 10% discount + same-day fee, small order pays shipping (incl. VAT) |
  | `OrderValidatorAndInventoryTest` (Lab 2) | 4 | valid order enriched, unknown product / bad quantity, reservation and release, secondary warehouse covers the primary |
  | `PaymentRetryPolicyTest` (Lab 2) | 2 | retry defaults added only when the order brings none; one more try with the default limit |
  | `RegistrationValidatorTest` (Lab 3) | 6 | required fields, ranges, formats, timer defaults / overrides, invalid duration |
  | `SearchWindowTest` (Lab 3) | 4 | appointment search window from max wait days and preferred date |
  | `SlotCalendarTest` (Lab 3) | 3 | neurology only at the partner clinic, no dermatology anywhere, booking and releasing a slot |

- **REST examples:** real requests and responses captured from the mock APIs are in each lab's `api-examples/` folder.
- **Screenshots:** each lab's `evidence/screenshots/` folder and the gallery at the end of each `REPORT.md`.

## Repository layout

```
├── pom.xml                         Maven parent (Java 21, Spring Boot 3.5.16, Camunda starter 8.8.32)
├── lab1-loan-approval/             ┐
├── lab2-order-fulfilment/          │  each lab:
├── lab3-hospital-triage/           ┘   src/main/resources/models/  BPMN, DMN, forms (deployed on start-up)
│                                       src/main/java/...           job workers, mock REST API, fault injection
│                                       src/test/java/...           unit tests
│                                       test-data/                  one JSON file per test scenario
│                                       api-examples/               real REST request/response samples
│                                       docs/                       DMN rules + real sample evaluations
│                                       evidence/                   run-log.json, test-results.md, screenshots/
│                                       REPORT.md                   implementation report
```

## Prerequisites

- Camunda 8 Run 8.8 (we used the copy bundled with Camunda Modeler 5.49: `c8run\start-c8run.bat`)
- JDK 21+ and Maven 3.9+

## Quick start

```bash
# 1. Camunda 8 Run must be running (Operate: http://localhost:8080/operate)
# 2. Build and unit-test all labs
mvn test
# 3. Start one lab app (deploys its BPMN/DMN/forms and starts workers + mock API)
mvn -pl lab1-loan-approval spring-boot:run
# 4. In another terminal: start an instance with the variables of a test scenario
curl -X POST http://localhost:8080/v2/process-instances -H "Content-Type: application/json" \
  -d '{"processDefinitionId": "loan-approval", "variables": { ...the "variables" block of a test-data/*.json file... }}'
# 5. Follow it in Operate (http://localhost:8080/operate), complete human tasks in Tasklist (http://localhost:8080/tasklist)
```

Lab 2 uses port 8082 and Lab 3 uses port 8083; all three apps can run at the same time.

## Coverage of the common submission checklist

| Checklist item | Lab 1 | Lab 2 | Lab 3 |
|---|---|---|---|
| BPMN model + design screenshot | `models/loan-approval.bpmn`, `01-bpmn-design.png` | `models/order-fulfilment.bpmn`, `01-…` | `models/patient-appointment-triage.bpmn`, `01-…` |
| DMN model, rules, sample results | `loan-decision.dmn`, `docs/` | `order-priority.dmn`, `docs/` | `triage-category.dmn`, `docs/` |
| Worker source + run instructions | `src/main/java`, REPORT §8 | REPORT §8 | REPORT §9 |
| REST Connector config + API examples | REPORT §4, `api-examples/` | REPORT §5, `api-examples/` | REPORT §5–6, `api-examples/` |
| Process-variable dictionary | REPORT §5 | REPORT §6 | REPORT §7 |
| Tests: success, rejection, manual review, failure/recovery | REPORT §6 | REPORT §7 | REPORT §8 |
| Deployment + execution evidence | `03-…`, `04-…` screenshots, `run-log.json` | same | same |
| Tasklist evidence | `05-…`, `06-…` (completed in the UI) | same | same |
| Operate monitoring + incident resolution | `07-…`–`10-…` | same | same |
| Short report | `REPORT.md` | `REPORT.md` | `REPORT.md` |
