/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.util.HashMap;
import java.util.Map;

/**
 * Tagごとの振る舞いを定義するためのクラス
 */
public class DicomTagRule {

	// Table E.1-1a で定義されたアクションキー
	public enum Action {
		X("Remove"), Z("Zero Length"), D("Dummy"), K("Keep"), C("Clean"), U("Unique UID");

		private String label;

		Action(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	private int tag;
	private String name;
	private Action defaultAction;
	// ★ 変更: オプションが有効な場合の「上書きアクション」を保持するMap
	private Map<AnonymizeConfig.Option, Action> optionActions;

	// コンストラクタ
	public DicomTagRule(int tag, String name, Action defaultAction, AnonymizeConfig.Option... options) {
		this.tag = tag;
		this.name = name;
		this.defaultAction = defaultAction;
		this.optionActions = new HashMap<>();
	}
	
	public void addOptionAction(AnonymizeConfig.Option opt, Action act) {
        this.optionActions.put(opt, act);
    }
	
	public Map<AnonymizeConfig.Option, Action> getOptionActions() {
        return optionActions;
    }

	public int getTag() {
		return tag;
	}

	public String getName() {
		return name;
	}

	public Action getDefaultAction() {
		return defaultAction;
	}

}
