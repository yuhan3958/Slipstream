# Slipstream

같은 로컬 네트워크에 있는 휴대폰과 PC 사이에서 파일을 전송하는 작은 HTTPS 파일 업로드 서버입니다. PC에서 서버를 실행한 뒤 표시되는 QR 코드를 휴대폰으로 스캔하면 업로드 페이지가 열리고, 전송된 파일은 `downloads/`에 저장됩니다.

## 기능

- HTTPS로 동작하는 로컬 파일 업로드 서버
- 접속 토큰이 포함된 QR 코드
- 여러 로컬 IPv4 주소를 고르는 드롭다운과 선택 즉시 갱신되는 QR 코드
- 다운로드 폴더 열기 및 서버 종료 버튼
- 파일명 정리, 임의 접두사 추가, 파일당 최대 2 GiB 제한

## 요구 사항

- JDK 21 이상 (가상 스레드를 사용합니다)
- 로컬 네트워크에서 사용할 PKCS#12 (`.p12`) 키스토어

## HTTPS 인증서 만들기

아래 예시는 현재 PC의 LAN IP가 `192.168.0.10`인 경우입니다. `ip:` 값은 실제로 QR 코드에서 사용할 모든 로컬 IP로 바꾸거나 추가하세요.

```powershell
keytool -genkeypair `
  -alias slipstream `
  -keyalg EC `
  -groupname secp256r1 `
  -storetype PKCS12 `
  -keystore keystore.p12 `
  -storepass "change-this-password" `
  -keypass "change-this-password" `
  -dname "CN=Slipstream" `
  -ext "SAN=ip:192.168.0.10"
```

`keystore.p12`는 `.gitignore`에 포함되어 있으므로 저장소에 커밋되지 않습니다. 자체 서명 인증서라면 휴대폰에서 인증서를 신뢰하지 않은 상태에서는 브라우저 경고가 표시됩니다. 인증서의 SAN에 접속 IP가 없을 때도 이름 불일치 경고가 발생합니다.

## 실행

Gradle의 `application.run` 작업은 기본적으로 `keystore.p12`와 `change-this-password`를 환경 변수로 자동 주입합니다. 따라서 예시 키스토어를 만들었다면 바로 실행할 수 있습니다.

```powershell
.\gradlew.bat run
```

키스토어 경로나 비밀번호가 다르면 Gradle 속성으로 재정의합니다.

```powershell
.\gradlew.bat run `
  -PslipstreamKeystore="C:\path\to\certificate.p12" `
  -PslipstreamKeystorePassword="your-password"
```

IntelliJ IDEA에서는 Gradle 창의 `Tasks > application > run`을 실행하면 됩니다.

실행하면 제어 창에 사용 가능한 로컬 HTTPS 주소와 QR 코드가 표시됩니다.

1. 휴대폰과 서버 PC를 같은 네트워크에 연결합니다.
2. 드롭다운에서 사용할 PC의 로컬 IP를 선택합니다.
3. QR 코드를 스캔한 뒤, 휴대폰 브라우저의 인증서 경고를 처리합니다.
4. 웹 페이지에서 파일을 선택하거나 끌어다 놓고 업로드합니다.
5. 받은 파일은 프로젝트 루트의 `downloads/` 폴더에서 확인합니다.

## 보안 메모

- QR URL에는 실행할 때마다 새로 생성되는 접근 토큰이 들어 있습니다. 이 URL을 신뢰할 수 없는 사람과 공유하지 마세요.
- 서버는 로컬 네트워크 전송 용도입니다. 인터넷에 포트를 노출하지 마세요.
- HTTPS를 쓰더라도 자체 서명 인증서는 신뢰 체인을 제공하지 않습니다. 실사용 환경에서는 신뢰할 수 있는 인증서를 사용하세요.

## 프로젝트 구성

```text
src/main/java/me/yuhan8954/slipstream/
├── Slipstream.java              # HTTPS 서버 및 업로드 처리
├── SlipstreamControlWindow.java # 주소/QR/서버 제어 UI
└── QrPopupViewer.java           # QR 이미지 생성 도우미
```
