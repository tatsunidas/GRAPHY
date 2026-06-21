/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import com.vis.configuration.Resources;
import com.vis.core.anonymize.AnonymizeConfig.Option;
import com.vis.core.anonymize.DicomTagRule.Action;
import com.vis.core.log.Log;

/*
 * import pandas as pd
 * url = "https://dicom.nema.org/medical/dicom/current/output/chtml/part15/chapter_E.html"
 * # すべてのテーブルを取得
 * tables = pd.read_html(url)
 * # Table E.1-1 は通常 1番目または2番目にある（環境により変動）
 * for i, table in enumerate(tables):
 * 		if "Attribute Name" in table.columns:
 * 			dicom_table = table
 * 			break
 * # CSV保存
 * dicom_table.to_csv("Table_E1_1_Application_Level_Confidentiality.csv", index=False)
 * print("CSV saved!")
 * 
 * この方法で他のテーブルも取得。1つ目のカラム名はテーブルに合わせて。
 * 
 */
public class AnonymizeTagDictionary {

	/*
	 * 順序を保証するためのタグリスト
	 */
	public static final List<DicomTagRule> TAG_RULES = new ArrayList<>();
	/*
	 * 検証用のMAP
	 */
	public static final Map<Integer, DicomTagRule> RULE_MAP = new HashMap<>();
	
	// SR Clean Content用辞書: Key = "CodingScheme:CodeValue" (例: "DCM:121008")
    public static final Set<String> SR_CLEAN_CODES = new HashSet<>();
    
    // Safe Private Attributes用辞書: Key = Private Creator (例: "Philips PET Private Group")
    // Value = 安全なタグの下位シグネチャのSet (Group << 16 | Elementの下位1バイト)
    public static final Map<String, Set<Integer>> SAFE_PRIVATE_ATTRIBUTES = new HashMap<>();

	static {
		loadRulesFromCsv();
		loadSrCleanCodesFromCsv();
        loadSafePrivateAttributesFromCsv();
	}

	private static void loadRulesFromCsv() {
		try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(Resources.PS3_15_TableE1_1.path())) {
			if (is == null) {
				Log.logger.severe("AnonymizeTagDictionary: CSV file not found: " + Resources.PS3_15_TableE1_1.toString());
				return;
			}

			try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line;
				boolean isHeader = true;
				while ((line = br.readLine()) != null) {
					if (isHeader) {
						isHeader = false;
						continue;
					}

					// 簡易CSVパース（カンマで分割。引用符内のカンマは考慮しない簡易版）
					// 本格的な場合は Apache Commons CSV 等の使用を推奨
					String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
					if (cols.length < 15)
						continue;

					String attrName = cols[0].replace("\"", "");
					// ここではまだカンマを消さずにカッコだけ外す: "(50xx,xxxx)" -> "50xx,xxxx"
					String originalTagStr = cols[1].replace("\"", "").replace("(", "").replace(")", "");
					String basicProf = cols[4].replace("\"", "");

					// 1. 基本アクションの決定（安全側に倒す）
					Action defaultAction = mapAction(basicProf);
					
					// プライベートタグ (gggg,eeee) の場合
					/*
					 * Table E 1-1には、プライベートタグをまとめて(gggg,eeee)と略記している。
					 * 実行エンジン側で「奇数グループは削除/保持する」という別ロジックで対応するため、
					 * ここではUIのリストには追加せずスキップする
					 */
					if (originalTagStr.contains("gggg") && originalTagStr.contains("eeee")) {
					    continue; 
					}
					
					// カンマで Group と Element に分割する
					String[] parts = originalTagStr.split(",");
					if (parts.length != 2) {
					    continue; // 想定外のフォーマットはスキップ
					}
					
					String groupStr = parts[0].trim(); // 例: "50xx", "0008", "60xx"
					String elemStr = parts[1].trim();  // 例: "xxxx", "3000", "0050"
					
					// 2. Element が "xxxx" の場合 (グループ全体を表す)
					// UI表示と設定保持の代表値として "0000" (Group Lengthの位置) を割り当てる
					// Curve Data (50xx,xxxx) エレメント
					if (elemStr.equals("xxxx")) {
					    elemStr = "0000"; 
					}
					
					// 3. リピーティンググループ (xx) の展開
					// 50xx, 60xx などは、00, 02, 04, ..., 1E の16個の偶数グループに展開してすべてリストに登録します。
					// 3. Group がリピーティンググループ (xx) の場合
					if (groupStr.contains("xx")) {
					    String baseGroupStr = groupStr.substring(0, 2); // "50" や "60"
					    
					    // 0x00 から 0x1E まで、2ずつ増加させて展開
					    for (int i = 0; i <= 0x1E; i += 2) {
					        String expandedTagStr = String.format("%s%02X%s", baseGroupStr, i, elemStr);
					        int tag = (int) Long.parseLong(expandedTagStr, 16);
					        
					        DicomTagRule rule = new DicomTagRule(tag, attrName + " (Group " + String.format("%02X", i) + ")", defaultAction);
					        applyOptions(rule, cols);
					        TAG_RULES.add(rule);
					        RULE_MAP.put(tag, rule); // ★追加
					    }
					} else {
					    // 通常のタグ
					    String expandedTagStr = groupStr + elemStr;
					    int tag = (int) Long.parseLong(expandedTagStr, 16);
					    
					    DicomTagRule rule = new DicomTagRule(tag, attrName, defaultAction);
					    applyOptions(rule, cols);
					    TAG_RULES.add(rule);
					    RULE_MAP.put(tag, rule); // ★追加
					}
				}
			}
		} catch (Exception e) {
			Log.logger.severe("AnonymizeTagDictionary: Failed to load anonymization rules from CSV: " + e.getMessage());
		}
	}

	private static void loadSrCleanCodesFromCsv() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(Resources.PS3_15_TableE3_4_1.path())) {
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                while ((line = br.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (cols.length < 4) continue;
                    
                    // Col 1: Code Value, Col 2: Coding Scheme Designator
                    String codeValue = cols[1].replace("\"", "").trim();
                    String codingScheme = cols[2].replace("\"", "").trim();
                    
                    if (!codeValue.isEmpty() && !codingScheme.isEmpty()) {
                        SR_CLEAN_CODES.add(codingScheme + ":" + codeValue);
                    }
                }
            }
        } catch (Exception e) {
            Log.logger.severe("AnonymizeTagDictionary: Failed to load SR clean codes from CSV: " + e.getMessage());
        }
    }

    private static void loadSafePrivateAttributesFromCsv() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(Resources.PS3_15_TableE3_10_1.path())) {
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                while ((line = br.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (cols.length < 2) continue;
                    
                    // Col 0: Data Element (e.g., "(7053,xx00)"), Col 1: Private Creator
                    String dataElementStr = cols[0].replace("\"", "").replace("(", "").replace(")", "").trim();
                    String privateCreator = cols[1].replace("\"", "").trim();
                    
                    if (dataElementStr.contains(",")) {
                        String[] parts = dataElementStr.split(",");
                        String groupStr = parts[0].trim();
                        String elemStr = parts[1].trim().replace("xx", "00"); // "xx00" -> "0000"
                        
                        try {
                            int group = Integer.parseInt(groupStr, 16);
                            int elemLower = Integer.parseInt(elemStr, 16);
                            // シグネチャ生成: (グループ番号 << 16) | エレメントの下位1バイト
                            int signature = (group << 16) | elemLower;
                            
                            SAFE_PRIVATE_ATTRIBUTES.computeIfAbsent(privateCreator, k -> new HashSet<>()).add(signature);
                        } catch (NumberFormatException e) {
                            // パースエラーはスキップ
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.logger.severe("AnonymizeTagDictionary: Failed to load safe private attributes from CSV: " + e.getMessage());
        }
    }

    private static void applyOptions(DicomTagRule rule, String[] cols) {
	    applyOptionIfPresent(rule, cols[5], Option.RetainSafePrivate);
	    applyOptionIfPresent(rule, cols[6], Option.RetainUIDs);
	    applyOptionIfPresent(rule, cols[7], Option.RetainDeviceIdentity);
	    applyOptionIfPresent(rule, cols[8], Option.RetainInstitutionIdentity);
	    applyOptionIfPresent(rule, cols[9], Option.RetainPatientCharacteristics);
	    applyOptionIfPresent(rule, cols[10], Option.RetainLongitudinalTemporalInformationFullDates);
	    applyOptionIfPresent(rule, cols[11], Option.RetainLongitudinalTemporalInformationModifiedDates);
	    applyOptionIfPresent(rule, cols[12], Option.CleanDescriptors);
	    applyOptionIfPresent(rule, cols[13], Option.CleanStructuredContent);
	    applyOptionIfPresent(rule, cols[14], Option.CleanGraphics);
	}
    
    private static void applyOptionIfPresent(DicomTagRule rule, String colValue, Option option) {
        String val = colValue.replace("\"", "").trim();
        if (!val.isEmpty()) {
            Action act = mapAction(val); // 既存の安全なマッピング処理を再利用
            if (act != null) {
                rule.addOptionAction(option, act);
            }
        }
    }
    
    private static Action mapAction(String rawAction) {
        String act = rawAction.replace("\"", "").trim();
        
        // 安全側に倒すロジック
        if (act.contains("U*") || act.equals("U")) return Action.U;
        if (act.contains("D")) return Action.D; // Z/D, X/D, X/Z/D -> D
        if (act.contains("Z")) return Action.Z; // X/Z -> Z
        if (act.equals("X")) return Action.X;
        if (act.equals("K")) return Action.K;
        if (act.equals("C")) return Action.C;
        
        return Action.X; // デフォルトは削除
    }
}
