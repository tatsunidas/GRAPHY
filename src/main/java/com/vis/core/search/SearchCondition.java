/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

public class SearchCondition {
    private String tagPath;
    @SuppressWarnings("unused")
	private String tagName;
    private String vr;
    private boolean isExclusion;
    
    private ConditionOperator operator;
    private String value1;
    private String value2;

    public SearchCondition(String tagPath, String tagName, String vr, boolean isExclusion) {
        this.tagPath = tagPath;
        this.tagName = tagName;
        this.vr = vr;
        this.isExclusion = isExclusion;
    }

    public String getTagPath() { return tagPath; }
    public String getVr() { return vr; }
    public boolean isExclusion() { return isExclusion; }
    
    public void setExclusion(boolean exclude) { this.isExclusion = exclude; }
    
    public ConditionOperator getOperator() { return operator; }
    public void setOperator(ConditionOperator operator) { this.operator = operator; }
    
    public String getValue1() { return value1; }
    public void setValue1(String value1) { this.value1 = value1; }
    
    public String getValue2() { return value2; }
    public void setValue2(String value2) { this.value2 = value2; }
}
