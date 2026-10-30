# DMN `loan-decision` – sample evaluations

Evaluated against the **deployed** decision on the local Camunda 8 Run with
the Orchestration Cluster API `POST /v2/decision-definitions/evaluation`.

| Case | Input | Output | Matched rule |
|---|---|---|---|
| LD-1 low score | `{"creditScore": 512, "debtToIncomeRatio": 0.3, "employmentStatus": "EMPLOYED", "loanAmount": 18000, "loanToIncomeRatio": 0.37}` | `{"outcome": "REJECTED", "reason": "Credit score below minimum of 550", "ruleId": "LD-1"}` | [1] |
| LD-2 DTI above 50% | `{"creditScore": 762, "debtToIncomeRatio": 0.61, "employmentStatus": "EMPLOYED", "loanAmount": 15000, "loanToIncomeRatio": 0.42}` | `{"outcome": "REJECTED", "reason": "Debt-to-income ratio above 50%", "ruleId": "LD-2"}` | [2] |
| LD-3 unemployed | `{"creditScore": 720, "debtToIncomeRatio": 0.2, "employmentStatus": "UNEMPLOYED", "loanAmount": 5000, "loanToIncomeRatio": 0.2}` | `{"outcome": "REJECTED", "reason": "No verifiable employment income", "ruleId": "LD-3"}` | [3] |
| LD-4 amount above 100 000 | `{"creditScore": 731, "debtToIncomeRatio": 0.16, "employmentStatus": "EMPLOYED", "loanAmount": 150000, "loanToIncomeRatio": 0.83}` | `{"outcome": "MANUAL_REVIEW", "reason": "Amount above automatic approval limit of 100 000", "ruleId": "LD-4"}` | [4] |
| LD-5 auto-approve | `{"creditScore": 762, "debtToIncomeRatio": 0.1482, "employmentStatus": "EMPLOYED", "loanAmount": 25000, "loanToIncomeRatio": 0.336}` | `{"outcome": "APPROVED", "reason": "Meets all automatic approval criteria", "ruleId": "LD-5"}` | [5] |
| LD-6 borderline score | `{"creditScore": 648, "debtToIncomeRatio": 0.2668, "employmentStatus": "EMPLOYED", "loanAmount": 30000, "loanToIncomeRatio": 0.463}` | `{"outcome": "MANUAL_REVIEW", "reason": "Borderline profile - loan officer judgement required", "ruleId": "LD-6"}` | [6] |
| FIRST-hit precedence: low score AND amount > 100 000 | `{"creditScore": 520, "debtToIncomeRatio": 0.2, "employmentStatus": "EMPLOYED", "loanAmount": 120000, "loanToIncomeRatio": 0.9}` | `{"outcome": "REJECTED", "reason": "Credit score below minimum of 550", "ruleId": "LD-1"}` | [1] |
