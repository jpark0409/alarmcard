# GitHub 에 `alarmcard` 저장소 생성 및 푸시 방법

로컬 저장소는 이미 준비되어 있고 (`C:\Users\user\Desktop\alarmcard`), 원격 `origin` 도
`https://github.com/jpark0409/alarmcard.git` 로 설정되어 있습니다.

원격 저장소를 GitHub 에 실제로 만들려면 **jpark0409 계정 인증이 필요**하므로 아래 방법 중 하나를 선택해 주세요.

---

## ⭐ 방법 0. VS Code + "GitHub Pull Requests" 확장 (설치 필요 없음, 가장 쉬움)

이 확장이 이미 설치되어 있으니 다음 순서로 진행하세요.

1. **VS Code 에서 `C:\Users\user\Desktop\alarmcard` 폴더가 열려 있는지 확인** (자동으로 새 창이 열렸을 것)
2. 좌측 **Source Control** 아이콘(브랜치 모양) 클릭
   - 또는 상단 메뉴 `View → Source Control` (단축키 `Ctrl+Shift+G`)
3. **`Publish Branch`** 버튼 클릭
   - 대신 명령 팔레트(`Ctrl+Shift+P`) 에서 **`Git: Publish to GitHub`** 실행해도 됩니다.
4. GitHub 로그인 팝업이 나오면 **`Allow`** → 브라우저 인증 완료 (jpark0409 계정)
5. 상단 입력창에 저장소 이름을 확인/수정: **`jpark0409/alarmcard`**
6. **`Publish to GitHub public repository`** 또는 **private** 중 선택
7. 완료! 아래 URL 에서 확인:

   https://github.com/jpark0409/alarmcard

> 참고: 이미 `origin` 이 설정되어 있어 `Publish` 버튼이 안 보이면, 대신 Source Control 상단의 `...` 메뉴 → `Push` 를 사용하되, 먼저 GitHub 웹(방법 A) 에서 빈 저장소를 만들어야 합니다.

---

> ⚠️ 현재 이 PC 에는 `gh` (GitHub CLI) 가 **설치되어 있지 않습니다**.
> `gh` 는 다음 위치들에 없습니다:
> - `C:\Program Files\GitHub CLI\gh.exe`
> - `C:\Users\user\AppData\Local\Programs\GitHub CLI\gh.exe`
> - `C:\Users\user\AppData\Local\Microsoft\WinGet\Links\gh.exe`
>
> `winget install --id GitHub.cli` 은 소스 카탈로그 갱신 대기로 완료되지 않았습니다.
> 아래 **방법 B** 의 MSI 직접 설치를 권장합니다.

---

## 방법 A. 웹 브라우저에서 저장소 생성 (가장 쉬움, 추가 설치 불필요)

1. https://github.com/new 접속 (jpark0409 계정으로 로그인)
2. **Repository name**: `alarmcard`
3. Public / Private 선택
4. **README, .gitignore, license 는 추가하지 말 것** (로컬에 이미 있음)
5. **Create repository** 클릭
6. PowerShell 에서 아래 실행:

```powershell
cd C:\Users\user\Desktop\alarmcard
git push -u origin main
```

푸시 시 GitHub 사용자명과 **Personal Access Token(PAT)** 을 물어봅니다.
(비밀번호가 아닌 PAT: https://github.com/settings/tokens 에서 `repo` 권한으로 생성)

---

## 방법 B. GitHub CLI (`gh`) 설치 후 사용

### B-1. MSI 직접 설치 (권장)

1. 아래에서 최신 Windows MSI 다운로드:

   https://github.com/cli/cli/releases/latest

   파일 예: `gh_2.x.x_windows_amd64.msi`

2. 더블클릭하여 설치 (기본 경로: `C:\Program Files\GitHub CLI\`)
3. **PowerShell 을 새 창으로 열어야** `gh` 명령이 인식됩니다.

### B-2. (대안) winget 재시도

```powershell
winget source update
winget install --id GitHub.cli -e
```

카탈로그 갱신에 수 분이 걸릴 수 있습니다.

### B-3. 로그인 및 저장소 생성 + 푸시

```powershell
gh auth login
# GitHub.com → HTTPS → Login with a web browser → 표시된 코드를 브라우저에 입력

cd C:\Users\user\Desktop\alarmcard
gh repo create jpark0409/alarmcard --public --source=. --remote=origin --push
```

이미 `origin` 이 설정되어 있어 위 명령이 실패하면 다음과 같이 나눠 실행:

```powershell
gh repo create jpark0409/alarmcard --public
git push -u origin main
```

---

## 확인

성공하면 아래 URL 에서 확인 가능:

https://github.com/jpark0409/alarmcard
