/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.image.DicomImage;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConditionVerifier {

    public static class VerificationResult {
        public int totalEvaluatedSeries = 0;
        public int matchedStudies = 0;
        public int matchedSeries = 0;
        public String summaryText = "";
        public String treeText = "";
        public List<File> validTargetFiles = new ArrayList<>();
    }

    public static VerificationResult verify(
    		List<File> representativeFiles, List<SearchCondition> conditions, 
            List<String> allowedPlanes, DICOMBackend backend, Consumer<Integer> progressCallback) {
    	
        VerificationResult result = new VerificationResult();
        result.totalEvaluatedSeries = representativeFiles.size();

        Map<String, List<File>> studyGroup = new LinkedHashMap<>();
        Map<String, String> studyDisplayNames = new LinkedHashMap<>();

        int count = 0;
        
        for (File f : representativeFiles) {
            count++;
            if (progressCallback != null) {
                progressCallback.accept(count);
            }

            try {
                DicomImage dcm = DicomImage.newDicomImage(f.getCanonicalPath(), false, backend);
                // ★ backendの実装によってはnullが返るため、ヘッダ未取得時はこのファイルをスキップする
                DicomObject header = (dcm != null) ? dcm.getHeader() : null;
                if (header == null) {
                    Log.logger.warning("ConditionVerifier: Failed to read DICOM header, skipping: " + f.getAbsolutePath());
                    continue;
                }

                if (SeriesConditionEvaluator.evaluate(header, conditions, allowedPlanes)) {
                    result.validTargetFiles.add(f);

                    String studyUid = header.getString(0x0020000D);
                    if (studyUid == null) studyUid = "UNKNOWN_STUDY";

                    studyGroup.computeIfAbsent(studyUid, k -> new ArrayList<>()).add(f);

                    if (!studyDisplayNames.containsKey(studyUid)) {
                        String patId = header.getString(0x00100020);
                        String studyDate = header.getString(0x00080020);
                        String studyDesc = header.getString(0x00081030);

                        String parentName = String.format("[%s] Date: %s - %s", 
                                patId != null ? patId : "NoPatient",
                                studyDate != null ? studyDate : "NoDate",
                                studyDesc != null ? studyDesc : "NoDescription");
                        studyDisplayNames.put(studyUid, parentName);
                    }
                }
            } catch (Exception e) {
                Log.logger.warning("ConditionVerifier: Failed to evaluate series file: " + f.getAbsolutePath() + " (" + e.getMessage() + ")");
            }
        }

        result.matchedStudies = studyGroup.size();
        result.matchedSeries = result.validTargetFiles.size();

        result.summaryText = String.format(
                "--- Verification Summary ---\nEvaluated Total Series: %d\nMatched Studies: %d\nMatched Series: %d\n----------------------------",
                result.totalEvaluatedSeries, result.matchedStudies, result.matchedSeries);

        StringBuilder tree = new StringBuilder("Target Extraction Directory\n");

        for (Map.Entry<String, List<File>> entry : studyGroup.entrySet()) {
            String studyUid = entry.getKey();
            tree.append(" |- ").append(studyDisplayNames.get(studyUid)).append("\n");

            for (File f : entry.getValue()) {
                try {
                    DicomImage dcm = DicomImage.newDicomImage(f.getCanonicalPath(), false, backend);
                    String expectedFolderName = generateUniqueFolderName((dcm != null) ? dcm.getHeader() : null);
                    tree.append(" |   |- ").append(expectedFolderName).append("\n");
                } catch (Exception e) {
                    tree.append(" |   |- ERROR_READING_FILE\n");
                }
            }
        }

        result.treeText = tree.toString();
        return result;
    }

    /**
     * DICOMヘッダから安全でユニークなフォルダ名を生成します
     */
    public static String generateUniqueFolderName(DicomObject header) {
        // ★ ヘッダを読み込めなかったファイル向けのフォールバック（NPE回避）
        if (header == null) {
            return "NoPatient_NoDate_NoProtocol_0000";
        }
        String patId = header.getString(0x00100020);
        String studyDate = header.getString(0x00080020);
        String protocolName = header.getString(0x00181030);
        
        if (protocolName == null || protocolName.isEmpty()) {
            protocolName = header.getString(0x0008103E); // Series Description
        }

        String seriesUid = header.getString(0x0020000E);
        String uidSuffix = (seriesUid != null && seriesUid.length() > 4) 
                ? seriesUid.substring(seriesUid.length() - 4) : "0000";

        // ★修正: "N/A" のスラッシュがOSのフォルダ区切りと誤認されないように "NoXXX" に変更
        String safePatId = (patId != null && !patId.trim().isEmpty()) ? patId.trim() : "NoPatient";
        String safeDate = (studyDate != null && !studyDate.trim().isEmpty()) ? studyDate.trim() : "NoDate";
        String safeProtocol = (protocolName != null && !protocolName.trim().isEmpty()) ? protocolName.trim() : "NoProtocol";

        // 一旦結合
        String rawFolderName = String.format("%s_%s_%s_%s", safePatId, safeDate, safeProtocol, uidSuffix);

        // ★修正: 念のため、結合後の文字列全体に対してOSの禁止文字をすべてアンダースコアに置換する
        return rawFolderName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
