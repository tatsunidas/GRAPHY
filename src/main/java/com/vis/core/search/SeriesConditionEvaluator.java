/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import com.vis.dicom.DicomObject;
import java.util.List;

public class SeriesConditionEvaluator {

    /**
     * DICOMヘッダが指定されたすべての条件（選択・除外）を満たすか評価します。
     */
    public static boolean evaluate(DicomObject header, List<SearchCondition> conditions) {
        if (header == null || conditions == null || conditions.isEmpty()) {
            return false;
        }

        // 1. まず「除外基準 (Exclusion)」をチェック
        // いずれか1つでも当てはまれば、即座に false (除外) を返す
        for (SearchCondition cond : conditions) {
            if (cond.isExclusion()) {
                if (matchCondition(header, cond)) {
                    return false; // 除外条件にヒットしたためアウト
                }
            }
        }

        // 2. 次に「選択基準 (Inclusion)」をチェック
        // すべて当てはまらなければならない (AND条件)
        for (SearchCondition cond : conditions) {
            if (!cond.isExclusion()) {
                if (!matchCondition(header, cond)) {
                    return false; // 選択条件を1つでも満たさなければアウト
                }
            }
        }

        return true; // 除外されず、すべての選択条件をクリアした
    }

    /**
     * 単一の条件とDICOMヘッダを照合します。
     */
    private static boolean matchCondition(DicomObject header, SearchCondition cond) {
        String tagValue = extractValueFromHeader(header, cond.getTagPath());
        
        // 値が存在しない場合は判定不能として false とする（要件により調整可能）
        if (tagValue == null || tagValue.trim().isEmpty()) {
            return false;
        }

        tagValue = tagValue.trim();
        String vr = cond.getVr();

        // VRごとの判定へ分岐
        if (isDateOrTime(vr)) {
            return compareStringLexicographically(tagValue, cond); // DICOM日付(YYYYMMDD)は文字列の辞書順比較で判定可能
        } else if (isNumeric(vr)) {
            return compareNumeric(tagValue, cond);
        } else {
            return compareString(tagValue, cond);
        }
    }

    // --- 値の抽出ロジック ---
    private static String extractValueFromHeader(DicomObject header, String tagPath) {
        try {
            String[] pathParts = tagPath.split(" > ");
            DicomObject currentObj = header;

            for (int j = 0; j < pathParts.length; j++) {
                String hexTag = pathParts[j].substring(0, 9).replace(",", "");
                int tagInt = Integer.parseUnsignedInt(hexTag, 16);

                if (j == pathParts.length - 1) {
                    String[] vals = currentObj.getStrings(tagInt);
                    
                    if (vals != null && vals.length > 0) {
                        // ★ 変更点: 複数値が存在する場合は null を返し、条件判定をスキップ(無視)する
                        if (vals.length > 1) {
                            return null; 
                        }
                        return vals[0]; // 1つだけの場合はその値を返す
                    } else {
                        return currentObj.getString(tagInt);
                    }
                } else {
                    currentObj = currentObj.getNestedDataset(tagInt);
                    if (currentObj == null) break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    // --- 比較ロジック ---

    // 文字列比較 (部分一致のOR検索、完全一致)
    private static boolean compareString(String targetValue, SearchCondition cond) {
        String query = cond.getValue1() != null ? cond.getValue1().trim() : "";
        
        if (cond.getOperator() == ConditionOperator.CONTAINS) {
            // カンマ区切りで複数キーワードを判定 (いずれかに部分一致で true)
            String[] keywords = query.split(",");
            for (String kw : keywords) {
                if (targetValue.toLowerCase().contains(kw.trim().toLowerCase())) {
                    return true;
                }
            }
            return false;
        } else if (cond.getOperator() == ConditionOperator.EQUALS) {
            return targetValue.equalsIgnoreCase(query);
        }
        return false;
    }

    // 数値比較
    /**
     * 単一の数値を持つタグが対象。
     * 複数の値を持つタグは対象としない。（無視する）
     * 
     * @param targetValue
     * @param cond
     * @return
     */
    // --- 数値比較ロジック ---
    private static boolean compareNumeric(String targetValue, SearchCondition cond) {
        try {
            // ★ 変更点: 複数値を無視する仕様になったため、splitによる分割処理を削除しシンプル化
            double targetNum = Double.parseDouble(targetValue.trim());
            double v1 = Double.parseDouble(cond.getValue1().trim());

            switch (cond.getOperator()) {
                case EQUALS:
                    return targetNum == v1;
                case GREATER_THAN_OR_EQUAL:
                    return targetNum >= v1;
                case LESS_THAN_OR_EQUAL:
                    return targetNum <= v1;
                case RANGE:
                    double v2 = Double.parseDouble(cond.getValue2().trim());
                    return targetNum >= v1 && targetNum <= v2;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false; // 数値変換エラー時は false
        }
    }

    // 日付・時刻比較 (DICOM形式は YYYYMMDD のため辞書式比較で大小が判定できる)
    private static boolean compareStringLexicographically(String targetValue, SearchCondition cond) {
        String v1 = cond.getValue1() != null ? cond.getValue1().trim() : "";
        
        switch (cond.getOperator()) {
            case EQUALS:
                return targetValue.equals(v1);
            case GREATER_THAN_OR_EQUAL:
                return targetValue.compareTo(v1) >= 0;
            case LESS_THAN_OR_EQUAL:
                return targetValue.compareTo(v1) <= 0;
            case RANGE:
                String v2 = cond.getValue2() != null ? cond.getValue2().trim() : "";
                return targetValue.compareTo(v1) >= 0 && targetValue.compareTo(v2) <= 0;
            default:
                return false;
        }
    }

    // --- VRヘルパー ---
    private static boolean isDateOrTime(String vr) {
        return vr.equals("DA") || vr.equals("DT") || vr.equals("TM");
    }

    private static boolean isNumeric(String vr) {
        return vr.equals("DS") || vr.equals("IS") || vr.equals("FL") || vr.equals("FD") || 
               vr.equals("SL") || vr.equals("SS") || vr.equals("UL") || vr.equals("US");
    }
}