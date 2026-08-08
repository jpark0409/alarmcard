param([Int64]$RunId = 31240740764, [string]$Repo = "jpark0409/alarmcard")
$j = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId"
"status=$($j.status) conclusion=$($j.conclusion) updated=$($j.updated_at)"
