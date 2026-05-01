/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnonymizeConfig {

    public enum Option {
        CleanPixelData, // 113101
        CleanRecognizableVisualFeatures, // 113102
        CleanGraphics,// 113103
        CleanStructuredContent,// 113104
        CleanDescriptors,// 113105
    	RetainLongitudinalTemporalInformationFullDates,// 113106
        RetainLongitudinalTemporalInformationModifiedDates,// 113107
        RetainPatientCharacteristics,// 113108
        RetainDeviceIdentity,// 113109
        RetainUIDs,// 113110
        RetainSafePrivate,// 113111
        RetainInstitutionIdentity,// 113112
    }

    private EnumSet<Option> options = EnumSet.noneOf(Option.class);
    
    private String replacePatientName = "de-identified";
    private String replacePatientId = "de-identified";
    
    private long randomSeed;
    
    public long getRandomSeed() {
		return randomSeed;
	}
	public void setRandomSeed(Long randomSeed) {
		this.randomSeed = randomSeed;
	}

	// ダイアログ上でユーザーが個別に「Retain」したタグのリスト
    private Set<Integer> manualRetainTags = new HashSet<>();
    // ダイアログ上でユーザーが手動で設定した「ダミー置換値」
    private Map<Integer, String> customTagReplacements = new HashMap<>();

    public EnumSet<Option> getOptions() { return options; }
    public void addOption(Option opt) { options.add(opt); }
    public boolean hasOption(Option opt) { return options.contains(opt); }

    public Set<Integer> getManualRetainTags() { return manualRetainTags; }
    public Map<Integer, String> getCustomTagReplacements() { return customTagReplacements; }

    public String getReplacePatientName() { return replacePatientName; }
    public void setReplacePatientName(String name) { this.replacePatientName = name; }

    public String getReplacePatientId() { return replacePatientId; }
    public void setReplacePatientId(String id) { this.replacePatientId = id; }

    /**
     * マスターオプションによって自動的にRetainされるタグかどうかを判定する
     */
    public boolean isAutoRetain(DicomTagRule rule) {
        for (Option opt : rule.getRetainOptions()) {
            if (options.contains(opt)) return true;
        }
        return false;
    }

    /**
     * 最終的にこのタグをRetainするかどうか（自動Retain + 手動Retain）
     */
    public boolean isRetain(DicomTagRule rule) {
        return isAutoRetain(rule) || manualRetainTags.contains(rule.getTag());
    }
}