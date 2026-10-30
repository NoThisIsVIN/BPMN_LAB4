# DMN `triage-category` – sample evaluations

Real results from the deployed decision, obtained with `python scripts/c8.py evaluate triage-category '<input JSON>'` (Camunda REST API `POST /v2/decision-definitions/evaluation`) against the local Camunda 8 Run 8.8.

_Educational rules for a synthetic scenario – not clinical guidance._

| # | Case | Input | Matched rule | Output |
|---|---|---|---|---|
| 1 | Chest pain (red flag) | `{"chestPain": true, "breathingDifficulty": false, "painLevel": 3, "highFever": false, "patientAge": 58, "symptomDurationDays": 0}` | [1] | `{"urgencyCategory": "URGENT_ASSESSMENT", "triageReason": "Chest pain reported", "maxWaitDays": 0, "ruleId": "TR-1"}` |
| 2 | Breathing difficulty | `{"chestPain": false, "breathingDifficulty": true, "painLevel": 2, "highFever": false, "patientAge": 30, "symptomDurationDays": 1}` | [2] | `{"urgencyCategory": "URGENT_ASSESSMENT", "triageReason": "Breathing difficulty reported", "maxWaitDays": 0, "ruleId": "TR-2"}` |
| 3 | Severe pain 9/10 | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 9, "highFever": false, "patientAge": 40, "symptomDurationDays": 1}` | [3] | `{"urgencyCategory": "URGENT_ASSESSMENT", "triageReason": "Severe pain (8 or more out of 10)", "maxWaitDays": 0, "ruleId": "TR-3"}` |
| 4 | High fever, age 72 | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 4, "highFever": true, "patientAge": 72, "symptomDurationDays": 1}` | [4] | `{"urgencyCategory": "PRIORITY_REVIEW", "triageReason": "High fever in patient aged 65 or over", "maxWaitDays": 2, "ruleId": "TR-4"}` |
| 5 | Moderate pain 6/10 | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 6, "highFever": false, "patientAge": 52, "symptomDurationDays": 10}` | [5] | `{"urgencyCategory": "PRIORITY_REVIEW", "triageReason": "Moderate pain (5-7 out of 10)", "maxWaitDays": 3, "ruleId": "TR-5"}` |
| 6 | Symptoms for 20 days | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 1, "highFever": false, "patientAge": 38, "symptomDurationDays": 20}` | [6] | `{"urgencyCategory": "PRIORITY_REVIEW", "triageReason": "Symptoms persisting for more than 14 days", "maxWaitDays": 5, "ruleId": "TR-6"}` |
| 7 | Mild symptoms (routine) | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 2, "highFever": false, "patientAge": 34, "symptomDurationDays": 3}` | [7] | `{"urgencyCategory": "ROUTINE", "triageReason": "No urgency indicators present", "maxWaitDays": 14, "ruleId": "TR-7"}` |
| 8 | High fever, age 30 (fever alone is not priority) | `{"chestPain": false, "breathingDifficulty": false, "painLevel": 2, "highFever": true, "patientAge": 30, "symptomDurationDays": 2}` | [7] | `{"urgencyCategory": "ROUTINE", "triageReason": "No urgency indicators present", "maxWaitDays": 14, "ruleId": "TR-7"}` |
| 9 | Chest pain AND pain 6 (FIRST hit: rule 1 wins) | `{"chestPain": true, "breathingDifficulty": false, "painLevel": 6, "highFever": false, "patientAge": 45, "symptomDurationDays": 2}` | [1] | `{"urgencyCategory": "URGENT_ASSESSMENT", "triageReason": "Chest pain reported", "maxWaitDays": 0, "ruleId": "TR-1"}` |
