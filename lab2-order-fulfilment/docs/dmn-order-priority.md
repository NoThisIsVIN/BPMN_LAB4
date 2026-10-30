| # | Delivery preference (`deliveryPreference`) | Customer category (`customerCategory`) | Order total (EUR) (`orderTotal`) | → Delivery priority (`deliveryPriority`) | → SLA (hours) (`slaHours`) | → Rule ID (`ruleId`) | Rule description |
|---|---|---|---|---|---|---|---|
| 1 | `"SAME_DAY"` | – | – | `"CRITICAL"` | `8` | `"OP-1"` | Same-day delivery is always the most urgent |
| 2 | `"EXPRESS"` | `"VIP"` | – | `"CRITICAL"` | `12` | `"OP-2"` | Express delivery for VIP customers |
| 3 | `"EXPRESS"` | – | – | `"HIGH"` | `24` | `"OP-3"` | Express delivery for everyone else |
| 4 | – | `"VIP"` | – | `"HIGH"` | `24` | `"OP-4"` | VIP customers get priority even on standard delivery |
| 5 | `"STANDARD"` | – | `>= 500` | `"NORMAL"` | `48` | `"OP-5"` | Standard delivery, high basket value |
| 6 | `"STANDARD"` | – | `< 500` | `"LOW"` | `96` | `"OP-6"` | Standard delivery, low basket value |
