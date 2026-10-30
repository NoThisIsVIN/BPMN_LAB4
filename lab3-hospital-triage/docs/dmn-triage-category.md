| # | Chest pain (`chestPain`) | Breathing difficulty (`breathingDifficulty`) | Pain level (0-10) (`painLevel`) | High fever (`highFever`) | Patient age (`patientAge`) | Symptom duration (days) (`symptomDurationDays`) | → Urgency category (`urgencyCategory`) | → Triage reason (`triageReason`) | → Max wait (days) (`maxWaitDays`) | → Rule ID (`ruleId`) | Rule description |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `true` | – | – | – | – | – | `"URGENT_ASSESSMENT"` | `"Chest pain reported"` | `0` | `"TR-1"` | Red flag: chest pain always needs immediate human assessment |
| 2 | – | `true` | – | – | – | – | `"URGENT_ASSESSMENT"` | `"Breathing difficulty reported"` | `0` | `"TR-2"` | Red flag: breathing difficulty |
| 3 | – | – | `>= 8` | – | – | – | `"URGENT_ASSESSMENT"` | `"Severe pain (8 or more out of 10)"` | `0` | `"TR-3"` | Severe pain (8-10) |
| 4 | – | – | – | `true` | `>= 65` | – | `"PRIORITY_REVIEW"` | `"High fever in patient aged 65 or over"` | `2` | `"TR-4"` | High fever in an older patient (65+) is reviewed by staff within 2 days |
| 5 | – | – | `[5..7]` | – | – | – | `"PRIORITY_REVIEW"` | `"Moderate pain (5-7 out of 10)"` | `3` | `"TR-5"` | Moderate pain (5-7) is reviewed by staff within 3 days |
| 6 | – | – | – | – | – | `> 14` | `"PRIORITY_REVIEW"` | `"Symptoms persisting for more than 14 days"` | `5` | `"TR-6"` | Symptoms lasting more than two weeks are reviewed within 5 days |
| 7 | – | – | – | – | – | – | `"ROUTINE"` | `"No urgency indicators present"` | `14` | `"TR-7"` | No red flags or priority indicators: routine appointment within 14 days |
