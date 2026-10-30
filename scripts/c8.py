#!/usr/bin/env python3
"""
Tiny command-line client for the Camunda 8 Orchestration Cluster REST API (v2) of a local
Camunda 8 Run (http://localhost:8080, API unprotected). Used to run the lab test scenarios
and to collect the actual outcome of every instance.

Commands
  start <processId> <vars.json> [--tag NAME]   start an instance, print its key
  wait <processInstanceKey> [--timeout 60]      wait until COMPLETED/TERMINATED or incident/user task
  state <processInstanceKey>                    instance state + visited elements + variables
  tasks [--process <processId>]                 open user tasks
  complete-task <userTaskKey> <vars.json|JSON>  complete a user task with variables
  incidents [--process <processId>]             active incidents
  set-vars <elementInstanceKey> <JSON>          update variables (e.g. fix a malformed one)
  retry <incidentKey> [--retries 1]             give the failed job retries again and resolve the incident
  evaluate <decisionId> <JSON>                  evaluate a deployed DMN decision
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/v2"


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> HTTP {e.code}: {e.read().decode()}")


def load_json(arg):
    if arg.strip().startswith("{"):
        return json.loads(arg)
    with open(arg, encoding="utf-8") as f:
        return json.load(f)


def search(path, flt, size=200):
    return call("POST", path, {"filter": flt, "page": {"limit": size}}).get("items", [])


def instance(key):
    items = search("/process-instances/search", {"processInstanceKey": str(key)})
    return items[0] if items else None


def elements(key):
    items = search("/element-instances/search", {"processInstanceKey": str(key)})
    return sorted(items, key=lambda e: e.get("startDate") or "")


def variables(key):
    """Root-scope (process-level) variables only; task-local connector inputs are left out."""
    items = search("/variables/search", {"processInstanceKey": str(key), "scopeKey": str(key)}, 500)
    out = {}
    for v in items:
        val = v.get("value")
        try:
            val = json.loads(val)
        except (TypeError, ValueError):
            pass
        out[v["name"]] = val
    return out


def open_tasks(process_id=None, key=None):
    flt = {"state": "CREATED"}
    if process_id:
        flt["processDefinitionId"] = process_id
    if key:
        flt["processInstanceKey"] = str(key)
    return search("/user-tasks/search", flt)


def active_incidents(process_id=None, key=None):
    """Unresolved incidents. In 8.8 a new incident can stay PENDING for a while before it becomes ACTIVE."""
    found = []
    for state in ("ACTIVE", "PENDING"):
        flt = {"state": state}
        if process_id:
            flt["processDefinitionId"] = process_id
        if key:
            flt["processInstanceKey"] = str(key)
        found += search("/incidents/search", flt)
    return found


def summary(key):
    pi = instance(key)
    els = elements(key)
    return {
        "processInstanceKey": str(key),
        "processDefinitionId": pi and pi.get("processDefinitionId"),
        "state": pi and pi.get("state"),
        "hasIncident": pi and pi.get("hasIncident"),
        "path": [f"{e['elementId']}({e['state']})" for e in els],
        "endEvents": [e["elementId"] for e in els if e.get("type") == "END_EVENT" and e["state"] == "COMPLETED"],
        "openUserTasks": [t["elementId"] for t in open_tasks(key=key)],
        "incidents": [{"key": i["incidentKey"], "element": i.get("elementId"), "type": i.get("errorType"),
                       "message": i.get("errorMessage")} for i in active_incidents(key=key)],
    }


def wait(key, timeout=60):
    """Wait until the instance finished, or is parked on a user task / incident (exported to secondary storage)."""
    deadline = time.time() + timeout
    s = None
    while time.time() < deadline:
        s = summary(key)
        if s["state"] in ("COMPLETED", "TERMINATED") or s["openUserTasks"] or s["incidents"]:
            # give the exporter a moment so variables/elements are complete
            time.sleep(1.5)
            return summary(key)
        time.sleep(1.5)
    return s


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("start"); s.add_argument("process"); s.add_argument("vars")
    s = sub.add_parser("wait"); s.add_argument("key"); s.add_argument("--timeout", type=int, default=60)
    s = sub.add_parser("state"); s.add_argument("key"); s.add_argument("--vars", action="store_true")
    s = sub.add_parser("tasks"); s.add_argument("--process")
    s = sub.add_parser("complete-task"); s.add_argument("key"); s.add_argument("vars")
    s = sub.add_parser("incidents"); s.add_argument("--process")
    s = sub.add_parser("set-vars"); s.add_argument("key"); s.add_argument("vars")
    s = sub.add_parser("retry"); s.add_argument("key"); s.add_argument("--retries", type=int, default=1)
    s = sub.add_parser("evaluate"); s.add_argument("decision"); s.add_argument("vars")
    a = p.parse_args()

    if a.cmd == "start":
        r = call("POST", "/process-instances", {"processDefinitionId": a.process, "variables": load_json(a.vars)})
        print(r["processInstanceKey"])
    elif a.cmd == "wait":
        print(json.dumps(wait(a.key, a.timeout), indent=2))
    elif a.cmd == "state":
        s = summary(a.key)
        if a.vars:
            s["variables"] = variables(a.key)
        print(json.dumps(s, indent=2))
    elif a.cmd == "tasks":
        for t in open_tasks(a.process):
            print(t["userTaskKey"], t["processInstanceKey"], t["elementId"], t.get("candidateGroups"))
    elif a.cmd == "complete-task":
        call("POST", f"/user-tasks/{a.key}/completion", {"variables": load_json(a.vars), "action": "complete"})
        print("completed", a.key)
    elif a.cmd == "incidents":
        for i in active_incidents(a.process):
            print(json.dumps({k: i.get(k) for k in ("incidentKey", "processInstanceKey", "elementId", "errorType",
                                                    "errorMessage", "jobKey", "elementInstanceKey")}))
    elif a.cmd == "set-vars":
        call("PUT", f"/element-instances/{a.key}/variables", {"variables": load_json(a.vars), "local": False})
        print("variables updated on", a.key)
    elif a.cmd == "retry":
        inc = search("/incidents/search", {"incidentKey": str(a.key)})
        if not inc:
            sys.exit("incident not found")
        job_key = inc[0].get("jobKey")
        if job_key:
            call("PATCH", f"/jobs/{job_key}", {"changeset": {"retries": a.retries}})
        call("POST", f"/incidents/{a.key}/resolution", {})
        print("retried job", job_key, "and resolved incident", a.key)
    elif a.cmd == "evaluate":
        r = call("POST", "/decision-definitions/evaluation", {"decisionDefinitionId": a.decision,
                                                               "variables": load_json(a.vars)})
        print(json.dumps({"output": json.loads(r.get("output", "null")),
                          "matchedRules": [m.get("ruleIndex") for m in r.get("evaluatedDecisions", [{}])[0].get("matchedRules", [])]},
                         indent=2))


if __name__ == "__main__":
    main()
