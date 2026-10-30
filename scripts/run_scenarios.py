#!/usr/bin/env python3
"""
Runs the test scenarios of one lab against the local Camunda 8 Run and records the real outcome.

Usage:  python scripts/run_scenarios.py lab1-loan-approval [--only L1-03] [--screenshots]

Each <lab>/test-data/*.json file is one scenario:
{
  "id": "L1-03",
  "title": "Borderline score -> manual review -> officer approves",
  "category": "manual-review",              # success | rejection | manual-review | failure-recovery | validation
  "processId": "loan-approval",
  "app": "http://localhost:8081",          # lab app (for /admin/faults)
  "expected": {"endEvent": "EndEvent_LoanApproved", "route": "human-readable expected route"},
  "variables": { ... start variables ... },
  "steps": [                                 # executed in order after the instance is started
    {"do": "fault", "name": "credit-bureau-down", "enabled": true, "before": true},   # before=true -> runs before start
    {"do": "completeTask", "elementId": "Task_ManualReview", "variables": {...}},
    {"do": "expectIncident", "elementId": "Task_ValidateApplicant"},
    {"do": "setVars", "variables": {"monthlyIncome": 5000}},
    {"do": "retryIncident"},
    {"do": "screenshot", "view": "operate-instance", "name": "07-operate-incident"}
  ]
}
Results are written to <lab>/evidence/run-log.json and <lab>/evidence/test-results.md.
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import c8  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def post(url):
    req = urllib.request.Request(url, data=b"", method="POST")
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read() or b"{}")


def wait_parked(key, timeout=90):
    """Wait until the instance is finished, waiting at a user task, or has an incident."""
    return c8.wait(key, timeout)


def wait_for(predicate, key, timeout=90, what="condition"):
    deadline = time.time() + timeout
    while time.time() < deadline:
        s = c8.summary(key)
        if predicate(s):
            time.sleep(1.5)
            return c8.summary(key)
        time.sleep(1.5)
    raise TimeoutError(f"timed out waiting for {what} on {key}")


def screenshot(lab, view, name, key):
    out = ROOT / lab / "evidence" / "screenshots" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["node", str(ROOT / "scripts/render/capture.mjs"), view, str(key), str(out)], check=True)
    return str(out.relative_to(ROOT / lab)).replace("\\", "/")


def run(lab, sc, take_screens):
    log = {"id": sc["id"], "title": sc["title"], "category": sc["category"], "expected": sc["expected"],
           "startedAt": datetime.now().isoformat(timespec="seconds"), "actions": [], "screenshots": []}
    app = sc.get("app")
    for st in sc.get("steps", []):
        if st.get("before") and st["do"] == "fault":
            post(f"{app}/admin/faults/{st['name']}?enabled={str(st['enabled']).lower()}")
            log["actions"].append(f"fault {st['name']} -> {st['enabled']} (before start)")

    key = c8.call("POST", "/process-instances", {"processDefinitionId": sc["processId"],
                                                  "variables": sc["variables"]})["processInstanceKey"]
    log["processInstanceKey"] = str(key)
    print(f"  {sc['id']}: started instance {key}")

    for st in sc.get("steps", []):
        if st.get("before"):
            continue
        do = st["do"]
        if do == "fault":
            post(f"{app}/admin/faults/{st['name']}?enabled={str(st['enabled']).lower()}")
            log["actions"].append(f"fault {st['name']} -> {st['enabled']}")
        elif do == "completeTask":
            s = wait_for(lambda s: st["elementId"] in s["openUserTasks"], key, what=st["elementId"])
            if take_screens and st.get("ui"):
                # genuine Tasklist interaction: assign, fill the form, complete (screenshots before/after)
                out = ROOT / lab / "evidence" / "screenshots" / f"{st['screenshot']}.png"
                out.parent.mkdir(parents=True, exist_ok=True)
                subprocess.run(["node", str(ROOT / "scripts/render/capture.mjs"), "tasklist-complete", str(key),
                                str(out), json.dumps(st["ui"])], check=True)
                log["screenshots"] += [f"evidence/screenshots/{st['screenshot']}.png",
                                       f"evidence/screenshots/{st['screenshot']}-completed.png"]
                log["actions"].append(f"completed user task {st['elementId']} in the Tasklist UI with {st['ui']}")
                wait_for(lambda s: st["elementId"] not in s["openUserTasks"], key, what="task completion")
            else:
                task = [t for t in c8.open_tasks(key=key) if t["elementId"] == st["elementId"]][0]
                c8.call("POST", f"/user-tasks/{task['userTaskKey']}/completion",
                        {"variables": st.get("variables", {}), "action": "complete"})
                log["actions"].append(f"completed user task {st['elementId']} via REST API with {st.get('variables', {})}")
        elif do == "expectIncident":
            s = wait_for(lambda s: any(i["element"] == st["elementId"] for i in s["incidents"]), key,
                         timeout=st.get("timeout", 120), what="incident")
            inc = [i for i in s["incidents"] if i["element"] == st["elementId"]][0]
            log["incident"] = inc
            log["actions"].append(f"incident raised at {inc['element']}: {inc['type']} - {inc['message']}")
        elif do == "setVars":
            c8.call("PUT", f"/element-instances/{key}/variables", {"variables": st["variables"], "local": False})
            log["actions"].append(f"corrected variables {st['variables']}")
        elif do == "retryIncident":
            for inc in c8.active_incidents(key=key):
                if inc.get("jobKey"):
                    c8.call("PATCH", f"/jobs/{inc['jobKey']}", {"changeset": {"retries": st.get('retries', 1)}})
                c8.call("POST", f"/incidents/{inc['incidentKey']}/resolution", {})
                log["actions"].append(f"retried job {inc.get('jobKey')} and resolved incident {inc['incidentKey']}")
            time.sleep(2)
        elif do == "wait":
            wait_parked(key, st.get("timeout", 90))
        elif do == "sleep":
            time.sleep(st["seconds"])
        elif do == "screenshot":
            if take_screens:
                if st.get("waitFor") == "parked":
                    wait_parked(key)
                log["screenshots"].append(screenshot(lab, st["view"], st["name"], key))
        else:
            raise ValueError(f"unknown step {do}")

    # Every scenario ends in a completed instance. Wait for that explicitly: right after a user task is
    # completed the search index can still list it as open for a moment, so "parked" is not enough here.
    try:
        final = wait_for(lambda s: s["state"] in ("COMPLETED", "TERMINATED") and s["endEvents"], key,
                         timeout=120, what="completion")
    except TimeoutError:
        final = c8.summary(key)
    vars_ = c8.variables(key)
    log["final"] = final
    log["keyVariables"] = {k: vars_.get(k) for k in sc.get("reportVariables", [])}
    actual_end = final["endEvents"]
    exp_end = sc["expected"]["endEvent"]
    log["actualEndEvents"] = actual_end
    log["status"] = "PASS" if exp_end in actual_end and final["state"] == "COMPLETED" else "FAIL"
    print(f"  {sc['id']}: {log['status']}  state={final['state']} ends={actual_end}")
    return log


def write_markdown(lab, logs):
    lines = ["| ID | Scenario | Category | Expected route | Actual outcome | Instance key | Status |",
             "|---|---|---|---|---|---|---|"]
    for l in logs:
        actual = ", ".join(l.get("actualEndEvents", [])) or l["final"]["state"]
        if l.get("incident"):
            actual += f" (after incident: {l['incident']['type']})"
        lines.append(f"| {l['id']} | {l['title']} | {l['category']} | {l['expected']['route']} | {actual} "
                     f"| `{l['processInstanceKey']}` | **{l['status']}** |")
    (ROOT / lab / "evidence" / "test-results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    sys.stdout.reconfigure(encoding="utf-8")  # scenario titles may contain arrows; Windows consoles default to cp1252
    p = argparse.ArgumentParser()
    p.add_argument("lab")
    p.add_argument("--only", nargs="*")
    p.add_argument("--screenshots", action="store_true")
    a = p.parse_args()
    files = sorted((ROOT / a.lab / "test-data").glob("*.json"))
    scenarios = [json.loads(f.read_text(encoding="utf-8")) for f in files]
    if a.only:
        scenarios = [s for s in scenarios if s["id"] in a.only]
    (ROOT / a.lab / "evidence").mkdir(parents=True, exist_ok=True)
    log_file = ROOT / a.lab / "evidence" / "run-log.json"
    previous = {l["id"]: l for l in json.loads(log_file.read_text())} if log_file.exists() else {}
    for sc in scenarios:
        print(f"[{sc['id']}] {sc['title']}")
        try:
            previous[sc["id"]] = run(a.lab, sc, a.screenshots)
        except Exception as e:  # keep going; failure is recorded
            print(f"  {sc['id']}: ERROR {e}")
            previous[sc["id"]] = {"id": sc["id"], "title": sc["title"], "category": sc["category"],
                                  "expected": sc["expected"], "status": "ERROR", "error": str(e),
                                  "processInstanceKey": "-", "final": {"state": "ERROR"}}
    logs = [previous[k] for k in sorted(previous)]
    log_file.write_text(json.dumps(logs, indent=2), encoding="utf-8")
    write_markdown(a.lab, logs)
    print("results:", log_file)


if __name__ == "__main__":
    main()
