| # | Credit score (`creditScore`) | Debt-to-income ratio (`debtToIncomeRatio`) | Employment status (`employmentStatus`) | Loan amount (`loanAmount`) | Loan-to-annual-income ratio (`loanToIncomeRatio`) | → Outcome (`outcome`) | → Reason (`reason`) | → Rule ID (`ruleId`) | Rule description |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `< 550` | – | – | – | – | `"REJECTED"` | `"Credit score below minimum of 550"` | `"LD-1"` | Hard reject: credit score below the minimum of 550 |
| 2 | – | `> 0.5` | – | – | – | `"REJECTED"` | `"Debt-to-income ratio above 50%"` | `"LD-2"` | Hard reject: total monthly debt (incl. new loan) above 50% of income |
| 3 | – | – | `"UNEMPLOYED"` | – | – | `"REJECTED"` | `"No verifiable employment income"` | `"LD-3"` | Hard reject: no employment income to repay the loan |
| 4 | – | – | – | `> 100000` | – | `"MANUAL_REVIEW"` | `"Amount above automatic approval limit of 100 000"` | `"LD-4"` | Large loans are never auto-approved |
| 5 | `>= 700` | `<= 0.35` | – | – | `<= 0.5` | `"APPROVED"` | `"Meets all automatic approval criteria"` | `"LD-5"` | Auto-approve: strong score, low debt burden, loan at most half of annual income |
| 6 | – | – | – | – | – | `"MANUAL_REVIEW"` | `"Borderline profile - loan officer judgement required"` | `"LD-6"` | Everything else is borderline and needs an officer |
