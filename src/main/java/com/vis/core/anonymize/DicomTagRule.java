/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.util.EnumSet;

public class DicomTagRule {
    
    // Table E.1-1a で定義されたアクションキー 
    public enum Action {
        X("Remove"), 
        Z("Zero Length"), 
        D("Dummy"), 
        K("Keep"), 
        C("Clean"), 
        U("Unique UID");

        private String label;
        Action(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private int tag;
    private String name;
    private Action defaultAction;
    private EnumSet<AnonymizeConfig.Option> retainOptions;

    // コンストラクタ
    public DicomTagRule(int tag, String name, Action defaultAction, AnonymizeConfig.Option... options) {
        this.tag = tag;
        this.name = name;
        this.defaultAction = defaultAction;
        this.retainOptions = EnumSet.noneOf(AnonymizeConfig.Option.class);
        for(AnonymizeConfig.Option opt : options) {
            this.retainOptions.add(opt);
        }
    }

    public int getTag() { return tag; }
    public String getName() { return name; }
    public Action getDefaultAction() { return defaultAction; }
    public EnumSet<AnonymizeConfig.Option> getRetainOptions() { return retainOptions; }
}
