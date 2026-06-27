# GRAPHY Reporting — 開発タスク進捗

最終更新: 2026-06-27 (全P1/P2/TD タスク完了 — P2-3サムネイル注入も実装済)

---

## Priority 1 — 臨床利用に必須 ✅ 全8件完了

| # | タスク | 状態 |
|---|--------|------|
| P1-1 | 患者・検査情報の自動入力 | ✅ |
| P1-2 | レポートセクション構造化（初期テンプレート） | ✅ |
| P1-3 | 未保存変更の確認ダイアログ | ✅ |
| P1-4 | 印刷（JEditorPane.print()） | ✅ |
| P1-5 | 確定SR → PACSへのC-STORE（SrSendDialog） | ✅ |
| P1-6 | テンプレート管理UI（CRUD + DB保存） | ✅ |
| P1-7 | 追記（Addendum）サポート | ✅ |
| P1-8 | SR CompletionFlag / VerificationFlag | ✅ |

---

## Priority 2 — 業務効率・品質向上 ✅ 全8件完了

| # | タスク | 状態 |
|---|--------|------|
| P2-1 | Verification Observer Sequence (0040,A073) | ✅ |
| P2-2 | レポートロック（同時編集防止） | ✅ |
| P2-3 | キー画像サムネイル表示（MDプレビュー） | ✅ |
| P2-4 | レポートリストのフィルタ | ✅ |
| P2-5 | Undo/Redo | ✅ |
| P2-6 | 紹介医師・臨床情報フィールド | ✅ |
| P2-7 | ReportManagerDialogのカラム強化 | ✅ |
| P2-8 | KO（Key Object Selection）オブジェクト生成 | ✅ |

---

## マークダウンエディタ — ✅ 全項目完了

---

## 技術的負債

| # | タスク | 状態 |
|---|--------|------|
| TD-1 | HTML→プレーンテキスト変換精度 | ✅ 一部対応 |
| TD-2 | テンプレートのDB管理化 | ✅ 完了 |
| TD-3 | StudyDate型の統一 | ⬜ 別PR |
| TD-4 | PATIENTモードのインポートSR表示 | ✅ 完了 |

---

## 実装済み（このセッション後半）

### P2-3: キー画像サムネイル表示（MDプレビュー・SRビューア）
- `DicomThumbnailCache.java` 新規作成
  - dcm4che3-imageio の `DicomImageReader` でDICOMピクセル読み込み（VOI LUT自動適用）
  - 最長辺150pxにBILINEARダウンスケール → PNG → Base64 data URI
  - LRUキャッシュ（最大64エントリ）、ネガティブキャッシュ（失敗時は空文字sentinel）
- `KeyImageHtmlInjector.java` 新規作成
  - 正規表現で `<a href="graphy://image/...">` を検出
  - `DicomThumbnailCache` でサムネイルを取得し div+img+label の埋め込みアンカーに置換
  - `hasKeyImages()` で安価な事前チェック（文字列検索）
- `MarkdownEditorPanel.refreshPreview()` を2段階化
  - Phase1: CommonMark → HTML を即時表示（ノーウェイト）
  - Phase2: SwingWorkerでバックグラウンドサムネイル注入 → EDT上でプレビュー更新
- `SRHtmlViewerWindow.setHtml()` に同様のSwingWorkerサムネイル注入追加

### P2-8: KO（Key Object Selection）生成
- `KeyObjectWriter.java` 新規作成
  - SOP Class: 1.2.840.10008.5.1.4.1.1.88.59
  - Modality: KO（SRではなく）、Series番号 "902"
  - IMAGE content items for each key image
  - CurrentRequestedProcedureEvidenceSequence（必須）
  - setVerificationObserver() でKOにも作成者を記録
- `ReportService.finalizeAsSR()`: キー画像がある場合にKOも自動生成
  - KO生成失敗でもSRは保存済み（フォールバック保護）

### TD-4: PATIENTモードのインポートSR表示
- `DatabaseHandler.getStudyUIDsForPatient()` 追加: STUDYテーブルから患者の全検査UID/日付を取得
- `ReportService.listStudiesForPatient()` 追加
- `ReportListPanel.reload()` PATIENT mode 更新:
  - GRAPHYレポートに加え、患者の全検査のインポートSRを列挙
  - フィルタ（Draft/Final）もインポートSRに対応（FINAL扱い）

### TD-2: テンプレートのDB管理化
- `REPORT_TEMPLATE.sql` 新規作成: TemplateID/Name/Category/Body/CreatedDateTime/ModifiedDateTime
- `Resources.SQL_REPORT_TEMPLATE` 追加
- `SCHEMA_TABLE_RESOURCES` に `SQL_REPORT_TEMPLATE` を追加（自動テーブル作成・マイグレーション対象）
- `DatabaseHandler.upsertTemplate()`, `deleteTemplate()`, `loadAllTemplates()`, `hasTemplates()` 追加
- `ReportTemplateStore` 全面改修:
  - ユーザ定義テンプレートを `REPORT_TEMPLATE` テーブルで管理
  - 初回起動時に `~/.graphy/report-templates.json` からDBへ自動マイグレーション
  - DBが使えない場合（テスト環境など）はJSONファイルにフォールバック

---

## 残タスク（保留）

| # | タスク | 理由 |
|---|--------|------|
| TD-3 | StudyDate型の統一 | String→LocalDate移行は既存コードへの影響範囲が広く、別PR推奨。 |
