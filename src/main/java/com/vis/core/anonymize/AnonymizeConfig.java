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
    	//clean opt
        CleanPixelData, // 113101
        CleanRecognizableVisualFeatures, // 113102
        CleanGraphics,// 113103
        CleanStructuredContent,// 113104
        CleanDescriptors,// 113105
        //retain opt
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
    
    private Long randomSeed;
    
    public Long getRandomSeed() {
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
     * 手動オーバーライド(Manual Retain, Custom Dummy)を【除外】した、
     * オプションとデフォルト設定のみによる本来のアクションを計算します。
     * （AdvancedSettingsDialog でのロック状態やベースアクションの表示に使用）
     */
    public DicomTagRule.Action getActionByOptionsAndDefault(DicomTagRule rule) {
        DicomTagRule.Action targetAction = null;

        // ONになっているオプションの中で、ルールに定義されたアクションを探す
        for (Option opt : options) {
            if (rule.getOptionActions().containsKey(opt)) {
                DicomTagRule.Action actFromOpt = rule.getOptionActions().get(opt);
                // 加工や削除（C, X）は保持（K）よりも優先する安全ロジック
                if (targetAction == null || actFromOpt == DicomTagRule.Action.C || actFromOpt == DicomTagRule.Action.X) {
                    targetAction = actFromOpt;
                }
            }
        }

        if (targetAction != null) {
            return targetAction;
        }
        return rule.getDefaultAction();
    }

    /**
     * エンジンが最終的に実行するアクションを決定する（完全版）
     */
    public DicomTagRule.Action determineFinalAction(DicomTagRule rule) {
        // 1. UIからの手動保持 (Manual Retain) が最優先
        if (manualRetainTags.contains(rule.getTag())) {
            return DicomTagRule.Action.K;
        }

        // 2. UIからのカスタムダミー値が設定されていれば 'D' (Dummy)
        if (customTagReplacements.containsKey(rule.getTag())) {
            return DicomTagRule.Action.D;
        }

        // 3. 上記のオーバーライドがなければ、オプションとデフォルトによるアクション
        return getActionByOptionsAndDefault(rule);
    }
}