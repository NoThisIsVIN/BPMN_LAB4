#!/usr/bin/env python3
"""
Fills the generated sections of <lab>/REPORT.md from the real evidence:
  <!-- TEST_RESULTS:BEGIN --> ... <!-- TEST_RESULTS:END -->   <- evidence/run-log.json
  <!-- SCREENSHOTS:BEGIN --> ... <!-- SCREENSHOTS:END -->     <- evidence/screenshots/*.png

and, with --readme, the experiment-results tables in the root README.md:
  <!-- RESULTS:labN:BEGIN --> ... <!-- RESULTS:labN:END -->    <- each lab's evidence/run-log.json

Usage: python scripts/report_assets.py lab1-loan-approval
       python scripts/report_assets.py --readme
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CAPTIONS = [
    (r"^01-bpmn-design", "BPMN process design (rendered from the deployed .bpmn with bpmn-js, the Modeler's renderer)"),
    (r"^02-dmn", "DMN decision table"),
    (r"^03-operate-deployed", "Deployment evidence – Operate: deployed process definition with instance counts per element"),
    (r"^03-operate-decision", "Operate – evaluated decision instances"),
    (r"^04-operate-instance", "Operate – executed instance (token path highlighted)"),
    (r"^05-tasklist", "Tasklist – open human tasks"),
    (r"^06-tasklist.*-completed", "Tasklist – task completed in the UI"),
    (r"^06-tasklist", "Tasklist – task form filled in by the reviewer (assigned to me)"),
    (r"^07-operate-incident", "Operate – incident raised after the failure (instance marked red)"),
    (r"^08-operate-incident-message", "Operate – full incident error message (diagnosis)"),
    (r"^08-operate-incident", "Operate – incidents view: type, failing element, error message"),
    (r"^09-operate-recovered", "Operate – same instance after the cause was fixed and the job retried: completed"),
    (r"^10-operate-variables", "Operate – process variables of a completed instance"),
]


def caption(name):
    for pattern, text in CAPTIONS:
        if re.search(pattern, name):
            scenario = re.search(r"(L\d-\d\d)", name)
            return f"{text}{f' – scenario {scenario.group(1)}' if scenario else ''}"
    return name


def test_table(lab):
    logs = json.loads((ROOT / lab / "evidence" / "run-log.json").read_text(encoding="utf-8"))
    lines = ["| ID | Scenario | Category | Expected route | Actual outcome (end event) | Process instance key | Status |",
             "|---|---|---|---|---|---|---|"]
    for l in logs:
        actual = ", ".join(l.get("actualEndEvents", [])) or l.get("final", {}).get("state", "")
        if l.get("incident"):
            actual += f" – after incident `{l['incident']['type']}` on `{l['incident']['element']}` was resolved"
        lines.append(f"| {l['id']} | {l['title']} | {l['category']} | {l['expected']['route']} | `{actual}` "
                     f"| `{l.get('processInstanceKey', '-')}` | **{l['status']}** |")
    passed = sum(1 for l in logs if l["status"] == "PASS")
    lines.append("")
    lines.append(f"**{passed} of {len(logs)} scenarios passed** on the last full run "
                 f"(`python scripts/run_scenarios.py {lab} --screenshots`). Per-scenario actions, incident messages "
                 f"and key variables: [`evidence/run-log.json`](evidence/run-log.json).")
    return "\n".join(lines)


def gallery(lab):
    shots = sorted((ROOT / lab / "evidence" / "screenshots").glob("*.png"))
    out = []
    for s in shots:
        out.append(f"**{s.stem}** – {caption(s.stem)}\n\n![{s.stem}](evidence/screenshots/{s.name})\n")
    return "\n".join(out)


def replace_block(text, tag, body):
    pattern = re.compile(rf"<!-- {tag}:BEGIN -->.*?<!-- {tag}:END -->", re.S)
    block = f"<!-- {tag}:BEGIN -->\n{body}\n<!-- {tag}:END -->"
    if not pattern.search(text):
        raise SystemExit(f"marker {tag} not found")
    return pattern.sub(lambda _: block, text)


def _v(kv, key):
    return kv.get(key)


def _lab1_output(kv):
    parts = []
    if kv.get("validationPassed") is False:
        return "validation failed: " + "; ".join(kv.get("validationErrors") or [])
    if kv.get("creditCheckStatus") == "UNAVAILABLE":
        parts.append(f"credit check UNAVAILABLE ({kv.get('creditCheckError')})")
    elif kv.get("creditScore") is not None:
        parts.append(f"score {kv['creditScore']} (band {kv.get('creditRiskBand')})")
    if kv.get("debtToIncomeRatio") is not None:
        parts.append(f"DTI {kv['debtToIncomeRatio']}")
    if kv.get("loanDecision"):
        d = kv["loanDecision"]
        parts.append(f"DMN {d.get('ruleId')} → {d.get('outcome')}")
    if kv.get("finalDecision"):
        parts.append(f"final **{kv['finalDecision']}** by {kv.get('decisionSource')}")
    if kv.get("offerId"):
        parts.append(f"offer {kv['offerId']} at {round(kv.get('interestRate', 0) * 100, 1)}%")
    return " · ".join(parts)


def _lab2_output(kv):
    if kv.get("orderValid") is False:
        return "validation failed: " + "; ".join(kv.get("validationErrors") or [])
    parts = [f"customer {kv.get('customerCategory')}", f"stock {kv.get('stockStatus')}"]
    if kv.get("orderTotal") is not None:
        parts.append(f"total {kv['orderTotal']} EUR")
    if kv.get("deliveryPriority"):
        parts.append(f"DMN priority {kv['deliveryPriority']} ({kv.get('slaHours')} h)")
    if kv.get("verificationDecision"):
        parts.append(f"reviewer {kv['verificationDecision']}")
    if kv.get("paymentStatus"):
        p = f"payment **{kv['paymentStatus']}**"
        if kv.get("declineReason"):
            p += f" ({kv['declineReason']})"
        parts.append(p)
    if kv.get("reservationStatus"):
        parts.append(f"stock {kv['reservationStatus']}")
    if kv.get("shipmentId"):
        parts.append(f"shipment {kv['shipmentId']}")
    return " · ".join(parts)


def _lab3_output(kv):
    if kv.get("registrationErrors") and not kv.get("urgencyCategory"):
        return "registration incomplete: " + "; ".join(kv["registrationErrors"])
    if kv.get("reviewOutcome") == "ESCALATE_URGENT":
        # the DMN said PRIORITY_REVIEW; staff escalation overwrote urgencyCategory / maxWaitDays
        parts = [f"DMN: {kv.get('triageReason')} → staff escalated to **{kv.get('urgencyCategory')}** "
                 f"(max wait {kv.get('maxWaitDays')} d)"]
    else:
        parts = [f"DMN **{kv.get('urgencyCategory')}** ({kv.get('triageReason')}, max wait {kv.get('maxWaitDays')} d)"]
    for key, label in (("reviewOutcome", "staff review"), ("assessmentOutcome", "clinical assessment"),
                       ("schedulingOutcome", "scheduling staff")):
        if kv.get(key):
            parts.append(f"{label}: {kv[key]}")
    if kv.get("slotCount") is not None:
        parts.append(f"{kv['slotCount']} slots found")
    if kv.get("bookingStatus"):
        b = f"booking **{kv['bookingStatus']}**"
        if kv.get("bookingReference"):
            b += f" {kv['bookingReference']}"
        parts.append(b)
    if kv.get("appointmentSlot"):
        parts.append(kv["appointmentSlot"].split(",")[0])
    return " · ".join(parts)


README_LABS = {
    "lab1": ("lab1-loan-approval", _lab1_output),
    "lab2": ("lab2-order-fulfilment", _lab2_output),
    "lab3": ("lab3-hospital-triage", _lab3_output),
}


def readme_results(lab, fmt):
    logs = json.loads((ROOT / lab / "evidence" / "run-log.json").read_text(encoding="utf-8"))
    lines = ["| ID | Experiment | Actual end event | Output (real process variables) | Status |",
             "|---|---|---|---|---|"]
    for l in logs:
        out = fmt(l.get("keyVariables") or {})
        if l.get("incident"):
            msg = l["incident"]["message"]
            connector = re.search(r"code=(\d+).*?body=\{error=([^,}]+)", msg)
            if connector:  # REST connector failure: "| Error variables: {error={code=503, ... body={error=...}"
                msg = f"HTTP {connector.group(1)}: {connector.group(2)}"
            else:  # job worker failure: first line of the worker's message
                msg = msg.replace("io.camunda.client.exception.JobError: ", "").split("\n")[0].strip(" |")
            if len(msg) > 110:
                msg = msg[:107] + "…"
            out = f"incident `{l['incident']['type']}` on `{l['incident']['element']}`: “{msg}” → fixed, retried → " + out
        end = ", ".join(e.replace("EndEvent_", "") for e in l.get("actualEndEvents", []))
        lines.append(f"| {l['id']} | {l['title']} | {end} | {out.replace('|', '/')} | **{l['status']}** |")
    passed = sum(1 for l in logs if l["status"] == "PASS")
    lines.append("")
    lines.append(f"**{passed} / {len(logs)} passed.** Instance keys, every action taken and all variables: "
                 f"[`{lab}/evidence/run-log.json`]({lab}/evidence/run-log.json).")
    return "\n".join(lines)


def update_readme():
    readme = ROOT / "README.md"
    text = readme.read_text(encoding="utf-8")
    for tag, (lab, fmt) in README_LABS.items():
        text = replace_block(text, f"RESULTS:{tag}", readme_results(lab, fmt))
    readme.write_text(text, encoding="utf-8")
    print("updated", readme)


def main():
    if sys.argv[1] == "--readme":
        update_readme()
        return
    lab = sys.argv[1]
    report = ROOT / lab / "REPORT.md"
    text = report.read_text(encoding="utf-8")
    text = replace_block(text, "TEST_RESULTS", test_table(lab))
    text = replace_block(text, "SCREENSHOTS", gallery(lab))
    report.write_text(text, encoding="utf-8")
    print("updated", report)


if __name__ == "__main__":
    main()
