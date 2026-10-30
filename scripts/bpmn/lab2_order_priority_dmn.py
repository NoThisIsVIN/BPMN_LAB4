"""Generates lab2-order-fulfilment/src/main/resources/models/order-priority.dmn (+ docs table)."""
from pathlib import Path

from dmn_builder import DecisionTable

t = DecisionTable("order-priority", "Order delivery priority", "FIRST",
                  doc="Educational fulfilment rules. FIRST hit policy: the most urgent delivery promise "
                      "is checked first; STANDARD orders are split by value.")
t.input("delivery", "Delivery preference", "deliveryPreference", "string")
t.input("category", "Customer category", "customerCategory", "string")
t.input("total", "Order total (EUR)", "orderTotal", "number")
t.output("priority", "Delivery priority", "deliveryPriority", "string", '"CRITICAL","HIGH","NORMAL","LOW"')
t.output("sla", "SLA (hours)", "slaHours", "number")
t.output("ruleId", "Rule ID", "ruleId", "string")

t.rule("Same-day delivery is always the most urgent",
       ['"SAME_DAY"', "-", "-"], ['"CRITICAL"', "8", '"OP-1"'])
t.rule("Express delivery for VIP customers",
       ['"EXPRESS"', '"VIP"', "-"], ['"CRITICAL"', "12", '"OP-2"'])
t.rule("Express delivery for everyone else",
       ['"EXPRESS"', "-", "-"], ['"HIGH"', "24", '"OP-3"'])
t.rule("VIP customers get priority even on standard delivery",
       ["-", '"VIP"', "-"], ['"HIGH"', "24", '"OP-4"'])
t.rule("Standard delivery, high basket value",
       ['"STANDARD"', "-", ">= 500"], ['"NORMAL"', "48", '"OP-5"'])
t.rule("Standard delivery, low basket value",
       ['"STANDARD"', "-", "< 500"], ['"LOW"', "96", '"OP-6"'])

root = Path(__file__).resolve().parents[2]
out = root / "lab2-order-fulfilment/src/main/resources/models/order-priority.dmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(t.to_xml(), encoding="utf-8")
(root / "lab2-order-fulfilment/docs").mkdir(parents=True, exist_ok=True)
(root / "lab2-order-fulfilment/docs/dmn-order-priority.md").write_text(t.to_markdown(), encoding="utf-8")
print("wrote", out)
