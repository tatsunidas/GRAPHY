package anonymizer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.vis.dicom.Tag; // プロジェクトの実際のTagクラスに合わせてください
import com.vis.core.anonymize.AnonymizeConfig;
import com.vis.core.anonymize.AnonymizeConfig.Option;
import com.vis.core.anonymize.DicomTagRule;
import com.vis.core.anonymize.DicomTagRule.Action;
import com.vis.core.anonymize.AnonymizeTagDictionary;

public class AnonymizeTagDictionaryTest {

	@Before
	public void setUp() {
		// 辞書クラスが初期化されていることを確認（staticブロックで初期化されている想定）
		assertFalse("TAG_RULES should not be empty", AnonymizeTagDictionary.TAG_RULES.isEmpty());
		assertFalse("RULE_MAP should not be empty", AnonymizeTagDictionary.RULE_MAP.isEmpty());
	}

	@Test
	public void testRetainUIDs() {
		// StudyInstanceUID (0020,000D) が RetainUIDs オプションで K になるか
		verifyTagAction(Tag.StudyInstanceUID, Option.RetainUIDs, Action.K);
		// SeriesInstanceUID (0020,000E) が K になるか
		verifyTagAction(Tag.SeriesInstanceUID, Option.RetainUIDs, Action.K);
	}

	@Test
	public void testRetainDeviceIdentity() {
		// StationName (0008,1010) が RetainDeviceIdentity オプションで K になるか
		verifyTagAction(Tag.StationName, Option.RetainDeviceIdentity, Action.K);
		// DeviceSerialNumber (0018,1000) が K になるか
		verifyTagAction(Tag.DeviceSerialNumber, Option.RetainDeviceIdentity, Action.K);
	}

	@Test
	public void testRetainInstitutionIdentity() {
		// InstitutionName (0008,0080) が RetainInstitutionIdentity オプションで K になるか
		verifyTagAction(Tag.InstitutionName, Option.RetainInstitutionIdentity, Action.K);
	}

	@Test
	public void testRetainPatientCharacteristics() {
		// PatientAge (0010,1010) が RetainPatientCharacteristics オプションで K になるか
		verifyTagAction(Tag.PatientAge, Option.RetainPatientCharacteristics, Action.K);
		// PatientWeight (0010,1030) が K になるか
		verifyTagAction(Tag.PatientWeight, Option.RetainPatientCharacteristics, Action.K);
	}

	@Test
	public void testRetainDatesFull() {
		// StudyDate (0008,0020) が RetainDatesFull オプションで K になるか
		verifyTagAction(Tag.StudyDate, Option.RetainLongitudinalTemporalInformationFullDates, Action.K);
	}

	@Test
	public void testRetainDatesModified() {
		// StudyDate (0008,0020) が RetainDatesModified オプションで C になるか
		// ※ DICOM標準では Modified Dates は "C" (Clean) に分類されます
		verifyTagAction(Tag.StudyDate, Option.RetainLongitudinalTemporalInformationModifiedDates, Action.C);
	}

	@Test
	public void testCleanDescriptors() {
		// StudyDescription (0008,1030) が CleanDescriptors オプションで C になるか
		verifyTagAction(Tag.StudyDescription, Option.CleanDescriptors, Action.C);
		// SeriesDescription (0008,103E) が C になるか
		verifyTagAction(Tag.SeriesDescription, Option.CleanDescriptors, Action.C);
	}

	@Test
	public void testCleanStructuredContent() {
		// ImageComments (0020,4000) が CleanStructuredContent オプションで C または X になるか
		// ※ 辞書の実装によって C か X か異なる可能性があるため、どちらでも許容するか、仕様に合わせて変更してください
		DicomTagRule rule = AnonymizeTagDictionary.RULE_MAP.get(Tag.ImageComments);
		assertNotNull("Tag ImageComments should be in dictionary", rule);

		AnonymizeConfig config = new AnonymizeConfig();
		config.addOption(Option.CleanStructuredContent);
		Action action = config.determineFinalAction(rule);

		assertTrue("Action should be C or X for ImageComments", action == Action.C || action == Action.X);
	}

	// ==========================================
	// ヘルパーメソッド
	// ==========================================

	/**
	 * 指定したタグに対して特定のオプションをONにした際、期待するアクションが返るか検証する
	 */
	private void verifyTagAction(int tag, Option option, Action expectedAction) {
		DicomTagRule rule = AnonymizeTagDictionary.RULE_MAP.get(tag);
		assertNotNull(String.format("Tag %08X should be registered in the dictionary", tag), rule);

		AnonymizeConfig config = new AnonymizeConfig();
		config.addOption(option);
		Action actualAction = config.determineFinalAction(rule);

		String tagHex = String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF);
		assertEquals("Tag " + tagHex + " with option " + option + " should result in action " + expectedAction,
				expectedAction, actualAction);
	}
}
