# Reporting feature — work-in-progress handoff

This note carries context across machines (the Claude Code conversation and the
local `~/.claude` memory do NOT transfer between PCs). Pick this up on the other PC
with `git fetch` + `git checkout <this branch>`.

## What this branch contains (status: implemented, full test suite green — 229 tests)

DICOM radiology reporting feature in `com.vis.core.reporting`, plus integration into
the viewer, the main DICOM tree, and Bird's-eye view.

### Design decisions (locked, confirmed with user)
- Editable rich-text HTML (REPORT table, CLOB) is the **source of truth**; the DICOM SR
  is a derived artifact generated on **finalize**.
- General free-text report = **Comprehensive SR Storage** (.88.33). RDSR/KO/etc. are
  view-only.
- Reports join to studies by **StudyInstanceUID** (DICOM-native) — no special
  "reports-only re-attach" logic.
- SR/RDSR/KO **series are hidden** from the tree; reports are surfaced via a study-level
  **Report column** (icon + click → report list popup).
- Voice input + Japanese medical autocomplete dictionary are **deferred to Phase 2**
  (hook = `reporting.ui.TextInputAssist`, no-op default).

### Key components
- `com.vis.core.reporting`: `ReportDocument`, `KeyImageRef` (`graphy://image/...`),
  `ReportType` (GENERAL→ComprehensiveSR), `ReportService` (the single seam:
  saveDraft/listReports/loadReport/deleteReport/finalizeAsSR/openSr/listImportedSrInStudy).
- `com.vis.core.reporting.sr`: `SRWriter` (builds the Attributes tree; the INHERIT_TAGS
  array MUST stay ascending-sorted — `Attributes.addSelected` assumes sorted), `SRReader`,
  `ContentItem`, `SRtoHtml` (generic, also renders RDSR), `SopClassUtil`, `SRCodes`, `HtmlText`.
- `com.vis.core.reporting.template`: bundled `resources/reporting/templates.json`.
- `com.vis.core.reporting.ui`: `ReportEditorDialog`, `ReportListPanel`, `SRHtmlViewerWindow`,
  `TextInputAssist`.
- DB: `sql/REPORT.sql`, `Resources.SQL_REPORT`, `configuration/ReportDBKey`; CRUD + report-state
  queries in `DatabaseHandler` (`insertReport`/`updateReport`/`deleteReport`/`loadReportContext*`/
  `getStudyReportCounts`/`getReportInstancesInStudy`/`getFirstSopClassUIDInSeries`).
- DB migration: `DatabaseHandler.createTableIfMissing(...)` so a new table in
  `SCHEMA_TABLE_RESOURCES` reflects to BOTH fresh and existing DBs (createTables runs only on a
  fresh DB).
- Tree: `DICOMTreeTableModel.ReportCol` (index 21, moved to view position 2),
  `ReportCellRenderer` (● blue=report / ○ orange=draft / blank), `DICOMNodeBuilder` filters SR
  series + stamps `DICOMNode.ReportState`/`ReportCount`, `TreeTableMouseListener.openReportListPopup`.
- Viewer routing: `Viewer2DScreen.loadImagesOnStage` terminal guard SKIPS SR series (mixed-study
  double-click shows images, never auto-opens reports); explicit SR open via SR-node double-click
  (`TreeTableMouseListener.routeSrNode`) / Bird's-eye document thumbnail / report list.
- Bird's-eye: non-image document series rendered as labeled "<date> <type>" thumbnails
  (`BirdsEyeView.classifyDocSeries` + `ThumbnailListView.addDocumentSeries`), double-click opens
  SR viewer.
- Safety: `ImageSpecimenGlass.calcImageSize2FitComponent` guards `original_width/height < 1`
  (pixel-less objects no longer divide-by-zero).

### Tests (all green)
- `com.vis.reporting.ReportingSrRoundTripTest` (SR build/read/HTML, key-image href, SR-family).
- `com.vis.db.ReportDbIntegrationTest` (REPORT CRUD on real Derby).
- `com.vis.db.SrRoutingDetectionTest` (imported SR detection).
- `com.vis.db.ReportColumnStateTest` (per-study report counts / SR-series detection).

## Build / test / run (on the other PC)
- `mvn compile` — full build (clean).
- `mvn test` — full suite. Expect ~229 tests, 0 failures (one pre-existing flaky DICOM
  network/TLS test may intermittently error — not reporting-related).
- Run the app from `target/classes` needs the OpenCV native lib:
  `java -Djava.library.path=native/native_opencv/linux-x86-64 -cp "target/classes:<m2 cp>" com.vis.core.launcher.Launcher`
  (opencv native is NOT on the default `java.library.path`).
- Sample SR/RDSR files: `sample_reports/sample_text_sr.dcm` (.88.33),
  `sample_reports/sample_rdsr.dcm` (.88.67); regenerate with
  `sample_reports/GenerateSampleReports.java` (compiled outside Maven).

## Manual verification still pending (do on the other PC)
1. Import the sample SR/RDSR → tree shows NO SR series rows; STUDY row Report column shows the
   icon; click → report list popup; "View" opens the SR HTML viewer (RDSR shows dose items).
2. New report → save draft → finalize as SR → view → key-image link retrieval.
3. Mixed study double-click → 2D viewer for images, reports NOT auto-opened.

## Phase 2 backlog
TID 1500 structured measurement SR; SCOORD/SCOORD3D; voice input; JP medical autocomplete
dictionary; DB-backed/user-editable templates; editing round-trip of externally-imported SRs;
optional PNG icons for the Report column.

## Notes
- This branch also carries unrelated pre-existing WIP that was already in the working tree
  (Comparison view: `ComparisonBoard`/`ComparisonScreen`/`SeriesPairing`/`PraparatSyncGroup`/
  `ImageViewerContext`; `com.vis.dicom.seg`; a few test files). They came along with the
  full-working-tree snapshot.
- The plan file `~/.claude/plans/humming-bouncing-castle.md` is local-only (not in git).
