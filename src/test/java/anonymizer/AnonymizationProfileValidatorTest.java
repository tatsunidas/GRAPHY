package anonymizer;

import org.dcm4che3.data.Attributes;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

// 実際のパッケージ・クラス名に合わせて調整してください
import com.vis.dicom.dcm4cheImpl.DicomObjectChe;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.core.anonymize.AnonymizeConfig;
import com.vis.core.anonymize.AnonymizeConfig.Option;
import com.vis.core.anonymize.DicomTagRule;
import com.vis.core.anonymize.DicomTagRule.Action;
import com.vis.core.anonymize.AnonymizeTagDictionary;
import com.vis.core.anonymize.DicomAnonymizerEngine;
import com.vis.core.anonymize.DicomAnonymizerEngine.PatientMapping;

import java.util.HashMap;
import java.util.Map;


@Ignore("Depends on MainScreen / live anonymization config; skipped in the headless pre-release suite.")
public class AnonymizationProfileValidatorTest {

    private DicomObject originalDcm;
    private DicomAnonymizerEngine engine;

    @Before
    public void setUp() throws Exception {
        engine = new DicomAnonymizerEngine();
        
        // ==========================================
        // ★ サンプルDICOM画像を読み込む
        // ==========================================
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
        reader.read("/home/tatsunidas/graphy_sample_images/cDWI-Sample/B1000/1-05.dcm", true);
        originalDcm = reader.getHeader();
    }

    // ==========================================
    // オプション別の検証テストメソッド群
    // ==========================================

    @Test
    public void testRetainUIDs() {
        runOptionTest(Option.RetainUIDs);
    }

    @Test
    public void testRetainDeviceIdentity() {
        runOptionTest(Option.RetainDeviceIdentity);
    }

    @Test
    public void testRetainInstitutionIdentity() {
        runOptionTest(Option.RetainInstitutionIdentity);
    }

    @Test
    public void testRetainPatientCharacteristics() {
        runOptionTest(Option.RetainPatientCharacteristics);
    }

    @Test
    public void testRetainLongitudinalTemporalInformationFullDates() {
        runOptionTest(Option.RetainLongitudinalTemporalInformationFullDates);
    }

    @Test
    public void testRetainLongitudinalTemporalInformationModifiedDates() {
        runOptionTest(Option.RetainLongitudinalTemporalInformationModifiedDates);
    }

    @Test
    public void testRetainSafePrivate() {
        runOptionTest(Option.RetainSafePrivate);
    }

    @Test
    public void testCleanDescriptors() {
        runOptionTest(Option.CleanDescriptors);
    }

    @Test
    public void testCleanStructuredContent() {
        runOptionTest(Option.CleanStructuredContent);
    }

    @Test
    public void testCleanGraphics() {
        runOptionTest(Option.CleanGraphics);
    }

    // ==========================================
    // ヘルパー＆コア検証ロジック
    // ==========================================

    /**
     * オプションのOFF状態とON状態をそれぞれ実行し、結果を検証する
     */
    private void runOptionTest(Option optionToTest) {
        // deidentify は渡された DicomObject を直接書き換えるため、
        // 比較元の originalDcm を守るためにコピー（複製）を作成します。
        
        // 【シナリオA: オプション OFF (デフォルト)】
        AnonymizeConfig configOff = new AnonymizeConfig();
        DicomObject targetOff = cloneDataset(originalDcm); 
        /*
         * 患者名はブランクにする
         */
        PatientMapping pMap = createTestPatientMapping("", "TEST_ID1");
        Map<String, String> globalUidMapOff = new HashMap<>();
        
        // ★ 実際の deidentify メソッドを実行
        engine.deidentify(targetOff, configOff, pMap, globalUidMapOff);
        verifyAllTags(originalDcm, targetOff, configOff);

        // 【シナリオB: オプション ON】
        AnonymizeConfig configOn = new AnonymizeConfig();
        configOn.addOption(optionToTest);
        DicomObject targetOn = cloneDataset(originalDcm);
        Map<String, String> globalUidMapOn = new HashMap<>();
        pMap = createTestPatientMapping("", "TEST_ID2");
        // ★ 実際の deidentify メソッドを実行
        engine.deidentify(targetOn, configOn, pMap, globalUidMapOn);
        verifyAllTags(originalDcm, targetOn, configOn);
    }

    private void verifyAllTags(DicomObject orig, DicomObject anon, AnonymizeConfig config) {
        List<DicomTagRule> rules = AnonymizeTagDictionary.TAG_RULES; 
        
        for (DicomTagRule rule : rules) {
            int tag = rule.getTag();
            String origValue = orig.getString(tag);
            
            // サンプルDICOMにそもそも含まれていないタグは検証をスキップ
            if (origValue == null || origValue.isEmpty()) {
                continue; 
            }

            String anonValue = anon.getString(tag);
            
            // ★ 新設計：Configに最終アクションを問い合わせる
            Action expectedAction = config.determineFinalAction(rule);

            String tagHex = String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF);
            String assertMsg = "Tag " + tagHex + " [" + rule.getName() + "] (Action: " + expectedAction + ") -> ";

            switch (expectedAction) {
                case K: // Keep
                    assertEquals(assertMsg + "Should be Kept.", origValue, anonValue);
                    break;
                case X: // Remove
                    assertNull(assertMsg + "Should be Removed.", anonValue);
                    break;
                case Z: // Zero Length
                    if (anonValue != null) {
                        // 文字列の前後の空白やDICOM特有の終端文字（Null文字）を取り除く
                        String cleanVal = anonValue.trim().replace("\0", "");
                        
                        // JUnitの機能を使って、「期待値(空)」と「実際の値(cleanVal)」を表示させる
                        assertEquals(assertMsg + "Should be empty.", "", cleanVal);
                    }
                    break;
                case U: // Unique UID
                    assertNotNull(assertMsg + "UID must not be null.", anonValue);
                    assertNotEquals(assertMsg + "UID should be replaced.", origValue, anonValue);
                    break;
                case D: // Dummy
                    assertNotNull(assertMsg + "Should have a dummy value.", anonValue);
                    assertNotEquals(assertMsg + "Should be replaced.", origValue, anonValue);
                    assertFalse(assertMsg + "Should not be empty.", anonValue.isEmpty());
                    break;
                case C: // Clean
                    assertNotEquals(assertMsg + "Should be Cleaned.", origValue, anonValue);
                    break;
                default:
                    fail("Unknown action code: " + expectedAction);
            }
        }
    }

    private DicomObject cloneDataset(DicomObject source) {
        // プロジェクト内の DicomObject の複製メソッドを呼び出してください。
        // もし複製機能がない場合は、毎回 loadSampleDicom() を呼ぶように変更してください。
    	DicomObjectChe che = (DicomObjectChe)source;
    	DicomObject clone = new DicomObjectChe((Attributes)che);
        return clone; // 仮置き
    }

    private PatientMapping createTestPatientMapping(String pn, String pid) {
        // TODO: deidentifyに必要な PatientMapping を生成してください
         PatientMapping pMap = new PatientMapping();
         pMap.newPatId = pid;
         pMap.newPatName = pn;
         return pMap;
    }
}