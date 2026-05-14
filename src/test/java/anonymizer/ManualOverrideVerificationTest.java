package anonymizer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.vis.core.anonymize.AnonymizeConfig;
import com.vis.core.anonymize.DicomTagRule;
import com.vis.core.anonymize.DicomTagRule.Action;
import com.vis.core.anonymize.AnonymizeTagDictionary;
import com.vis.dicom.Tag; // お使いのプロジェクトのTagクラスに合わせてください

public class ManualOverrideVerificationTest {

	private AnonymizeConfig config;
	private DicomTagRule testRule; // 検証に使う一般的なタグ（例: Accession Number）

	@Before
	public void setUp() {
		// オプションを一切持たないまっさらなConfigを用意
		config = new AnonymizeConfig();

		// テスト用のタグとして Accession Number (0008,0050) を取得
		// ※ DICOMの基本プロファイルでは、通常 Z (Zero Length) または D になります
		testRule = AnonymizeTagDictionary.RULE_MAP.get(Tag.AccessionNumber);
		assertNotNull("Test rule should exist in dictionary", testRule);
	}

	@Test
	public void testDefaultActionWhenNoOverrides() {
		// 何もオーバーライドしていない場合は、デフォルトアクションが返るはず
		Action action = config.determineFinalAction(testRule);
		assertEquals("Should return default action when no options or manual overrides exist",
				testRule.getDefaultAction(), action);
	}

	@Test
	public void testManualRetainOverridesDefault() {
		// 1. 手動で Retain を強制する（AdvancedSettingsDialogでチェックを入れた状態を再現）
		config.getManualRetainTags().add(testRule.getTag());

		Action action = config.determineFinalAction(testRule);

		// 結果は K (Keep) になるはず
		assertEquals("Manual retain should override to Action.K", Action.K, action);
	}

	@Test
	public void testCustomValueOverridesDefault() {
		// 2. カスタムダミー値を設定する（Retainチェックなしで値を入力した状態を再現）
		config.getCustomTagReplacements().put(testRule.getTag(), "TEST_DUMMY_VALUE");

		Action action = config.determineFinalAction(testRule);

		// 結果は D (Dummy) になるはず
		assertEquals("Custom value replacement should override to Action.D", Action.D, action);
	}

	@Test
	public void testManualRetainWinsOverCustomValue() {
		// 3. 競合テスト：もし手動Retainとカスタム値の両方が設定された場合
		config.getManualRetainTags().add(testRule.getTag());
		config.getCustomTagReplacements().put(testRule.getTag(), "TEST_DUMMY_VALUE");

		Action action = config.determineFinalAction(testRule);

		// Retain (K) の方が優先度が高いため、Kになるはず
		assertEquals("Manual retain should have higher priority than custom value", Action.K, action);
	}

	@Test
	public void testPatientInformationSpecialRules() {
		// 4. 患者情報（PatientName, PatientID）のトップレベル割り込みルールの検証

		DicomTagRule patIdRule = AnonymizeTagDictionary.RULE_MAP.get(Tag.PatientID);
		DicomTagRule patNameRule = AnonymizeTagDictionary.RULE_MAP.get(Tag.PatientName);

		// 【PatientID】
		// 常に D になるはず
		Action patIdAction = config.determineFinalAction(patIdRule);
		assertEquals("PatientID should ALWAYS be Action.D", Action.D, patIdAction);

		// 【PatientName - デフォルト(空)】
		// テキストボックスが空文字の場合、Z になるはず
		config.setReplacePatientName("");
		Action patNameActionEmpty = config.determineFinalAction(patNameRule);
		assertEquals("PatientName should be Action.Z when replacement string is empty", Action.Z, patNameActionEmpty);

		// 【PatientName - 入力あり】
		// テキストボックスに文字が入力されている場合、D になるはず
		config.setReplacePatientName("John Doe");
		Action patNameActionFilled = config.determineFinalAction(patNameRule);
		assertEquals("PatientName should be Action.D when replacement string is provided", Action.D,
				patNameActionFilled);
	}
}