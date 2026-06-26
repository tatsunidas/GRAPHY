# GRAPHY Web化 要件定義

> 作成日: 2026-06-26  
> ステータス: 確定

---

## 1. 概要

既存のスタンドアローン Java デスクトップアプリケーション GRAPHY を、以下の 2 モードで動作する Web ベースアプリケーションに移行する。

| モード | 説明 |
|---|---|
| **スタンドアローン** | Electron でデスクトップアプリとして動作。DICOM サーバーは組み込みの dcm4che を使用。ローカル PC のリソースを最大活用する。 |
| **Web アプリ** | ブラウザで動作。外部の dcm4chee サーバーと連携する。 |

---

## 2. 技術スタック

### フロントエンド
- **TypeScript + React** — UI 全体
- **Cornerstone3D** — 2D / MPR 描画（医療画像専用 WebGL ライブラリ）
- **VTK.js** — 3D ボリュームレンダリング（WebGL ベース）
- **Electron** — デスクトップアプリ化（スタンドアローンモード）
- ~~Vaadin~~ — 採用しない（3D/2D 描画との混在が問題）

### バックエンド
- **Spring Boot (Java)** — バックエンド全体。既存 Java コードを継続使用。
- **dcm4che** — DICOM 処理（既存の自作 DicomObject ラッパーを置き換え）
- **CUDA (JNI)** — GPU レンダリング（継続使用）
- **Derby (組み込み DB)** — スタンドアローンモードのローカルデータ管理

### デスクトップ起動方法（2 種類、どちらも Electron で対応）
1. デスクトップアイコンから直接起動
2. `graphy://` カスタムプロトコルから起動（病院 Web システムとの IHE IID 連携）

---

## 3. アーキテクチャ

```
┌─────────────────────────────────────────────┐
│           Electron                          │
│  ┌──────────────────────────────────────┐  │
│  │  React + TypeScript                  │  │
│  │  Cornerstone3D (2D/MPR)              │  │
│  │  VTK.js (3D)                         │  │
│  └──────────────────────────────────────┘  │
│  起動方法 1: デスクトップアイコン             │
│  起動方法 2: graphy:// プロトコル            │
└───────────────────┬─────────────────────────┘
                    │ localhost (standalone)
                    │ HTTPS / DICOMweb (web)
┌───────────────────┴─────────────────────────┐
│           Spring Boot (Java)                │
│                                             │
│  dcm4che / CUDA / Radiomics / Centerline    │
│  匿名化 / Histogram / Fusion / Plugin       │
│                                             │
│  [standalone]  組み込み dcm4che / Derby      │
│  [web]         外部 dcm4chee サーバー        │
└─────────────────────────────────────────────┘
```

### モード切り替え
Spring Boot のプロファイルで切り替える。

```
--spring.profiles.active=standalone
--spring.profiles.active=web
```

---

## 4. 開発要件

### 4.1 全機能移植
既存のすべての機能を Web 版に移植する。省略・後回しは行わない。
移植と同時に、アップデートが必要な機能はアップデートも実施する。

対象機能（主なもの）：

| カテゴリ | 機能 |
|---|---|
| 画像表示 | 2D ビューア、MPR、Curved MPR |
| 3D | ボリュームレンダリング（GL/CUDA）、Cinematic、MIP/MinIP、サーフェスレンダリング |
| ROI | Line, Arrow, Ellipse, Polygon, Freehand, Text, 3D ROI, 計測 |
| 処理 | Radiomics、Centerline 抽出、Fusion、セグメンテーション |
| DICOM | DIMSE (C-FIND/C-MOVE/C-STORE)、DICOMweb、TLS、匿名化 |
| データ | ローカルファイル読み込み、CD/DVD 書き込み |
| その他 | Plugin システム、核医学 (SUV)、NIfTI/PDF/Video インポート |

### 4.2 統合データフローインターフェース
スタンドアローンと Web でデータの流れが異なるため、差異を吸収する統合インターフェース・クラスを設ける。

```java
interface DicomDataService {
    List<PatientRecord>  findPatients(QueryParams q);
    List<StudyRecord>    findStudies(String patientId, QueryParams q);
    List<SeriesRecord>   findSeries(String studyUID, QueryParams q);
    List<InstanceRecord> findInstances(String seriesUID);

    Attributes   getInstance(String sopUID);
    PixelData    getPixelData(String sopUID, int frame);

    void storeInstance(Attributes obj);
    void storeRoi(RoiContext roi);
    List<RoiContext> loadRois(String sopUID);
    void deleteInstance(String sopUID);
}
```

| 実装クラス | 使用モード | 内部実装 |
|---|---|---|
| `StandaloneDicomDataService` | standalone | Derby DB + DcmQRSCP + ローカルFS |
| `WebDicomDataService` | web | QIDO-RS / WADO-RS / STOW-RS |

Spring Boot の DI により、プロファイルに応じて自動注入する。

### 4.3 dcm4che への置き換え
自作の DicomObject ラッパークラスを dcm4che の `Attributes` で置き換える。

| 現在（自作） | 置き換え先（dcm4che） |
|---|---|
| `com.vis.dicom.Tag` | `org.dcm4che3.data.Tag` |
| `com.vis.dicom.UID` | `org.dcm4che3.data.UID` |
| `com.vis.dicom.TagDict` | `org.dcm4che3.data.ElementDictionary` |
| 自作 DicomObject | `org.dcm4che3.data.Attributes` |

### 4.4 ユーザーマニュアル更新
新機能を作成・更新するたびに、`docs/` 以下のユーザーマニュアルを更新する。
- 文体：丁寧・詳細な説明文
- 対象読者：医療従事者（エンジニアでなくてもよい）
- スクリーンショットや操作手順を含める

### 4.5 テストコード
新機能を開発するたびにテストコードを作成する。

**テストデータの方針：**
- 実際の DICOM ファイルに依存しない
- テスト内でデジタルファントムをその場で生成する
- `DicomPhantomFactory` クラスを共通ファクトリとして整備する

```java
// テストデータ生成の例
Attributes ct = DicomPhantomFactory.createCtPhantom();        // 均一 CT ファントム
Attributes sphere = DicomPhantomFactory.createSpherePhantom(10.0); // 既知サイズの球
Attributes seg = DicomPhantomFactory.createSegmentation(ct);  // セグメンテーション付き
```

---

## 5. ROI の取り扱い

| | スタンドアローン | Web アプリ |
|---|---|---|
| 保存先 | Derby DB（現状通り） | DICOM SR / DICOM SEG → dcm4chee |
| 保存方法 | `DatabaseHandler.saveRoi()` | STOW-RS 経由 |
| 読み込み | Derby DB | WADO-RS → Cornerstone3D で描画 |

---

## 6. ピクセルデータのデコード

| 転送構文 | 担当 |
|---|---|
| 標準圧縮（JPEG, J2K, JPEG-LS, RLE）| Cornerstone3D（WASM）がブラウザ側でデコード |
| メーカー独自圧縮 | Java サーバー側（`Decompressor.java`）でデコードして非圧縮で返す |

---

## 7. 開発フェーズ

| Phase | 内容 |
|---|---|
| **Phase 0** | 準備：`DicomDataService` インターフェース定義 / dcm4che 置き換え / `DicomPhantomFactory` 作成 |
| **Phase 1** | Spring Boot REST API / DICOMweb エンドポイント整備 |
| **Phase 2** | React + Cornerstone3D で 2D / MPR ビューア |
| **Phase 3** | Electron ラップ + `graphy://` プロトコル登録 |
| **Phase 4** | VTK.js で 3D ボリュームレンダリング |
| **Phase 5** | Radiomics / Centerline / 高度機能を REST API 経由で呼び出し |

Phase 2 と Phase 3 は Phase 1 完了後、並行して進められる。

---

## 8. 非採用の技術と理由

| 技術 | 不採用理由 |
|---|---|
| **Vaadin** | 3D/2D 描画との混在で保守困難。React に統一する。 |
| **Java → JavaScript 全書き直し** | dcm4che 代替なし・430 ファイルの書き直しコストに見合わない。 |
| **純粋ブラウザ完結（Java なし）** | CUDA 不可・dcm4che 不可・Radiomics 計算不可。 |
| **JCEF** | Electron で同等機能をカバーできるため不要。 |
| **WebGPU（CUDA 代替）** | 現状（2026 年）ブラウザ対応が不安定・成熟度不足。将来の候補として保留。 |
