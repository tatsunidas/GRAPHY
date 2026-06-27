# Reporting upgrade — multi-role participation (DICOM Observer/Role)

最終更新: 2026-06-27

## 目的
医師以外のスタッフ(放射線技師・医療助手・事務職員・研究者)もレポーティングに関与できるようにする。
DICOM SR の「関与タイプ(Participation Type)」と「職種(Organizational Role)」を取り込み、
*誰がどの役割で関与したか* を UI と SR の双方に記録する。GRAPHY にはログイン機構が無いため、
権限はアカウントではなく **レポート上で指定された関与者の職種** に基づくワークフロー・ガードレールとして実装する。

## 確定仕様(ユーザー合意)
- **職種(StaffRole)= 5職種**: 読影医 / 放射線技師 / 医療助手 / 事務職員 / 研究者
- **関与タイプ(ParticipationType)= 4種**: AUTHOR / VERIFIER / ENTERER / REVIEWER
- **承認(VERIFY)ガードはレポート種別ごと**:
  - 画像診断レポート(IMAGING_DIAGNOSTIC) → VERIFIER が **医師** でないと確定(Finalize/VERIFIED)不可
  - 検査技師レポート(TECHNOLOGIST) → VERIFIER が **放射線技師**(または医師)で確定可
  - 計測レポート(MEASUREMENT) → 制限なし
  - 医師は上位として全種別を承認可能
- **関与者の入力**: 軽量スタッフ名簿(STAFF テーブル)からプルダウン選択

## DICOM 符号化(規格準拠)
関与者は SR データセットの **ヘッダシーケンス** に符号化する(コンテンツツリーではなく、PACS が読む位置)。
- **AUTHOR** → Author Observer Sequence `(0040,A078)` 各 item
- **VERIFIER** → Verifying Observer Sequence `(0040,A073)` 各 item。VERIFIER があれば `VerificationFlag=VERIFIED`
- **ENTERER** → Participant Sequence `(0040,A07A)`, ParticipationType `(0040,A080)` = `ENT`
- **REVIEWER** → Participant Sequence `(0040,A07A)`, ParticipationType `(0040,A080)` = `ATTEST`

各 Author/Participant item の中身:
- Observer Type `(0040,A084)` = `PSN`
- Person Name `(0040,A123)` = 氏名(PN)
- Institution Name `(0008,0080)` = 施設名 or "GRAPHY"
- **Organizational Role Code Sequence `(0044,010A)`** = 職種コード(下記)

Verifying Observer item: Verifying Observer Name `(0040,A075)`, Verification DateTime `(0040,A030)`,
Verifying Organization `(0040,A027)`。職種は GRAPHY 側 DB に保持(VERIFYING OBSERVER seq には role code 欄が無い)。

### 職種コード(StaffRole → DICOM Code)
CID 7452 "Organizational Role" にある2職種は標準コード、無い3職種はプライベートスキーム `99GRAPHY`。
| StaffRole | Code Value | Scheme | Meaning |
|---|---|---|---|
| PHYSICIAN | 309343006 | SCT | Physician |
| RADIOLOGIC_TECHNOLOGIST | 159016003 | SCT | Radiologic Technologist |
| MEDICAL_ASSISTANT | MEDASSIST | 99GRAPHY | Medical Assistant |
| CLERICAL_WORKER | CLERK | 99GRAPHY | Clerical Worker |
| SCIENTIST | SCIENTIST | 99GRAPHY | Scientist |

> 3職種は SNOMED CT 等の検証済みコードが得られ次第 `StaffRole` の1か所を差し替えるだけでよい。

## コード構成
- `com.vis.core.reporting.StaffRole`(enum, plain code strings; dcm4che 非依存)
- `com.vis.core.reporting.ParticipationType`(enum; participant-seq CS term を保持)
- `com.vis.core.reporting.ReportParticipant`(name/role/participation/organization/staffId/dateTimeMillis)
- `com.vis.core.reporting.ReportType` に IMAGING_DIAGNOSTIC / TECHNOLOGIST を追加し `allowedVerifierRoles`+`canVerify(role)` を持たせる(GENERAL/MEASUREMENT は後方互換で残す)
- `com.vis.core.reporting.ReportDocument` に `List<ReportParticipant> participants`(JSON 永続化, 後方互換で author 欄も維持)
- `com.vis.core.reporting.staff.StaffMember` + `StaffStore`(DB-backed, テンプレートと同パターン)
- DB: `ReportDBKey.Participants`(REPORT に clob 列追加・自動マイグレーション)/ STAFF テーブル新設
- SR: `SrCommon.addObservers(...)` が上記ヘッダシーケンスを構築。`SRWriter`/`KeyObjectWriter`/`Tid1500Writer` から呼ぶ。`SRtoHtml` に関与者セクションを追加
- `ReportService` に承認ガード `checkVerifiable(doc)` を追加。`finalizeAsSR` が違反時は確定しない
- UI: `ReportEditorDialog` にレポート種別セレクタ + 関与者テーブル + 承認ガード。`StaffManagerDialog` で名簿 CRUD

## 後方互換
- 既存 REPORT 行: participants 空 + author 有り → 読込時に AUTHOR 1名(role 未設定)を合成
- 既存 ReportType "GENERAL" → そのまま残し承認制限なし。新規作成は IMAGING_DIAGNOSTIC 既定
