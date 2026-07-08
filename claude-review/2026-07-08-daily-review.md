# GRAPHY Daily Code Review — 2026-07-08

> **注**: 本レビューは直近24時間のコミットがなかったため、直近コミット（2026-06-27）を対象に実施しました。
> GitHub REST API がセッションプロキシポリシーによりブロックされているため、GitHub Issue は自動作成できませんでした。
> 以下の内容を手動で Issue 登録してください。

---

## Issue 1 — [NPEリスク] `ReportEditorDialog`: `DatabaseHandler.getInstance()` nullチェック漏れ（3箇所）

**カテゴリ**: NPEリスク  
**対象ファイル**: `src/main/java/com/vis/core/reporting/ui/ReportEditorDialog.java`

### 概要

`ReportEditorDialog.java` は `DatabaseHandler.getInstance()` を4箇所で呼び出しているが、
**3箇所（行509, 552, 617）では戻り値のnullチェックなしにメソッドを連鎖呼び出し**している。
同じクラスの行203では正しくnullチェックしており、一貫性がない。
`DatabaseHandler` が未初期化の状態でダイアログが開かれた場合、
キー画像のナビゲート・インライン画像正規化・キー画像挿入のいずれの操作でも NPE が発生し、
ユーザー操作がサイレントに失敗するか未ハンドルの例外としてスタックトレースを出力する。

### 対象箇所

| 行   | メソッド | 問題 |
|------|----------|------|
| 509  | `navigateToKeyImage()` | `DatabaseHandler.getInstance().getValueFromImage(...)` — nullチェックなし |
| 552  | `normalizeInlineKeyImages()` | `DatabaseHandler.getInstance().getValueFromImage(...)` — nullチェックなし |
| 617  | `insertKeyImage()` | `DatabaseHandler.getInstance().getValueFromImage(...)` — nullチェックなし |
| 203  | `buildPatientInfoPanel()` | ✅ `if (db != null && ...)` — 正しく対処済み |

### 問題のあるコード（例: 行509）

```java
// navigateToKeyImage() — DatabaseHandler が null の場合に NPE
String refUID = DatabaseHandler.getInstance().getValueFromImage(
        "FrameOfReferenceUID", pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID());
```

### リファクタリング提案

```java
// 共通ヘルパーを使うか、各呼び出し箇所に null ガードを追加する
private void navigateToKeyImage(KeyImageRef ref) {
    Viewer2DScreen viewer = Viewer2DScreen.getInstance();
    if (viewer == null) return;
    DatabaseHandler db = DatabaseHandler.getInstance();
    if (db == null) return;                          // ← 追加
    String pid = doc.getPatientID();
    String refUID = db.getValueFromImage(
            "FrameOfReferenceUID", pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID());
    if (refUID == null) refUID = "";
    viewer.loadSeriesAndNavigate(pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID(), refUID);
}
```

同様に `normalizeInlineKeyImages()` (行552) と `insertKeyImage()` (行617) にも同じ修正を適用する。
あるいは `ReportService` にこれらの DB ルックアップを委譲するメソッドを追加し、
UI クラスから `DatabaseHandler` への直接依存を排除する（責務分離の観点でも望ましい）。

### 期待される効果

- DB 未初期化時のクラッシュを防止し、無音で処理をスキップできる
- `DatabaseHandler.getInstance()` の呼び出し規約をクラス内で統一できる
- 将来的に UI と DB の間に `ReportService` を挟む際のリファクタリングが容易になる

---

## Issue 2 — [NPEリスク / AIOOBE] `MeasurementExtractor.fromPraparat()`: `prap.getUIDs()` 配列境界チェック漏れ

**カテゴリ**: NPEリスク  
**対象ファイル**: `src/main/java/com/vis/core/reporting/measurement/MeasurementExtractor.java` :57-60

### 概要

`fromPraparat()` は `prap.getUIDs()` の戻り値をインデックス 0〜4 で直接参照するが、
戻り値が `null` であるか長さが5未満の配列である場合に `NullPointerException` または
`ArrayIndexOutOfBoundsException` が発生する。
同クラスの `imageRefOf()` における `sg.getUIDs()` の Javadoc コメントには
`[patID, studyUID, seriesUID, sopUID]` と記載されており（4要素）、
`Praparat.getUIDs()` が FrameOfReferenceUID を含む5要素を返す保証はコード上で明確でない。
この問題は「計測値をSRエクスポート」ボタンを押したタイミングで再現し、
スタックトレースなしに処理が中断するか、ログに uncaught exception が残る。

### 問題のあるコード

```java
// MeasurementExtractor.java:57-60
public static MeasurementReport fromPraparat(Praparat prap, String title) {
    Object[] uids = prap.getUIDs();          // null の可能性あり
    String patID             = str(uids[0]); // uids==null → NPE
    String studyUID          = str(uids[1]);
    String frameOfReferenceUID = str(uids[4]); // 配列長 < 5 → ArrayIndexOutOfBoundsException
```

### リファクタリング提案

```java
public static MeasurementReport fromPraparat(Praparat prap, String title) {
    Object[] uids = prap.getUIDs();
    if (uids == null || uids.length < 5) {
        // getUIDs() が期待どおりの長さを返さない場合は空レポートを返す
        return new MeasurementReport(null, null,
                title == null || title.trim().isEmpty() ? "Measurements" : title);
    }
    String patID               = str(uids[0]);
    String studyUID            = str(uids[1]);
    String frameOfReferenceUID = str(uids[4]);
    // ... 以降は変更なし
```

または、`Praparat` に専用のアクセサメソッドを追加してインデックスアクセスを排除し、
APIの契約を明確にする方法も検討できる。

### 期待される効果

- 計測SR エクスポート時のクラッシュを防止
- 不正な `getUIDs()` の戻り値に対して空レポートを返すことで、呼び出し元の整合性を保つ
- `Praparat.getUIDs()` の返却長仕様をコード上で自己文書化できる

---

## Issue 3 — [NPEリスク] `SRReader.read()`: null入力に対するガードなし

**カテゴリ**: NPEリスク  
**対象ファイル**: `src/main/java/com/vis/core/reporting/sr/SRReader.java` :24-26, :31

### 概要

`SRReader.read(Attributes sr)` はパブリックな静的メソッドだが、
引数 `sr` が `null` の場合の保護がなく、`parseItem()` 内の
`item.getString(Tag.ValueType)` で NPE が発生する。
`ReportService.openSr()` などの呼び出し元は読み取り失敗時に `attr` が `null` になる可能性を
持っており（`reader.getHeader()` が null を返す可能性）、
その null を `SRtoHtml.toHtml()` → `SRReader.read()` に流すパスが存在する。
壊れたDICOM SRファイルや想定外のSOP Classを持つインスタンスを開こうとした場合に再現する。

### 問題のあるコード

```java
// SRReader.java:24-26
public static ContentItem read(Attributes sr) {
    return parseItem(sr, null); // sr == null の場合にガードなし
}

// parseItem():31
ci.setValueType(item.getString(Tag.ValueType)); // item == null → NPE
```

### リファクタリング提案

```java
/**
 * @param sr a read SR dataset. May be {@code null}; returns {@code null} in that case.
 * @return the root content item, or {@code null} if {@code sr} is null.
 */
public static ContentItem read(Attributes sr) {
    if (sr == null) {
        return null;
    }
    return parseItem(sr, null);
}
```

呼び出し側 (`SRtoHtml.toHtml()` や `MeasurementExtractor` の内部利用箇所) でも
戻り値が `null` の場合の処理を追加することを推奨。

### 期待される効果

- 壊れたDICOMファイルや非対応SOP Classの読み込みでのクラッシュを防止
- nullを返すことで呼び出し元が安全に「SR読み取り不可」状態を処理できる
- publicユーティリティAPIとしての堅牢性が向上する

---

## サマリー

| # | カテゴリ | ファイル | 行 | 優先度 |
|---|----------|----------|----|--------|
| 1 | NPEリスク | `ReportEditorDialog.java` | 509, 552, 617 | 高 |
| 2 | NPEリスク / AIOOBE | `MeasurementExtractor.java` | 57-60 | 高 |
| 3 | NPEリスク | `SRReader.java` | 24-26 | 中 |

---

*自動生成: Claude Daily Review Agent | 実行日: 2026-07-08 | ブランチ: claude/daily-review*  
*分析対象: 2026-06-27 コミット群 (142 Javaファイル) — reporting機能の新規追加コード中心*  
*備考: GitHub REST API がセッションプロキシポリシーでブロックされているため Issue 自動作成不可。手動での Issue 登録を推奨します。*
