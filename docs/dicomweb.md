# DICOMweb (QIDO-RS / WADO-RS / STOW-RS)

GRAPHY は、古典的な DIMSE プロトコルに加え、HTTP/REST ベースの **DICOMweb** サービスをローカルサーバーとして公開できます。
DIMSE と同じデータベース・同じファイルストレージを共有するため、どちらのプロトコルで取り込んだデータも統一して管理されます。

| サービス | DIMSE 相当 | 説明 |
|---|---|---|
| **QIDO-RS** | C-FIND | HTTP GET によるスタディ・シリーズ・インスタンス検索 |
| **WADO-RS** | C-GET | HTTP GET による DICOM ファイル取得（multipart/related） |
| **STOW-RS** | C-STORE | HTTP POST による DICOM ファイル保存 |

---

<!-- new-page -->

## 有効化と設定

### 設定画面を開く

メインメニュー → **Edit** → **PACS Connection** → **DICOMweb (QIDO/WADO/STOW)** セクション

<!-- スクリーンショット: dicomweb_settings_01.png — PACSConnectionPrefs の DICOMweb セクション全体。有効化チェックボックス、Port フィールド、HTTPS チェックボックス、Keystore path、Keystore password、Update ボタンが表示されている状態 -->
![DICOMweb 設定パネル](images/dicomweb_settings_01.png)

### 設定項目

| 項目 | 説明 |
|---|---|
| **Enable DICOMweb** | DICOMweb サーバーを起動する |
| **Port** | 待ち受けポート番号（DIMSE の Port とは別の番号を指定） |
| **Use HTTPS (TLS)** | TLS 暗号化を有効にする（キーストアが必要） |
| **Keystore path** | JKS キーストアファイルのパス |
| **Keystore password** | キーストアのパスワード |
| **Basic 認証を有効にする** | HTTP Basic 認証でアクセス制御を行う |
| **ユーザー名** | 認証に使用するユーザー名 |
| **パスワード** | 認証パスワード（SHA-256 でハッシュ化して保存） |

### 設定手順（HTTP）

1. **Enable DICOMweb** にチェックを入れる
2. **Port** に未使用のポート番号を入力する（例: `8080`）
3. **Update DICOMweb Settings** をクリックする
4. ダイアログに従って GRAPHY を再起動する

再起動後、以下の URL でアクセスできます：

```
http://{GRAPHYが動いているPCのIPアドレス}:{Port}/dicomweb
```

!!! warning "HTTP は平文通信"
    HTTP モードでは通信内容が暗号化されません。
    院内の信頼できるネットワーク内での利用に限定し、インターネットに公開しないでください。
    インターネット経由でアクセスする場合は HTTPS を使用してください。

---

<!-- new-page -->

## HTTPS (TLS) の設定

TLS を使用することで通信内容が暗号化され、院外からの安全なアクセスが可能になります。

### ステップ 1: キーストアの作成

GRAPHY に付属する Java の `keytool` コマンドを使って JKS キーストアを作成します。
以下のコマンドを**一度だけ**実行してください。

=== "Windows"
    ```bat
    keytool -genkeypair ^
      -alias graphy ^
      -keyalg RSA -keysize 2048 -validity 3650 ^
      -keystore C:\graphy\graphy-keystore.jks ^
      -storepass mypassword -keypass mypassword ^
      -dname "CN=192.168.1.100, OU=Radiology, O=Hospital, C=JP"
    ```

=== "Linux / macOS"
    ```bash
    keytool -genkeypair \
      -alias graphy \
      -keyalg RSA -keysize 2048 -validity 3650 \
      -keystore ~/graphy/graphy-keystore.jks \
      -storepass mypassword -keypass mypassword \
      -dname "CN=192.168.1.100, OU=Radiology, O=Hospital, C=JP"
    ```

| オプション | 説明 |
|---|---|
| `-alias graphy` | キーストア内のキー名（任意） |
| `-keyalg RSA -keysize 2048` | RSA 2048 ビット（推奨の最低限） |
| `-validity 3650` | 有効期限（日数）。3650 = 約 10 年 |
| `-keystore` | 保存先ファイルパス（`.jks` 拡張子） |
| `-storepass` / `-keypass` | キーストアとキーのパスワード（同じ値を設定） |
| `-dname` の `CN` | GRAPHYが動いているPCのIPアドレスまたはホスト名 |

!!! tip "`keytool` の場所"
    `keytool` は Java に同梱されています。GRAPHY のインストール先に JRE が含まれている場合は：

    - Windows: `{GRAPHYフォルダ}\jre-bins\windows\bin\keytool.exe`
    - Linux: `{GRAPHYフォルダ}/jre-bins/linux/bin/keytool`

### ステップ 2: GRAPHY に設定する

1. **Use HTTPS (TLS)** にチェックを入れる
2. **Keystore path** に作成した `.jks` ファイルのパスを入力（**Browse…** ボタンで選択可能）
3. **Keystore password** にパスワードを入力する
4. **Update DICOMweb Settings** をクリックして再起動する

再起動後、以下の URL でアクセスできます：

```
https://{IPアドレスまたはホスト名}:{Port}/dicomweb
```

### 自己署名証明書とクライアントの設定

`keytool` で作成した証明書は**自己署名証明書**です。
クライアント（curl、Python など）はデフォルトで証明書の検証を行うため、
自己署名証明書を使用している場合は検証をスキップする設定が必要です。

!!! warning "本番環境での注意"
    自己署名証明書でも**通信は暗号化**されます。ただし、証明書の正当性（なりすましでないこと）は保証されません。
    本番環境では CA（認証局）が署名した証明書の使用を推奨します。

---

<!-- new-page -->

## Basic 認証の設定

HTTP Basic 認証を使用することで、ユーザー名とパスワードを知っているクライアントのみが DICOMweb サービスにアクセスできるようになります。

!!! tip "HTTPS との組み合わせを強く推奨"
    Basic 認証は、ユーザー名とパスワードを Base64 エンコードして送信します。
    **HTTP（平文）のまま Basic 認証を使うと、ネットワーク上でパスワードが盗聴される危険があります。**
    必ず **HTTPS (TLS)** と組み合わせて使用してください。

### 設定手順

1. **HTTPS (TLS)** をまず有効にする（前節参照）
2. **Basic認証を有効にする** にチェックを入れる
3. **ユーザー名** を入力する（例: `gruser`）
4. **パスワード** を入力する（変更しない場合は空欄のまま）
5. **Update DICOMweb Settings** をクリックし、再起動する

パスワードは SHA-256 ハッシュとしてデータベースに保存されます。平文パスワードは保存されません。

### curl での使用例

=== "HTTP + 認証（非推奨）"
    ```bash
    # --user でユーザー名:パスワードを指定
    curl --user gruser:secret123 \
      "http://192.168.1.100:8080/dicomweb/studies"
    ```

=== "HTTPS + 認証（推奨）"
    ```bash
    # 自己署名証明書の場合は --insecure を追加
    curl --insecure --user gruser:secret123 \
      "https://192.168.1.100:8443/dicomweb/studies"

    # CA 署名証明書の場合
    curl --user gruser:secret123 \
      "https://192.168.1.100:8443/dicomweb/studies"
    ```

=== "STOW-RS + 認証"
    ```bash
    curl --insecure --user gruser:secret123 \
      -X POST \
      -H "Content-Type: multipart/related; type=\"application/dicom\"; boundary=GRAPHY_BOUNDARY" \
      --data-binary @stow_request.bin \
      "https://192.168.1.100:8443/dicomweb/studies"
    ```

### Python での使用例

```python
import requests

# HTTPS + Basic 認証（自己署名証明書は verify=False）
auth = ("gruser", "secret123")
url = "https://192.168.1.100:8443/dicomweb"

# QIDO-RS: スタディ検索
r = requests.get(f"{url}/studies", auth=auth, verify=False)
studies = r.json()
print(f"{len(studies)} studies found")

# STOW-RS: ファイル送信
boundary = "GRAPHY_BOUNDARY"
with open("sample.dcm", "rb") as f:
    dcm_bytes = f.read()

body = (
    f"--{boundary}\r\n"
    "Content-Type: application/dicom\r\n\r\n"
).encode() + dcm_bytes + f"\r\n--{boundary}--\r\n".encode()

headers = {
    "Content-Type": f'multipart/related; type="application/dicom"; boundary={boundary}'
}
r = requests.post(f"{url}/studies", data=body, headers=headers,
                  auth=auth, verify=False)
print(r.status_code, r.text[:200])
```

### 認証エラー時の動作

認証に失敗した場合、サーバーは HTTP **401 Unauthorized** を返します。

```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="GRAPHY DICOMweb"
```

正しい認証情報を設定して再試行してください。

---

<!-- new-page -->

## QIDO-RS（検索）

GRAPHY のローカルデータベースに登録されている DICOM データを HTTP GET で検索します。
レスポンスは **DICOM JSON**（`application/dicom+json`）形式で返ります。

### エンドポイント

| エンドポイント | 内容 |
|---|---|
| `GET /dicomweb/studies` | スタディ一覧の検索 |
| `GET /dicomweb/studies/{studyUID}/series` | 指定スタディのシリーズ一覧 |
| `GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances` | 指定シリーズのインスタンス一覧 |

### クエリパラメータ（スタディ検索）

| パラメータ | DICOM タグ | 説明 |
|---|---|---|
| `PatientID` | (0010,0020) | 患者 ID |
| `PatientName` | (0010,0010) | 患者名（`%` でワイルドカード） |
| `PatientBirthDate` | (0010,0030) | 生年月日 |
| `AccessionNumber` | (0008,0050) | アクセッション番号 |
| `StudyDate` | (0008,0020) | 検査日 |
| `StudyDescription` | (0008,1030) | 検査説明 |
| `ModalitiesInStudy` | (0008,0061) | モダリティ |

### 使用例

=== "curl (HTTP)"
    ```bash
    # 全スタディを取得
    curl "http://192.168.1.100:8080/dicomweb/studies"

    # PatientID で絞り込み
    curl "http://192.168.1.100:8080/dicomweb/studies?PatientID=P001"

    # 患者名の部分一致（% をエンコード → %25）
    curl "http://192.168.1.100:8080/dicomweb/studies?PatientName=YAMADA%25"

    # シリーズ一覧
    curl "http://192.168.1.100:8080/dicomweb/studies/1.2.840.xxxxx/series"

    # インスタンス一覧
    curl "http://192.168.1.100:8080/dicomweb/studies/1.2.840.xxxxx/series/1.2.840.yyyyy/instances"
    ```

=== "curl (HTTPS・自己署名証明書)"
    ```bash
    # --insecure オプションで証明書検証をスキップ
    curl --insecure "https://192.168.1.100:8443/dicomweb/studies?PatientID=P001"
    ```

=== "Python requests (HTTP)"
    ```python
    import requests

    base = "http://192.168.1.100:8080/dicomweb"
    resp = requests.get(f"{base}/studies", params={"PatientID": "P001"})
    studies = resp.json()
    for study in studies:
        uid = study["0020000D"]["Value"][0]
        print("StudyUID:", uid)
    ```

=== "Python requests (HTTPS・自己署名証明書)"
    ```python
    import requests
    import urllib3

    # 自己署名証明書の警告を抑制（開発・院内環境向け）
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    base = "https://192.168.1.100:8443/dicomweb"
    resp = requests.get(f"{base}/studies", params={"PatientID": "P001"}, verify=False)
    studies = resp.json()
    ```

### レスポンス例（スタディ検索）

```json
[
  {
    "0020000D": {"vr": "UI",  "Value": ["1.2.840.10008.5.1.4.1.1.2.1234"]},
    "00100020": {"vr": "LO",  "Value": ["P001"]},
    "00100010": {"vr": "PN",  "Value": [{"Alphabetic": "YAMADA^TARO"}]},
    "00080020": {"vr": "DA",  "Value": ["2024-01-15"]},
    "00080050": {"vr": "SH",  "Value": ["ACC-0001"]},
    "00201206": {"vr": "IS",  "Value": [3]},
    "00201208": {"vr": "IS",  "Value": [120]}
  }
]
```

結果が 0 件の場合は HTTP **204 No Content** が返ります。

---

<!-- new-page -->

## WADO-RS（取得）

指定したスタディ・シリーズ・インスタンスの DICOM ファイルを取得します。
レスポンスは **`multipart/related; type="application/dicom"`** 形式（RFC 2387）で、
複数インスタンスがひとつのレスポンスにパートとして含まれます。

### エンドポイント

| エンドポイント | 内容 |
|---|---|
| `GET /dicomweb/studies/{studyUID}` | スタディ内の全インスタンスを取得 |
| `GET /dicomweb/studies/{studyUID}/series/{seriesUID}` | シリーズ内の全インスタンスを取得 |
| `GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}` | 単一インスタンスを取得 |

### 使用例

=== "curl (HTTP)"
    ```bash
    # 単一インスタンスを取得してファイルに保存
    curl -o result.multipart \
      "http://192.168.1.100:8080/dicomweb/studies/1.2.840.xxxxx/series/1.2.840.yyyyy/instances/1.2.840.zzzzz"

    # シリーズ内の全インスタンスを一括取得
    curl -o series.multipart \
      "http://192.168.1.100:8080/dicomweb/studies/1.2.840.xxxxx/series/1.2.840.yyyyy"
    ```

=== "curl (HTTPS・自己署名証明書)"
    ```bash
    curl --insecure -o result.multipart \
      "https://192.168.1.100:8443/dicomweb/studies/1.2.840.xxxxx/series/1.2.840.yyyyy/instances/1.2.840.zzzzz"
    ```

=== "Python（multipart を DICOM ファイルに分割）"
    ```python
    import requests

    base = "http://192.168.1.100:8080/dicomweb"
    study_uid  = "1.2.840.xxxxx"
    series_uid = "1.2.840.yyyyy"
    sop_uid    = "1.2.840.zzzzz"

    resp = requests.get(f"{base}/studies/{study_uid}/series/{series_uid}/instances/{sop_uid}")

    # Content-Type から boundary を取得
    content_type = resp.headers["Content-Type"]
    boundary = content_type.split("boundary=")[1].strip('"')

    # multipart を DICOM パートに分割
    parts = resp.content.split(f"--{boundary}".encode())
    saved = 0
    for i, part in enumerate(parts[1:], 1):
        header_end = part.find(b"\r\n\r\n")
        if header_end < 0:
            continue
        body = part[header_end + 4:]
        if body.endswith(b"\r\n"):
            body = body[:-2]
        if body.startswith(b"--"):   # closing delimiter
            break
        with open(f"instance_{i:04d}.dcm", "wb") as f:
            f.write(body)
        saved += 1

    print(f"{saved} インスタンスを保存しました")
    ```

=== "Python (HTTPS・自己署名証明書)"
    ```python
    import requests, urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    resp = requests.get(
        "https://192.168.1.100:8443/dicomweb/studies/1.2.840.xxxxx/...",
        verify=False
    )
    # 以降は HTTP と同じ処理
    ```

### ストレージの場所

STOW-RS または DIMSE C-STORE で受信したファイルは以下に保存されます：

```
{GRAPHYのストレージフォルダ}/STOW/
  └── {PatientID}/
        └── {StudyInstanceUID}/
              └── {SeriesInstanceUID}/
                    └── {SOPInstanceUID}.dcm
```

ファイルは通常の DICOM ファイル（Part 10 バイナリ形式）です。
不要になった場合は GRAPHY の UI からスタディを削除するか、
このフォルダから直接削除できます。

---

<!-- new-page -->

## STOW-RS（保存）

外部システムから GRAPHY へ DICOM ファイルを HTTP POST で送信・保存します。
受信したデータは DIMSE C-STORE と同じデータベース・同じストレージに書き込まれ、
GRAPHY のツリーテーブルにそのまま表示されます。

### エンドポイント

| エンドポイント | 内容 |
|---|---|
| `POST /dicomweb/studies` | DICOM ファイルを GRAPHY へ保存 |

リクエストの `Content-Type` は必ず `multipart/related; type="application/dicom"` にしてください。

### 使用例

=== "curl (HTTP)"
    ```bash
    BOUNDARY="graphy-boundary-001"

    {
      printf -- "--${BOUNDARY}\r\n"
      printf "Content-Type: application/dicom\r\n\r\n"
      cat /path/to/image.dcm
      printf "\r\n--${BOUNDARY}--\r\n"
    } | curl -X POST \
        -H "Content-Type: multipart/related; type=\"application/dicom\"; boundary=\"${BOUNDARY}\"" \
        --data-binary @- \
        "http://192.168.1.100:8080/dicomweb/studies"
    ```

=== "curl (HTTPS・自己署名証明書)"
    ```bash
    BOUNDARY="graphy-boundary-001"

    {
      printf -- "--${BOUNDARY}\r\n"
      printf "Content-Type: application/dicom\r\n\r\n"
      cat /path/to/image.dcm
      printf "\r\n--${BOUNDARY}--\r\n"
    } | curl --insecure -X POST \
        -H "Content-Type: multipart/related; type=\"application/dicom\"; boundary=\"${BOUNDARY}\"" \
        --data-binary @- \
        "https://192.168.1.100:8443/dicomweb/studies"
    ```

=== "Python（単一ファイル）"
    ```python
    import requests
    import uuid

    base = "http://192.168.1.100:8080/dicomweb"
    boundary = f"graphy-{uuid.uuid4()}"

    with open("/path/to/image.dcm", "rb") as f:
        dcm_bytes = f.read()

    body = (
        f"--{boundary}\r\n"
        "Content-Type: application/dicom\r\n\r\n"
    ).encode() + dcm_bytes + f"\r\n--{boundary}--\r\n".encode()

    resp = requests.post(
        f"{base}/studies",
        headers={"Content-Type": f'multipart/related; type="application/dicom"; boundary="{boundary}"'},
        data=body
    )
    print(resp.status_code, resp.json())
    ```

=== "Python（複数ファイル）"
    ```python
    import requests, uuid, glob

    base = "http://192.168.1.100:8080/dicomweb"
    boundary = f"graphy-{uuid.uuid4()}"
    files = sorted(glob.glob("/path/to/series/*.dcm"))

    body = b""
    for path in files:
        with open(path, "rb") as f:
            body += (
                f"--{boundary}\r\nContent-Type: application/dicom\r\n\r\n"
            ).encode() + f.read() + b"\r\n"
    body += f"--{boundary}--\r\n".encode()

    resp = requests.post(
        f"{base}/studies",
        headers={"Content-Type": f'multipart/related; type="application/dicom"; boundary="{boundary}"'},
        data=body
    )
    result = resp.json()
    succeeded = result.get("00081199", {}).get("Value", [])
    failed    = result.get("00081198", {}).get("Value", [])
    print(f"成功: {len(succeeded)} 件, 失敗: {len(failed)} 件")
    ```

=== "Python (HTTPS・自己署名証明書)"
    ```python
    import requests, uuid, urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    # 上記と同様のコードで、requests.post(..., verify=False) を指定
    resp = requests.post(url, headers=headers, data=body, verify=False)
    ```

### レスポンス

**成功（HTTP 200）**：

```json
{
  "00081199": {
    "vr": "SQ",
    "Value": [
      {
        "00081150": {"vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"]},
        "00081155": {"vr": "UI", "Value": ["1.2.840.zzzzz"]}
      }
    ]
  }
}
```

**一部失敗（HTTP 200）** — 成功分と失敗分が両方含まれます：

```json
{
  "00081199": {"vr": "SQ", "Value": [{ "00081155": {"vr": "UI", "Value": ["成功したUID"]} }]},
  "00081198": {"vr": "SQ", "Value": [{ "00081155": {"vr": "UI", "Value": ["失敗したUID"]} }]}
}
```

**全件失敗（HTTP 400）**：`00081198` のみ返ります。

### 重複送信の扱い

同一 SOPInstanceUID のファイルを再送した場合、既存ファイルはそのまま保持され、
レスポンスの成功リスト（`00081199`）に含まれます（冪等操作）。

---

<!-- new-page -->

## 外部システムとの連携

### OHIF ビューワ

OHIF（Open Health Imaging Foundation）ビューワから GRAPHY のデータを参照できます。

1. [OHIF ビューワ](https://ohif.org/)の設定でサーバーを追加する
2. DICOMweb URL に GRAPHY のエンドポイントを指定する：

    ```
    http://192.168.1.100:8080/dicomweb
    ```

    HTTPS の場合：

    ```
    https://192.168.1.100:8443/dicomweb
    ```

### 自動インポートスクリプト

モダリティや外部 PACS から定期的に STOW-RS で送信することで、
GRAPHY への自動インポートを実現できます。

```bash
#!/bin/bash
# 指定フォルダ内の .dcm ファイルを GRAPHY へ自動送信する例
GRAPHY_URL="http://192.168.1.100:8080/dicomweb/studies"
WATCH_DIR="/incoming/dicom"

for dcm in "$WATCH_DIR"/*.dcm; do
  BOUNDARY="b-$(date +%s%N)"
  {
    printf -- "--${BOUNDARY}\r\n"
    printf "Content-Type: application/dicom\r\n\r\n"
    cat "$dcm"
    printf "\r\n--${BOUNDARY}--\r\n"
  } | curl -s -X POST \
      -H "Content-Type: multipart/related; type=\"application/dicom\"; boundary=\"${BOUNDARY}\"" \
      --data-binary @- \
      "$GRAPHY_URL" && rm "$dcm"
done
```

---

## セキュリティに関する注意事項

| 項目 | 現バージョンの状態 |
|---|---|
| 通信暗号化 | HTTP（平文）または HTTPS（TLS）を選択可能 |
| 認証 | **HTTP Basic 認証**（オプション、HTTPS との組み合わせを推奨） |
| 認可 | **未実装**（認証済みユーザーは全データにアクセス可能） |

!!! warning "セキュリティ対策のチェックリスト"
    DICOMweb サーバーを安全に運用するために、以下を確認してください。

    - [ ] **HTTPS (TLS)** を有効にして通信を暗号化する
    - [ ] **Basic 認証**を有効にしてアクセスを制限する
    - [ ] ファイアウォールで DICOMweb ポートの外部公開を制限する
    - [ ] 院外公開する場合は CA 署名証明書を使用する

!!! info "HTTPS と Basic 認証の違い"
    - **HTTPS (TLS)**: 通信経路を暗号化する。盗聴・改ざんを防ぐ。
    - **Basic 認証**: ユーザー名とパスワードでアクセスを制限する。誰がアクセスできるかを制御する。
    - この 2 つは**別の機能**で、互いに補完します。両方を有効にすることで安全なアクセス環境を構築できます。
