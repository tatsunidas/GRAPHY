# DICOM 通信の TLS 暗号化（DIMSE over TLS）

GRAPHY の DICOM 通信（C-ECHO / C-STORE / C-FIND / C-MOVE / C-GET）は、標準では**平文（暗号化なし）**で行われます。
**DIMSE TLS** を有効にすると、これらの通信を **相互 TLS（mutual TLS）** で暗号化できます。

相互 TLS では、接続する**両者がそれぞれ証明書を提示し、互いに相手を検証**します。
これにより「通信内容の盗聴防止」だけでなく「接続相手が本物であることの確認（なりすまし防止）」も実現できます。

| 用語 | 意味 |
|---|---|
| **keystore（キーストア）** | GRAPHY 自身の「鍵 + 証明書」を入れたファイル。自分の身分証明に使う |
| **truststore（トラストストア）** | 「信頼する相手の証明書」を入れたファイル。相手の検証に使う |
| **相互 TLS** | 双方が証明書を提示し、双方が相手を検証する方式 |

!!! info "DICOMweb の HTTPS とは別物です"
    [DICOMweb](dicomweb.md) の HTTPS 設定と、この DIMSE TLS は**別の設定**です。
    DICOMweb は HTTP（REST）通信、DIMSE TLS は従来の DICOM（ポート 104 / 11112 など）通信を暗号化します。
    キーストアのファイルは両者で共用してもかまいません。

---

<!-- new-page -->

## 全体の流れ

DIMSE TLS を使うには、次の 3 ステップが必要です。

1. **鍵と証明書を作る**（`keytool` コマンド） … 自分の keystore と、相手を信頼するための truststore
2. **GRAPHY 自局に設定する**（設定画面） … TLS を有効化し、ポート・keystore・truststore を指定
3. **接続先ノードごとに「Use TLS」を ON にする** … TLS で通信したい相手だけを個別に指定

!!! note "平文と TLS は同時に使えます"
    DIMSE TLS を有効にしても、従来の平文 listener は**そのまま動き続けます**。
    TLS は**別のポート**で待ち受けるため、平文の相手も TLS の相手も両方と通信できます。

---

<!-- new-page -->

## ポートの考え方（送信用と受信用は別）

TLS の設定で最も混乱しやすいのが**ポート**です。
ポイントは、**「送信（GRAPHY → 相手）」と「受信（相手 → GRAPHY）」で使われるポートが別**ということです。

| 設定項目 | 向き | 何のためのポートか |
|---|---|---|
| **ノードの Port**（DICOM Communication Nodes） | **送信** | GRAPHY が接続しに行く**相手側**の TLS 受信ポート |
| **TLS Port**（Current DIMSE TLS） | **受信** | GRAPHY 自身が TLS で待ち受けるポート |

つまり、**各システムは「自分の TLS 受信ポート」を 1 つ持ち、相手へ接続するときは「相手の受信ポート」を指定する**、という関係です。

- **GRAPHY の TLS Port** … GRAPHY の「受信ポート」。相手が GRAPHY へ TLS で送ってくるときに使われます（送信には**使いません**）。
- **ノードの Port** … その相手の「受信ポート」。GRAPHY がその相手へ TLS で送るときに使われます。

!!! info "鍵（keystore / truststore）は送受信で共通"
    **TLS Port** は送信・受信で別ですが、**keystore（自分の証明書）と truststore（相手の証明書）はアプリ全体で共通**です。
    送信でも受信でも、同じ keystore で自分を証明し、同じ truststore で相手を検証します。

### 図解：GRAPHY と PACS がどちらも TLS ポート 2762 で待ち受ける場合

```
   [GRAPHY (PC-A)]                          [PACS (PC-B)]
   自分の TLS Port = 2762 で受信待ち         TLS 2762 で受信待ち

   ● GRAPHY → PACS へ送る（C-STORE など）
     ノードを登録: Host = PC-B / Port = 2762（← PACS の受信ポート）/ Use TLS = ON
     GRAPHY ───TLSで接続───▶ PC-B:2762

   ● PACS → GRAPHY へ送る
     GRAPHY 側: Current DIMSE TLS を有効化 / TLS Port = 2762（← GRAPHY の受信ポート）
     PACS 側: 送信先を PC-A:2762 に設定
     PC-B ───TLSで接続───▶ PC-A:2762
```

!!! warning "ノードの Port は「相手の TLS ポート」を入れる"
    「Use TLS = ON」のノードでは、**Port に相手の TLS 受信ポート**を入れてください。
    相手の**平文ポート**に対して TLS で接続することはできません（ハンドシェイクに失敗します）。

---

## ステップ 1: 鍵と証明書を作る

GRAPHY に同梱されている Java の `keytool` コマンドを使います。

!!! tip "`keytool` の場所"
    `keytool` は Java に同梱されています。GRAPHY のインストール先に JRE が含まれている場合は：

    - Windows: `{GRAPHYフォルダ}\jre-bins\windows\bin\keytool.exe`
    - Linux: `{GRAPHYフォルダ}/jre-bins/linux/bin/keytool`

### 1-1. 自分（GRAPHY）の keystore を作る

GRAPHY 自身の鍵ペアと自己署名証明書を作成します。**一度だけ**実行してください。

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
| `-dname` の `CN` | GRAPHY が動いている PC の IP アドレスまたはホスト名 |

### 1-2. お互いの証明書を交換して truststore を作る

相互 TLS では、**自分の証明書を相手に渡し、相手の証明書を自分の truststore に取り込む**必要があります。

まず、自分の証明書をファイルに書き出します。

=== "Windows"
    ```bat
    keytool -exportcert -alias graphy ^
      -keystore C:\graphy\graphy-keystore.jks -storepass mypassword ^
      -file C:\graphy\graphy.cer
    ```

=== "Linux / macOS"
    ```bash
    keytool -exportcert -alias graphy \
      -keystore ~/graphy/graphy-keystore.jks -storepass mypassword \
      -file ~/graphy/graphy.cer
    ```

次に、**相手から受け取った証明書ファイル（例: `peer.cer`）**を、自分の truststore に取り込みます。

=== "Windows"
    ```bat
    keytool -importcert -noprompt -alias peer ^
      -file C:\graphy\peer.cer ^
      -keystore C:\graphy\graphy-truststore.jks -storepass mypassword
    ```

=== "Linux / macOS"
    ```bash
    keytool -importcert -noprompt -alias peer \
      -file ~/graphy/peer.cer \
      -keystore ~/graphy/graphy-truststore.jks -storepass mypassword
    ```

!!! tip "信頼する相手が複数いる場合"
    相手ごとに `-alias` を変えて、同じ truststore へ追加で `-importcert` してください。
    truststore には信頼する相手の証明書を何件でも入れられます。

---

<!-- new-page -->

## ステップ 2: GRAPHY 自局に設定する

### 設定画面を開く

メインメニュー → **Edit** → **PACS Connection** → **DIMSE TLS (mutual)** セクション

<!-- スクリーンショット: dimse_tls_settings_01.png — PACSConnectionPrefs の DIMSE TLS セクション全体。Enable DIMSE TLS チェックボックス、TLS Port、Keystore path/password、Truststore path/password、TLS protocols、Cipher suites、Update ボタンが表示されている状態 -->
![DIMSE TLS 設定パネル](images/dimse_tls_settings_01.png)

### 設定項目

| 項目 | 説明 |
|---|---|
| **Enable DIMSE TLS (mutual)** | TLS 受信（listener）を有効にする |
| **TLS Port** | TLS 専用の待ち受けポート（平文の DIMSE Port とは**別の番号**。慣例では `2762`） |
| **Keystore path** | 自分の keystore（`graphy-keystore.jks`）のパス |
| **Keystore password** | keystore のパスワード |
| **Truststore path** | 相手を信頼するための truststore（`graphy-truststore.jks`）のパス |
| **Truststore password** | truststore のパスワード |
| **TLS protocols** | 使用する TLS バージョン（既定: `TLSv1.2,TLSv1.3`） |
| **Cipher suites** | 使用する暗号スイート（既定のままで通常 OK） |

### 設定手順

1. **Enable DIMSE TLS (mutual)** にチェックを入れる
2. **TLS Port** に未使用のポート番号を入力する（例: `2762`）
3. **Keystore path / password** に、ステップ 1 で作った keystore を指定する（**Browse…** で選択可能）
4. **Truststore path / password** に、ステップ 1 で作った truststore を指定する
5. **TLS protocols** / **Cipher suites** は、通常はそのままで構いません
6. **Update DIMSE TLS Settings** をクリックし、ダイアログに従って GRAPHY を再起動する

再起動後、GRAPHY は **平文ポート**と **TLS ポート**の両方で待ち受けるようになります。

!!! warning "相互 TLS には keystore と truststore の両方が必須"
    GRAPHY の TLS listener は、接続してきた相手にも証明書の提示を求めます（相互認証）。
    そのため keystore（自分の証明書）と truststore（相手の証明書）の**両方**が設定されていないと有効になりません。
    どちらかが空の場合、TLS listener は起動せず、平文 listener のみで動作します。

---

<!-- new-page -->

## ステップ 3: 接続先ノードごとに TLS を有効にする

送信（C-STORE / C-FIND / C-MOVE / C-GET / C-ECHO）で TLS を使うかどうかは、**接続先ノードごと**に指定します。

### 新しいノードを追加するとき

メインメニュー → **Edit** → **PACS Connection** → **Add**（ノード追加ダイアログ）

<!-- スクリーンショット: dimse_tls_addnode_01.png — AddDicomCommunicationNodeWin ダイアログ。Nickname / AE Title / Host / Port / Ciphers / Use TLS チェックボックスが表示されている状態 -->
![ノード追加ダイアログ](images/dimse_tls_addnode_01.png)

1. Nickname / AE Title / Host / Port を入力する
2. **Use TLS** にチェックを入れる
3. （任意）**Ciphers** に暗号スイートを指定する（空欄ならグローバルの既定値を使用）
4. 追加すると、GRAPHY は相互 TLS で接続テスト（C-FIND）を行い、成功したノードだけが登録されます

!!! note "接続先の Port は相手の TLS ポート"
    **Port** には、相手側の **TLS 用ポート**（相手が GRAPHY なら `2762` など）を入力してください。
    相手の平文ポートに対して TLS で接続することはできません。

### 既存のノードを TLS に切り替えるとき

PACS Connection の**ノード一覧テーブル**に **TLS** 列（チェックボックス）があります。
チェックを ON / OFF するだけで、そのノードの TLS 利用が切り替わります。

<!-- スクリーンショット: dimse_tls_nodetable_01.png — PACSConnectionPrefs のノード一覧テーブル。Nickname / AE title / Host / Port / Ciphers / TLS（チェックボックス）/ Ready 列が表示されている状態 -->
![ノード一覧テーブルの TLS 列](images/dimse_tls_nodetable_01.png)

| 列 | 説明 |
|---|---|
| **Ciphers** | そのノード専用の暗号スイート（空欄なら既定値） |
| **TLS** | ON にすると、このノードへの接続を相互 TLS で行う |
| **Ready** | 接続テスト（C-ECHO）の結果。TLS ノードは TLS で確認されます |

---

<!-- new-page -->

## 動作テスト（dcm4che を使った検証）

GRAPHY の TLS 設定が正しく機能しているかは、無料の DICOM ツールキット **[dcm4che](https://github.com/dcm4che/dcm4che/releases)** のコマンドラインツールを「対向役」にして確認できます。

ここでは、dcm4che の `storescp`（TLS 対応の受信サーバー）を相手に、GRAPHY から TLS で C-ECHO / C-STORE を送るテストを示します。

### 準備: テスト用の鍵を作る

簡単のため、1 つの鍵ペアを両者で共用する例です（実運用では GRAPHY 用・相手用を別々に作ってください）。

=== "Linux / macOS"
    ```bash
    cd /tmp/tlstest
    # 鍵ペア(keystore)を作成
    keytool -genkeypair -alias t -keyalg RSA -keysize 2048 -validity 3650 \
      -keystore ks.jks -storepass secret -keypass secret -dname "CN=tlstest"
    # 証明書を書き出して truststore に取り込む
    keytool -exportcert -alias t -keystore ks.jks -storepass secret -file t.cer
    keytool -importcert -noprompt -alias t -file t.cer -keystore ts.jks -storepass secret
    ```

### dcm4che 側を TLS 受信サーバーとして起動

```bash
storescp -b STORESCP@127.0.0.1:2762 \
  --tls-cipher TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 --tls-protocol TLSv1.2 \
  --key-store   ks.jks --key-store-type JKS --key-store-pass secret --key-pass secret \
  --trust-store ts.jks --trust-store-type JKS --trust-store-pass secret \
  --directory ./rcv
```

### GRAPHY 側の設定

1. 上記ステップ 2 で、GRAPHY の DIMSE TLS を有効化（keystore = `ks.jks`、truststore = `ts.jks`）
2. ステップ 3 で、ノードを `STORESCP@127.0.0.1:2762`・**Use TLS = ON** で追加
3. C-ECHO（接続テスト）や C-STORE（送信）を実行する

接続が成功すれば、GRAPHY ↔ dcm4che 間で相互 TLS が機能しています。

### dcm4che 側の TLS オプション早見表

| オプション | 説明 |
|---|---|
| `--tls-aes` | 暗号スイート `TLS_RSA_WITH_AES_128_CBC_SHA` を有効化 |
| `--tls-cipher <名前>` | 暗号スイートを個別指定（複数回指定可） |
| `--tls-protocol TLSv1.2` | TLS バージョンを指定（複数回指定可） |
| `--key-store <ファイル>` `--key-store-type JKS` `--key-store-pass <pw>` `--key-pass <pw>` | 自分の keystore |
| `--trust-store <ファイル>` `--trust-store-type JKS` `--trust-store-pass <pw>` | 相手検証用の truststore |
| `--tls-noauth` | クライアント認証を**要求しない**（相互 TLS では**付けない**） |

!!! warning "噛み合わせの 3 つの注意点"
    1. **暗号スイートとプロトコルを両者で揃える** — 片方が `TLSv1.2` 専用なら、もう片方も `TLSv1.2` を含めること。
    2. **キーストアの種類は JKS に揃える** — GRAPHY は JKS 固定なので、dcm4che 側も `--key-store-type JKS --trust-store-type JKS` を明示（dcm4che の既定は PKCS12）。
    3. **相互認証なので `--tls-noauth` は付けない** — 双方の truststore に相手の証明書が入っていることが必須です。

---

<!-- new-page -->

## うまく接続できないとき

| 症状 | 主な原因と対処 |
|---|---|
| `unable to find valid certification path` / `PKIX path building failed` | 相手の証明書が自分の truststore に入っていない。ステップ 1-2 で相手の証明書を取り込む |
| `No appropriate protocol` / `cipher suites are inappropriate` | 暗号スイート／TLS バージョンが両者で噛み合っていない。両側の **Cipher suites** / **TLS protocols** を揃える |
| `Received fatal alert: bad_certificate` / `certificate_unknown` | 相手側の truststore に**自分の**証明書が入っていない。自分の証明書（`graphy.cer`）を相手に渡して取り込んでもらう |
| TLS ノードの **Ready** が常に × | 相手の TLS ポート番号が違う、相手が起動していない、または証明書／cipher の不一致 |
| 接続自体がタイムアウトする | ファイアウォールで TLS ポート（例 `2762`）が閉じている。OS のファイアウォールで該当ポートの受信を許可してください |

!!! tip "詳しい失敗理由を見る"
    ハンドシェイクの失敗理由を詳しく知りたい場合は、GRAPHY の起動オプションに
    `-Djavax.net.debug=ssl:handshake` を付けて起動すると、コンソールに TLS ネゴシエーションの詳細が出力されます。
    「証明書未信頼」「cipher 不一致」「プロトコル不一致」のどれかを切り分けられます。

---

## 補足: 暗号スイートの既定値について

GRAPHY の既定の暗号スイートは、新しい順に次の 3 つです。

```
TLS_AES_128_GCM_SHA256,
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
TLS_RSA_WITH_AES_128_CBC_SHA
```

- 上 2 つは新しい Java でも有効なモダンな暗号で、TLS 1.3 / TLS 1.2 のどちらでも安全に接続できます。
- 3 つ目は古い PACS との互換用です（新しい Java では既定で無効ですが、互換性の保険として残しています）。

特別な要件がない限り、**既定のまま**で問題ありません。
相手が特定の暗号スイートしか受け付けない場合のみ、設定画面の **Cipher suites** で調整してください。
