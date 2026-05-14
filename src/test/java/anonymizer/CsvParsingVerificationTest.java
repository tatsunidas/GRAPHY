package anonymizer;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import com.vis.core.anonymize.AnonymizeConfig.Option;
import com.vis.core.anonymize.DicomTagRule;
import com.vis.core.anonymize.DicomTagRule.Action;
import com.vis.core.anonymize.AnonymizeTagDictionary;

public class CsvParsingVerificationTest {

    @Test
    public void printTagsPerOptionForCsvVerification() {
        System.out.println("==========================================================");
        System.out.println(" DICOM PS3.15 Table E.1-1 CSV Parsing Verification Report ");
        System.out.println("==========================================================\n");

        // 検証したい全オプションの配列
        Option[] optionsToVerify = {
            Option.RetainSafePrivate,
            Option.RetainUIDs,
            Option.RetainDeviceIdentity,
            Option.RetainInstitutionIdentity,
            Option.RetainPatientCharacteristics,
            Option.RetainLongitudinalTemporalInformationFullDates,
            Option.RetainLongitudinalTemporalInformationModifiedDates,
            Option.CleanDescriptors,
            Option.CleanStructuredContent,
            Option.CleanGraphics
        };

        for (Option opt : optionsToVerify) {
            System.out.println("■ Option: [" + opt.name() + "]");
            System.out.println("----------------------------------------------------------");
            System.out.printf("  %-13s | %-50s | %s%n", "Tag", "Attribute Name", "Action");
            System.out.println("----------------------------------------------------------");

            int count = 0;
            List<DicomTagRule> matchedRules = new ArrayList<>();

            // TAG_RULESを走査して、このオプションが紐づいているタグを抽出
            for (DicomTagRule rule : AnonymizeTagDictionary.TAG_RULES) {
                if (rule.getOptionActions().containsKey(opt)) {
                    matchedRules.add(rule);
                }
            }

            // 見つかったタグを出力
            for (DicomTagRule rule : matchedRules) {
                Action act = rule.getOptionActions().get(opt);
                String tagHex = String.format("(%04X,%04X)", rule.getTag() >>> 16, rule.getTag() & 0xFFFF);
                
                // コンソール出力時のフォーマット（名前が長すぎる場合は切り詰め）
                String name = rule.getName();
                if (name.length() > 48) {
                    name = name.substring(0, 45) + "...";
                }
                
                System.out.printf("  %-13s | %-50s | %s%n", tagHex, name, act);
                count++;
            }

            System.out.println("----------------------------------------------------------");
            System.out.println("  -> Total matches for " + opt.name() + ": " + count + " tags\n");
        }
        
        System.out.println("==========================================================");
        System.out.println(" Total Tags Loaded in Dictionary: " + AnonymizeTagDictionary.TAG_RULES.size());
        System.out.println("==========================================================");
    }
}