# DMN `order-priority` – sample evaluations

Real results from the deployed decision, evaluated with `python scripts/c8.py evaluate order-priority '<json>'` (Camunda REST API `POST /v2/decision-definitions/evaluation`).

| Target rule | deliveryPreference | customerCategory | orderTotal | → deliveryPriority | slaHours | ruleId | matched rule # |
|---|---|---|---|---|---|---|---|
| OP-1 | SAME_DAY | REGULAR | 120.0 | CRITICAL | 8 | OP-1 | [1] |
| OP-2 | EXPRESS | VIP | 300.0 | CRITICAL | 12 | OP-2 | [2] |
| OP-3 | EXPRESS | NEW | 300.0 | HIGH | 24 | OP-3 | [3] |
| OP-4 | STANDARD | VIP | 80.0 | HIGH | 24 | OP-4 | [4] |
| OP-5 | STANDARD | REGULAR | 750.0 | NORMAL | 48 | OP-5 | [5] |
| OP-6 | STANDARD | REGULAR | 215.76 | LOW | 96 | OP-6 | [6] |
| first-hit | SAME_DAY | VIP | 5000.0 | CRITICAL | 8 | OP-1 | [1] |

The `first-hit` row shows the FIRST hit policy: a VIP same-day order satisfies both rule 1 (SAME_DAY) and rule 4 (VIP); rules are checked top-down, so rule 1 (CRITICAL / 8 h) wins.
