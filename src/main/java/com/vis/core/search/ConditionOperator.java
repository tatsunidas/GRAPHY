/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

public enum ConditionOperator {
    EQUALS("=="),
    CONTAINS("Contains"), // 部分一致 (カンマ区切りで複数キーワードのOR)
    GREATER_THAN_OR_EQUAL(">="), // 以降、以上
    LESS_THAN_OR_EQUAL("<="),    // 以前、以下
    RANGE("Range");              // 範囲指定 (A 〜 B)

    private final String label;

    ConditionOperator(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}