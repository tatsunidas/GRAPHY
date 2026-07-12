# GRAPHY Daily Review — 2026-07-12

**対象コミット**: `28d71a0c` (2026-07-10 15:53 JST)  
**変更ファイル**: 2件  
- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java`  
- `src/main/java/com/vis/core/view/D2/ui/glasses/Praparat.java`

---

## Issue 1 — [Claude Review] NPEリスク: `loadRoi2Slide()` の UID 一致パスでnullチェック漏れ

**カテゴリ**: NPEリスク  
**深刻度**: 高（MPEG/動画DICOMへのROIインポート時にクラッシュ）

### 概要

`loadRoi2Slide()` の UID 一致パス（`roiFramePos >= 0` ブランチ）で、
`realizeImage()` 呼び出し後に `getAllSlides().get(roiFramePos)` が返す
`SlideGlass` のnullチェックが存在しない。`realizeImage()` が失敗した場合
（破損フレーム、範囲外インデックス等）、次行の `s.addRoi(roiObj)` が
`NullPointerException` をスローする。

同じコミット内の InstanceNo フォールバックブランチ（line 2252–2263）では
`if (candidate != null)` ガードが正しく追加されているのに、UID一致ブランチには
同等のガードがない。MPEG/動画DICOMが対象のバグ修正で追加されたコードだけに、
まさにその用途で問題を引き起こすリスクが高い。

### 対象ファイル

- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java:2219` — `RoiObjManager#loadRoi2Slide`

### 問題のあるコード

```java
// line 2219–2225 (commit 28d71a0c)
if (roiFramePos >= 0) {
    // ★修正: MPEG動画等は遅延生成のため…
    prap.realizeImage(roiFramePos);
    SlideGlass s = prap.getAllSlides().get(roiFramePos);
    roiObj.setSlideGlass(s, false);  // ← s が null なら NPE
    s.addRoi(roiObj);                // ← NPE: NullPointerException
```

### リファクタリング提案

```java
if (roiFramePos >= 0) {
    prap.realizeImage(roiFramePos);
    SlideGlass s = prap.getAllSlides().get(roiFramePos);
    if (s == null) {
        Log.logger.warning("[ROI-IMPORT] Frame " + roiFramePos
                + " could not be realized; skipping ROI attachment.");
        continue;
    }
    roiObj.setSlideGlass(s, false);
    s.addRoi(roiObj);
```

### 期待される効果

- MPEG/動画DICOMの破損フレームへROI付与を試みた際のクラッシュを防止
- InstanceNoブランチと同等の安全性を確保（コードの一貫性向上）

---

## Issue 2 — [Claude Review] NPEリスク: `open()` null変数誤りと `openZip()` のドット欠落で全 .zip ファイルクラッシュ

**カテゴリ**: NPEリスク  
**深刻度**: 高（全 .zip ROI ファイルのオープンが常時クラッシュ）

### 概要

`openZip(String path)` の guard 条件が `path.endsWith("zip")` となっており
（ドット抜け）、`"archive.zip"` などの正常なパスでも常に `null` を返す。
さらに呼び出し元の `open()` が `if (rois != null)` （正しくは `rois_`）で
ヌルチェックしているため、`rois.addAll(null)` が `NullPointerException` を
スローする。この2つのバグが組み合わさり、**`open("*.zip")` は例外なしで
常にクラッシュ**する。

### 対象ファイル

- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java:2086` — `RoiObjManager#openZip`
- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java:2072` — `RoiObjManager#open`

### 問題のあるコード

```java
// Bug A — openZip() (line 2086)
public static List<Roi> openZip(String path) {
    if (path == null || path.endsWith("zip")) {  // ← "zip" で "archive.zip" も true → 常に null を返す
        return null;
    }
    ...

// Bug B — open() (line 2072)
List<Roi> rois = new ArrayList<>();        // rois は常に非null
if (path.endsWith(".zip")) {
    List<Roi> rois_ = openZip(path);       // null が返る (Bug A)
    if (rois != null) {                    // ← rois を見ているが、常に true
        rois.addAll(rois_);                // ← rois_.addAll(null) → NullPointerException
    }
}
```

### リファクタリング提案

```java
// Fix A — openZip() (line 2086)
public static List<Roi> openZip(String path) {
    if (path == null || !path.endsWith(".zip")) {  // ← ".zip" に修正
        return null;
    }
    ...

// Fix B — open() (line 2072)
    List<Roi> rois_ = openZip(path);
    if (rois_ != null) {                   // ← rois_ を見るように修正
        rois.addAll(rois_);
    }
```

### 期待される効果

- ZIP形式のROIファイル（複数ROI一括）を正常にインポートできるようになる
- NPEによるスタックトレース出力・UIフリーズを排除

---

## Issue 3 — [Claude Review] Javadoc不足: `getSlicePosition()` と `loadRoi2Slide()` の複雑な InstanceNo 照合ロジックが未文書化

**カテゴリ**: Javadoc  
**深刻度**: 中（保守性・理解コスト）

### 概要

2026-07-10 コミットで `getSlicePosition()` と `loadRoi2Slide()` に
MPEG/多フレームDICOM向けの InstanceNo 照合ロジックが追加されたが、
両メソッドとも package-private (実質 internal API) ながらJavadocが存在しない。
照合の優先順序（UID一致 → InstanceNo照合 → zPosition → 現在スライド）が
日本語コメントのみに記述されており、英語Javadocとして外部化されていない。
将来の開発者がこのロジックを修正・拡張する際の理解コストが高い。

### 対象ファイル

- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java:1974` — `RoiObjManager#getSlicePosition`
- `src/main/java/com/vis/core/view/D2/roi/RoiObjManager.java:2188` — `RoiObjManager#loadRoi2Slide`

### 問題のあるコード

```java
// line 1974 — No Javadoc
int getSlicePosition(Praparat pp, ij.gui.Roi roi) {
    ...
    // ★修正: MPEG動画等の多フレームDICOMは… (日本語コメントのみ)
```

### リファクタリング提案

```java
/**
 * Resolves the reading-order index of the slide that best matches the given
 * ImageJ ROI within the specified Praparat.
 *
 * <p>Matching strategy (in priority order):
 * <ol>
 *   <li>PatientID + StudyUID + SeriesUID + SOPInstanceUID must all agree.</li>
 *   <li>When the ROI carries {@link RoiDBKey#InstanceNo} <em>and</em> the
 *       candidate slide's instance number is known, the instance numbers must
 *       also agree.  This extra check is required for multi-frame (MPEG) DICOM
 *       where every frame shares the same SOPInstanceUID.</li>
 * </ol>
 *
 * @param pp  the Praparat (series) to search
 * @param roi the ImageJ ROI whose DICOM properties are used for matching
 * @return the reading-order key of the matching {@link SlideGlass}, or {@code -1}
 *         if no match is found
 */
int getSlicePosition(Praparat pp, ij.gui.Roi roi) {
```

### 期待される効果

- 将来の開発者がUIDのみvs UID+InstanceNoマッチングの意図を即座に理解できる
- MPEG/通常シリーズの違いがAPIドキュメントレベルで明確化される

---

*自動生成: Claude Daily Review Agent | 実行日: 2026-07-12 | ブランチ: claude/daily-review*  
*注意: GitHub REST API がこのセッションのプロキシポリシーでブロックされたため、Issue は自動作成されませんでした。上記をコピーして手動でIssueを作成してください。*
