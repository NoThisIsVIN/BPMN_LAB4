"""Generates lab2-order-fulfilment/src/main/resources/models/order-fulfilment.bpmn"""
from pathlib import Path

from bpmn_builder import Diagram, decision, job, rest_attrs, rest_connector, user_task

SYS, REV = "Lane_OrderSystem", "Lane_OrderReviewer"

d = Diagram(
    "order-fulfilment", "Order Fulfilment", "E-Commerce Order Processing & Fulfilment (educational scenario)",
    lanes=[(SYS, "Order system (automated)", 3), (REV, "Order reviewer", 2)],
    doc="Validates an order, checks and reserves stock, calculates the total, determines delivery priority "
        "(DMN), verifies high-value/exception orders in Tasklist, charges a mock payment gateway via the REST "
        "connector and hands paid orders to fulfilment. Synthetic data and mock payment tokens only.",
    version_tag="1.0.0",
)

# ---------------- main (happy) line: lane SYS, row 1 ----------------
d.start("StartEvent_OrderPlaced", "Order placed", SYS, 0, 1,
        doc="Instance variables: orderId, customerReference, productId, quantity, deliveryPreference "
            "(STANDARD | EXPRESS | SAME_DAY), paymentToken (mock token, e.g. tok_test_approve).")

d.task("Task_ValidateOrder", "Validate order", SYS, 1, 1,
       ext=job("order.validate"),
       doc="Java job worker. Checks mandatory fields, looks up the synthetic customer (-> customerCategory) "
           "and product (-> productName, unitPrice), quantity 1-100, delivery preference and token format.")
d.xor("Gateway_OrderValid", "Order valid?", SYS, 2, 1, label="top")

d.task("Task_CheckInventory", "Check inventory & reserve stock", SYS, 3, 1,
       ext=job("order.check-inventory"),
       doc="Java job worker against the simulated inventory service. Reserves the quantity when available "
           "(reservationId). Fault 'inventory-service-down' -> retries with 5 s back-off -> incident.")
d.xor("Gateway_StockAvailable", "Stock available?", SYS, 4, 1, label="bottom")

d.task("Task_CalculateTotal", "Calculate order total", SYS, 5, 1,
       ext=job("order.calculate-total"),
       doc="Java job worker. subtotal, discount (VIP 10%), shipping fee, VAT 20% -> orderTotal (EUR).")

d.task("Task_DeterminePriority", "Determine delivery priority", SYS, 6, 1, kind="rule",
       ext=decision("order-priority", "priorityDecision", outputs={
           "priorityDecision": "=priorityDecision",
           "deliveryPriority": "=priorityDecision.deliveryPriority",
           "slaHours": "=priorityDecision.slaHours"}),
       doc="Business rule task -> DMN 'order-priority' (hit policy FIRST) on orderTotal, customerCategory "
           "and deliveryPreference.")

d.xor("Gateway_VerificationRequired", "Manual verification required?", SYS, 7, 1, label="top", label_w=120)
d.xor("Gateway_JoinVerified", None, SYS, 9, 1)

d.task("Task_ProcessPayment", "Process payment", SYS, 10, 1, color="rest",
       attrs=rest_attrs(),
       ext=rest_connector(
           "POST", "http://localhost:8082/mock/payment-gateway/charges",
           body='={orderId: orderId, amount: orderTotal, currency: currency, paymentToken: paymentToken, '
                'customerReference: customerReference}',
           result_expression='={paymentStatus: response.body.status, transactionId: response.body.transactionId, '
                             'declineReason: response.body.declineReason}',
           retries=3, retry_backoff="PT5S", read_timeout=10, connect_timeout=5),
       doc="REST Outbound Connector -> POST /mock/payment-gateway/charges (mock gateway). Declines come back as "
           "HTTP 200 with status DECLINED. HTTP 5xx/timeouts are retried 3 times (5 s back-off), then an "
           "incident is raised for an operator.")
d.xor("Gateway_PaymentApproved", "Payment approved?", SYS, 11, 1, label="bottom")

d.task("Task_CreateShipment", "Create fulfilment request", SYS, 12, 1,
       ext=job("order.create-shipment"),
       doc="Java job worker. Commits the stock reservation, creates shipmentId/trackingNumber, "
           "estimatedDelivery = now + slaHours.")
d.task("Task_NotifyConfirmed", "Send order confirmation", SYS, 13, 1, kind="send",
       ext=job("order.notify-customer", headers={"template": "ORDER_CONFIRMED"}))
d.end("EndEvent_OrderFulfilled", "Order sent to fulfilment", SYS, 14, 1, color="success")

# ---------------- below the main line: invalid order ----------------
d.task("Task_NotifyInvalid", "Notify customer: order rejected", SYS, 3, 2, kind="send",
       ext=job("order.notify-customer", headers={"template": "ORDER_INVALID"}))
d.end("EndEvent_OrderRejected", "Order rejected – invalid order", SYS, 4, 2, color="failure", label_w=100)

# ---------------- above the main line: out of stock / payment declined ----------------
d.task("Task_NotifyOutOfStock", "Notify customer: out of stock", SYS, 5, 0, kind="send",
       ext=job("order.notify-customer", headers={"template": "OUT_OF_STOCK"}))
d.end("EndEvent_OutOfStock", "Order cancelled – insufficient stock", SYS, 6, 0, color="failure", label_w=110)

d.task("Task_ReleaseStockPayment", "Release reserved stock", SYS, 12, 0,
       ext=job("order.release-stock"),
       doc="Compensation: returns the reserved quantity to the simulated inventory.")
d.task("Task_NotifyPaymentDeclined", "Notify customer: payment declined", SYS, 13, 0, kind="send",
       ext=job("order.notify-customer", headers={"template": "PAYMENT_DECLINED"}))
d.end("EndEvent_PaymentDeclined", "Order cancelled – payment declined", SYS, 14, 0, color="failure", label_w=110)

# ---------------- order reviewer lane ----------------
d.task("Task_VerifyOrder", "Verify high-value order", REV, 8, 0, kind="user",
       ext=user_task("order-verification-form", "order-reviewers", priority=70, outputs={
           "verificationDecision": "=verificationDecision",
           "verificationComment": "=verificationComment"}),
       doc="Camunda user task (Tasklist), candidate group order-reviewers. Form: order-verification-form.")
d.xor("Gateway_OrderVerified", "Order verified?", REV, 9, 0, label="right", label_w=60)

d.task("Task_ReleaseStockReview", "Release reserved stock", REV, 10, 1,
       ext=job("order.release-stock"),
       doc="Compensation: returns the reserved quantity to the simulated inventory.")
d.task("Task_NotifyCancelledReview", "Notify customer: order cancelled", REV, 11, 1, kind="send",
       ext=job("order.notify-customer", headers={"template": "CANCELLED_BY_REVIEW"}))
d.end("EndEvent_CancelledByReviewer", "Order cancelled by reviewer", REV, 12, 1, color="failure")

# ---------------- sequence flows ----------------
d.flow("Flow_Start_Validate", "StartEvent_OrderPlaced", "Task_ValidateOrder")
d.flow("Flow_Validate_Gw", "Task_ValidateOrder", "Gateway_OrderValid")
d.flow("Flow_Valid_Yes", "Gateway_OrderValid", "Task_CheckInventory", "Yes", condition="=orderValid = true")
d.flow("Flow_Valid_No", "Gateway_OrderValid", "Task_NotifyInvalid", "No", default=True)
d.flow("Flow_NotifyInvalid_End", "Task_NotifyInvalid", "EndEvent_OrderRejected")
d.flow("Flow_Inventory_Gw", "Task_CheckInventory", "Gateway_StockAvailable")
d.flow("Flow_Stock_Yes", "Gateway_StockAvailable", "Task_CalculateTotal", "Yes",
       condition='=stockStatus = "AVAILABLE"')
d.flow("Flow_Stock_No", "Gateway_StockAvailable", "Task_NotifyOutOfStock", "No", default=True)
d.flow("Flow_OutOfStock_End", "Task_NotifyOutOfStock", "EndEvent_OutOfStock")
d.flow("Flow_Total_Priority", "Task_CalculateTotal", "Task_DeterminePriority")
d.flow("Flow_Priority_Gw", "Task_DeterminePriority", "Gateway_VerificationRequired")
d.flow("Flow_Verification_No", "Gateway_VerificationRequired", "Gateway_JoinVerified", "No", default=True)
d.flow("Flow_Verification_Yes", "Gateway_VerificationRequired", "Task_VerifyOrder", "Yes",
       condition='=orderTotal >= 2000 or (customerCategory = "NEW" and orderTotal >= 1000)')
d.flow("Flow_Verify_Gw", "Task_VerifyOrder", "Gateway_OrderVerified")
d.flow("Flow_Verified_Yes", "Gateway_OrderVerified", "Gateway_JoinVerified", "Yes",
       condition='=verificationDecision = "APPROVED"', exit="top", entry="bottom")
d.flow("Flow_Verified_No", "Gateway_OrderVerified", "Task_ReleaseStockReview", "No", default=True)
d.flow("Flow_ReleaseReview_Notify", "Task_ReleaseStockReview", "Task_NotifyCancelledReview")
d.flow("Flow_NotifyCancelled_End", "Task_NotifyCancelledReview", "EndEvent_CancelledByReviewer")
d.flow("Flow_Join_Payment", "Gateway_JoinVerified", "Task_ProcessPayment")
d.flow("Flow_Payment_Gw", "Task_ProcessPayment", "Gateway_PaymentApproved")
d.flow("Flow_Payment_Yes", "Gateway_PaymentApproved", "Task_CreateShipment", "Yes",
       condition='=paymentStatus = "APPROVED"')
d.flow("Flow_Payment_No", "Gateway_PaymentApproved", "Task_ReleaseStockPayment", "No", default=True)
d.flow("Flow_ReleasePayment_Notify", "Task_ReleaseStockPayment", "Task_NotifyPaymentDeclined")
d.flow("Flow_NotifyDeclined_End", "Task_NotifyPaymentDeclined", "EndEvent_PaymentDeclined")
d.flow("Flow_Shipment_Notify", "Task_CreateShipment", "Task_NotifyConfirmed")
d.flow("Flow_Confirmed_End", "Task_NotifyConfirmed", "EndEvent_OrderFulfilled")

# ---------------- annotations ----------------
d.annotate("Annotation_DmnRules",
           "DMN order-priority (FIRST hit):\n"
           "• SAME_DAY → CRITICAL (8 h)\n"
           "• EXPRESS + VIP → CRITICAL (12 h)\n"
           "• EXPRESS → HIGH (24 h)\n"
           "• VIP → HIGH (24 h)\n"
           "• STANDARD, total ≥ 500 → NORMAL (48 h)\n"
           "• STANDARD, total < 500 → LOW (96 h)",
           d.x(0) - 40, d.y(REV, 0) - 50, w=270)
d.annotate("Annotation_VerificationRule",
           "Manual verification when:\n"
           "• total ≥ 2 000 (high value), or\n"
           "• NEW customer and total ≥ 1 000",
           d.x(5) - 40, d.y(SYS, 2) - 30, w=230)
d.annotate("Annotation_Payment",
           "Payment gateway HTTP 5xx / timeout\n→ 3 attempts, 5 s back-off\n→ incident (fix, then retry in Operate)",
           d.x(11) - 40, d.y(REV, 0) - 32, w=250)
d.annotate("Annotation_Legend",
           "Legend\n"
           "green task = Java job worker\n"
           "blue task = REST connector\n"
           "purple task = DMN decision\n"
           "orange task = human task (Tasklist)\n"
           "grey task = notification",
           d.x(3) - 20, d.y(REV, 0) - 50, w=220)

out = Path(__file__).resolve().parents[2] / "lab2-order-fulfilment/src/main/resources/models/order-fulfilment.bpmn"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(d.to_xml(), encoding="utf-8")
print("wrote", out)
