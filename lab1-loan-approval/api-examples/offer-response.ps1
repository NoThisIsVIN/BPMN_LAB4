# Sends the applicant's answer to a waiting loan offer (message "loan-offer-response").
# Run: right-click > Run with PowerShell, or:  powershell -ExecutionPolicy Bypass -File offer-response.ps1

$appId = Read-Host "applicationId variable from Operate (e.g. LA-DEMO-1, NOT the instance key)"
$choice = Read-Host "Accept or reject the offer? (a/r)"

if ($choice -match '^[aA]') { $accepted = $true } elseif ($choice -match '^[rR]') { $accepted = $false }
else { Write-Host "Type a or r." -ForegroundColor Red; Read-Host "Press Enter to close"; exit 1 }

$body = @{
    name           = "loan-offer-response"
    correlationKey = $appId
    variables      = @{ offerAccepted = $accepted }
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/v2/messages/correlation" `
        -ContentType "application/json" -Body $body | Out-Null
    $word = if ($accepted) { "ACCEPTED" } else { "REJECTED" }
    Write-Host "Offer $word for $appId - check Operate." -ForegroundColor Green
} catch {
    Write-Host "Failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Check the Application ID and that the instance is waiting at 'Wait for offer acceptance'."
}
Read-Host "Press Enter to close"
