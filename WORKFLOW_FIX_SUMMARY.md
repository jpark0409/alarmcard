# GitHub Actions 워크플로우 수정 - 릴리즈 APK 아티팩트 생성 문제 해결

## 문제 분석 (fd7722f 커밋)

### 기존 문제점
1. **release.yml**: APK 찾기 실패 시 디버깅 정보 부족
2. **android.yml**: APK 경로 검증 미흡 (실패해도 조용히 넘어감)
3. 두 워크플로우 모두 오류 처리 미비

---

## 🔧 해결 방안

### 1. release.yml 개선사항

#### 추가된 디버깅 단계
```yaml
- name: Locate APK
  id: apk
  run: |
    set -e
    echo "Searching for debug APK in app/build/outputs/apk/debug..."
    ls -la app/build/outputs/apk/debug/ || echo "Directory not found"
    
    APK=$(find app/build/outputs/apk/debug -type f -name "*.apk" 2>/dev/null | head -n1)
    if [ -z "$APK" ]; then
      echo "ERROR: APK not found in app/build/outputs/apk/debug!" >&2
      echo "Available APK files:" >&2
      find app/build/outputs/apk -type f -name "*.apk" 2>/dev/null || true
      exit 1
    fi
```

**개선점**:
- APK 빌드 후 직접 디렉토리 확인 (ls -la)
- 모든 APK 파일 검색해서 발견 여부 확인
- 파일 정보 출력 (파일 크기, 수정 시간)

#### 검증 단계 추가
```yaml
- name: Verify APK exists before release
  run: |
    if [ ! -f "alarmcard-debug.apk" ]; then
      echo "ERROR: alarmcard-debug.apk not found!" >&2
      ls -la *.apk 2>/dev/null || echo "No APK files in workspace root"
      exit 1
    fi
    ls -lh alarmcard-debug.apk
    file alarmcard-debug.apk
```

**효과**:
- 릴리즈 전 APK 존재 여부 확인
- 파일 타입 검증 (실제 APK인지 확인)

---

### 2. android.yml 개선사항

#### 개선된 APK 찾기 로직
```yaml
- name: Find Release APK
  id: find_apk
  run: |
    echo "Searching for release APK..."
    ls -la app/build/outputs/apk/release/ || echo "Release directory not found"
    
    APK_PATH=$(find app/build/outputs/apk/release -type f -name "*.apk" 2>/dev/null | head -1)
    if [ -z "$APK_PATH" ]; then
      echo "ERROR: Release APK not found!" >&2
      echo "Searching all APK files:" >&2
      find app/build/outputs/apk -type f -name "*.apk" 2>/dev/null || echo "No APK files found"
      exit 1
    fi
    
    echo "Found APK: $APK_PATH"
    ls -lh "$APK_PATH"
    file "$APK_PATH"
    echo "apk_path=$APK_PATH" >> $GITHUB_OUTPUT
```

**개선점**:
- 디렉토리 존재 여부 먼저 확인
- 모든 APK 파일을 광범위하게 검색
- 파일 정보 및 타입 검증

#### 무결성 검증
```yaml
- name: Verify APK integrity
  run: |
    if [ ! -f "${{ steps.find_apk.outputs.apk_path }}" ]; then
      echo "ERROR: APK file does not exist: ${{ steps.find_apk.outputs.apk_path }}" >&2
      exit 1
    fi
    echo "APK verified successfully"
```

#### 아티팩트 업로드 오류 처리 강화
```yaml
- name: Upload Release APK
  uses: actions/upload-artifact@v4
  with:
    name: release-apk
    path: ${{ steps.find_apk.outputs.apk_path }}
    retention-days: 30
    if-no-files-found: error  # ← 파일 없으면 실패로 처리
```

---

## 📋 변경 요약

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| **APK 찾기 실패 시** | 조용히 넘어감 | 명확한 에러 메시지 + 디버깅 정보 |
| **디렉토리 존재 확인** | 없음 | ls -la로 확인 |
| **모든 APK 파일 검색** | 없음 | find 명령으로 전체 검색 |
| **파일 무결성 검사** | 없음 | file 명령으로 타입 검증 |
| **아티팩트 업로드 오류** | 무시됨 | if-no-files-found: error 추가 |
| **출력 명확성** | 낮음 | 높음 (각 단계별 로깅) |

---

## 🚀 예상 효과

### 로그 분석 개선
1. **APK 빌드 실패 감지**: 빌드 로그에 APK가 생성되지 않은 원인 파악 가능
2. **경로 오류 식별**: 실제 APK가 생성되는 경로를 로그로 확인
3. **파일 무결성 확인**: APK가 실제 유효한 안드로이드 패키지인지 검증

### 워크플로우 안정성
- **조기 실패**: APK 없으면 즉시 워크플로우 중단 (낭비되는 리소스 방지)
- **명확한 에러**: GitHub Actions 로그에서 문제 원인 바로 파악
- **모니터링 용이**: 각 단계별 상세 로그로 CI/CD 파이프라인 관찰 가능

---

## 🔍 문제 해결 방법

### 만약 여전히 APK가 없다면:

1. **로그 확인**:
   ```
   Searching for release APK...
   ls -la app/build/outputs/apk/release/ || echo "Release directory not found"
   ```
   → 이 부분에서 디렉토리 존재 여부 확인

2. **빌드 오류 확인**:
   ```
   Searching all APK files:
   find app/build/outputs/apk -type f -name "*.apk"
   ```
   → 다른 경로에 APK가 있는지 확인

3. **gradle 설정 검토**:
   - `app/build.gradle.kts`에서 buildTypes 설정 확인
   - minSdk, targetSdk 호환성 확인
   - 빌드 에러 로그 분석

---

## 📝 테스트 방법

### release.yml 수동 실행
```bash
# GitHub 웹 UI에서:
# Actions → Release APK → Run workflow → Branch: main
```

### android.yml 검증
```bash
# PR을 main 또는 develop에 생성하면 자동 실행
# 또는 GitHub UI에서 직접 실행
```

---

## ✅ 검증 체크리스트

- [x] release.yml 들여쓰기 수정 (YAML 문법)
- [x] android.yml 들여쓰기 수정 (YAML 문법)
- [x] APK 찾기 로직 강화
- [x] 디버깅 정보 추가
- [x] 오류 처리 개선
- [x] 무결성 검증 추가