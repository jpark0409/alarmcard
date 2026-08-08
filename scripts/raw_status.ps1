param([Int64]$RunId = 31241621187)
$j = Invoke-RestMethod "https://api.github.com/repos/jpark0409/alarmcard/actions/runs/$RunId"
"status=$($j.status) conclusion=$($j.conclusion) updated=$($j.updated_at)"
