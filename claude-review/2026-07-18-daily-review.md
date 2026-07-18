# GRAPHY Daily Code Review — 2026-07-18

**対象コミット**: `92a5a96f` — BUG Fix: Show mp4 frame position correctly (2026-07-17)  
**変更Javaファイル数**: 2件  
**検出問題数**: 3件  
**ブランチ**: `claude/daily-review`

---

## Issue 1 — [NPEリスク] VideoToDicomConverter.convertMpegVideo()のmp4VideoInfoがnullの場合のNPE

### 概要
`convertMpegVideo()` 内で `mp4Info.getVideo()` の戻り値 `mp4VideoInfo` をnullチェックせずに直接使用しています。入力動画にビデオストリームが含まれない場合（音声のみのMP4、破損ファイル等）、`getVideo()` がnullを返し、後続の `mp4VideoInfo.getSize().getWidth()` でNullPointerExceptionが発生します。

### 対象ファイル
- `src/main/java/com/vis/core/media/VideoToDicomConverter.java:118` — `VideoToDicomConverter#convertMpegVideo`

### 問題のあるコード
```java
// line 116-131
ws.schild.jave.MultimediaObject mp4Object = new ws.schild.jave.MultimediaObject(tempMp4);
ws.schild.jave.info.MultimediaInfo mp4Info = mp4Object.getInfo();
ws.schild.jave.info.VideoInfo mp4VideoInfo = mp4Info.getVideo();
// ↑ mp4Info.getVideo() はビデオストリームがない場合にnullを返す

long mp4FileSize = tempMp4.length();
// ...
int w = mp4VideoInfo.getSize().getWidth(); // ← NPE: mp4VideoInfo が null の場合
int h = mp4VideoInfo.getSize().getHeight();
```

### リファクタリング提案
```java
ws.schild.jave.info.VideoInfo mp4VideoInfo = mp4Info.getVideo();

// nullチェックを追加してユーザーに分かりやすいメッセージを提供する
if (mp4VideoInfo == null) {
    throw new IllegalArgumentException(
        "The transcoded MP4 file contains no video stream. "
        + "Input file may be audio-only or corrupted: " + videoFile.getName());
}

int w = mp4VideoInfo.getSize().getWidth(); // nullチェック済みで安全
int h = mp4VideoInfo.getSize().getHeight();
double fps = mp4VideoInfo.getFrameRate();
```

### 期待される効果
- 音声のみのMP4や破損ファイルを入力した場合に、NPEではなく原因が明確な `IllegalArgumentException` を発生させることで、デバッグが容易になる
- `finally` ブロックでの一時ファイル削除は引き続き正常動作するため、副作用なし

---

## Issue 2 — [NPEリスク] VideoReaderJCodec.read()のgetVideoTrack()がnullの場合のNPE

### 概要
`VideoReaderJCodec.read()` の105行目で `grab.getVideoTrack().getMeta()` を呼び出していますが、`grab.getVideoTrack()` がnullを返した場合（音声のみのファイル、映像トラックが存在しない破損ファイル等）にNullPointerExceptionが発生します。このメソッドはcatch節で例外を捕捉して `imp = null` にするものの、`catch (Exception e)` での握りつぶしのためスタックトレースのみがログに残り、ユーザーへのフィードバックが不明瞭です。

### 対象ファイル
- `src/main/java/com/vis/imageio/VideoReaderJCodec.java:105` — `VideoReaderJCodec#read`

### 問題のあるコード
```java
// line 101-110
in = NIOUtils.readableChannel(video);
FrameGrab grab = FrameGrab.createFrameGrab(in);

// 1. 動画のメタデータ（フレーム数、FPS）を取得
DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
// ↑ grab.getVideoTrack() が null の場合（映像トラックなし）にNPE発生
totalFrames = meta.getTotalFrames();
double totalDuration = meta.getTotalDuration();
```

### リファクタリング提案
```java
in = NIOUtils.readableChannel(video);
FrameGrab grab = FrameGrab.createFrameGrab(in);

// nullチェックでビデオトラックの存在を確認
if (grab.getVideoTrack() == null) {
    Log.logger.severe("No video track found in file: " + video.getName());
    return null;
}

DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
totalFrames = meta.getTotalFrames();
double totalDuration = meta.getTotalDuration();
```

### 期待される効果
- 映像トラックのないファイル（音声のみ、DICOM以外の特殊形式等）に対して、NPEではなく明確なログメッセージを出力して安全にnullを返す
- 呼び出し元（`convertRawVideo`）はすでにnullチェックしているため動作は変わらず、エラーの原因が明確になる

---

## Issue 3 — [重複コード] createMpegDicomHeader()とcreateDicomHeader()のPatient/Study設定コードが40行超で重複

### 概要
`VideoToDicomConverter` 内に `createMpegDicomHeader()`（line 166）と `createDicomHeader()`（line 355）の2つのヘッダ構築メソッドがあります。両メソッドにおけるPatient・Study関連のDICOMタグ設定コード（PatientName, PatientID, PatientSex, PatientBirthDate, StudyID, StudyDescription, StudyInstanceUID, SeriesDescription, SeriesNumber, Modality, StudyDate/Time, ContentDate/Time）が約40行にわたりほぼ完全に重複しており、DRY原則に違反しています。将来の変更（例: 新しい必須タグの追加）を片方にしか適用しないミスが起きやすい状態です。

### 対象ファイル
- `src/main/java/com/vis/core/media/VideoToDicomConverter.java:176-194` — `createMpegDicomHeader`内Patient/Study設定部
- `src/main/java/com/vis/core/media/VideoToDicomConverter.java:368-388` — `createDicomHeader`内Patient/Study設定部

### 問題のあるコード（重複部分の抜粋）
```java
// createMpegDicomHeader (line 176-194) と createDicomHeader (line 368-388) が同一
core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
if (dob != null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);
core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
// ... (以下同様)
```

### リファクタリング提案
```java
/**
 * Applies patient and study-level DICOM tags common to all video conversion outputs.
 */
private static void applyPatientStudyTags(DicomObject core,
        String patName, String patID, String sex, java.util.Date dob,
        String studyUID, String studyID, String studyDesc,
        java.util.Date studyDate, java.util.Date studyTime,
        java.util.Date contentDate, java.util.Date contentTime,
        String seriesDesc, int seriesNumber, int instanceNumber, Modality m) {

    core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
    core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
    core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
    if (dob != null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);
    core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
    core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
    core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
    core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
    core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber);
    core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], instanceNumber);
    core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], m == Modality.UNKNOWN ? "OT" : m.toString());
    if (studyDate != null) core.setDate(Tag.Study​Date, VR.DA, studyDate);
    if (studyTime != null) core.setDate(Tag.Study​Time, VR.TM, studyTime);
    if (contentDate != null) core.setDate(Tag.Content​Date, VR.DA, contentDate);
    if (contentTime != null) core.setDate(Tag.Content​Time, VR.TM, contentTime);
}

// createMpegDicomHeader と createDicomHeader それぞれで以下を呼ぶだけ:
// applyPatientStudyTags(core, patName, patID, sex, dob, studyUID, studyID, studyDesc,
//     studyDate, studyTime, contentDate, contentTime, seriesDesc, seriesNumber, instanceNumber, m);
```

### 期待される効果
- 新しいDICOMタグ追加・変更を1箇所に集約でき、片方への適用漏れを防止
- `createMpegDicomHeader` と `createDicomHeader` のコードをそれぞれ約30行削減可能
- 共通メソッドへのJavadocで患者・スタディタグの仕様を一元管理できる

---

*自動生成: Claude Daily Review Agent | 実行日: 2026-07-18 | ブランチ: claude/daily-review*  
*注意: GitHubプロキシポリシーによりIssue API呼び出し不可のため、このブランチにMarkdownで記録*
