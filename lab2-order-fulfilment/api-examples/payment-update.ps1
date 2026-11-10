# Sends the customer's new payment method to an order waiting at "Wait for new payment method"
# (message "payment-method-updated", correlated by orderId).
# Run:  .\payment-update.ps1   or   powershell -ExecutionPolicy Bypass -File .\payment-update.ps1

$orderId = Read-Host "orderId variable from Operate (e.g. ORD-HTTP-1, NOT the instance key)"
$choice = Read-Host "New card works or gets declined again? (a = approve / d = decline)"

if ($choice -match '^[aA]') { $token = "tok_test_approve" } elseif ($choice -match '^[dD]') { $token = "tok_test_decline_funds" }
else { Write-Host "Type a or d." -ForegroundColor Red; Read-Host "Press Enter to close"; exit 1 }

$body = @{
    name           = "payment-method-updated"
    correlationKey = $orderId
    variables      = @{ paymentToken = $token }
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/v2/messages/correlation" `
        -ContentType "application/json" -Body $body | Out-Null
    Write-Host "New payment method ($token) sent for $orderId - check Operate." -ForegroundColor Green
} catch {
    Write-Host "Failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Check the orderId and that the order is waiting at 'Wait for new payment method'."
}
Read-Host "Press Enter to close"
