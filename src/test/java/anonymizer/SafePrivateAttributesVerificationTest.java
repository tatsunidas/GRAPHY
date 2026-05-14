package anonymizer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.vis.core.anonymize.AnonymizeTagDictionary;

public class SafePrivateAttributesVerificationTest {

    @Before
    public void setUp() {
        // テスト前に辞書がロードされていることを確認
        assertNotNull("SAFE_PRIVATE_ATTRIBUTES should not be null", AnonymizeTagDictionary.SAFE_PRIVATE_ATTRIBUTES);
    }

    @Test
    public void verifyAndPrintSafePrivateAttributes() {
        Map<String, Set<Integer>> safePrivateMap = AnonymizeTagDictionary.SAFE_PRIVATE_ATTRIBUTES;

        // 1. 自動検証 (アサーション)
        assertFalse("SAFE_PRIVATE_ATTRIBUTES should not be empty. CSV might not be loaded.", safePrivateMap.isEmpty());

        System.out.println("==========================================================");
        System.out.println(" DICOM PS3.15 Table E.3.10-1 Safe Private Attributes Report ");
        System.out.println("==========================================================\n");

        int totalCreators = safePrivateMap.size();
        int totalTags = 0;

        // 見やすくするために Creator 名のアルファベット順にソートして出力
        Set<String> sortedCreators = new TreeSet<>(safePrivateMap.keySet());

        for (String creator : sortedCreators) {
            Set<Integer> signatures = safePrivateMap.get(creator);
            
            System.out.println("■ Private Creator: [" + creator + "]");
            System.out.println("----------------------------------------------------------");
            
            // シグネチャ（Integer）を元のDICOMタグ表現 (gggg,xxee) に逆算して表示
            for (Integer signature : signatures) {
                // 上位16ビットがグループ番号
                int group = signature >>> 16;
                // 下位16ビットがエレメントパターンのベース値
                int elemLower = signature & 0xFFFF;
                
                // 元の文字列のように "(gggg,xxee)" 形式に復元する
                // elemLowerが 0x0000 の場合は "(gggg,xx00)" のような表現だったと推測
                String elemStr = String.format("%04X", elemLower);
                if (elemStr.endsWith("00")) {
                    elemStr = "xx" + elemStr.substring(2); // "0000" -> "xx00", "1000" -> "xx10" など
                }
                
                String tagFormat = String.format("(%04X,%s)", group, elemStr);
                
                System.out.printf("  %s  (Signature Hex: %08X)%n", tagFormat, signature);
                totalTags++;
            }
            System.out.println("----------------------------------------------------------\n");
        }

        System.out.println("==========================================================");
        System.out.println(" Total Private Creators: " + totalCreators);
        System.out.println(" Total Safe Private Tags Loaded: " + totalTags);
        System.out.println("==========================================================");

        // 2. 代表的なベンダー（例えばPhilipsなど）が含まれているかの簡易チェック
        // ※ もしCSV内に "Philips PET Private Group" 等が存在すればパスします。
        // ※ お使いのCSVの内容に合わせて、確実に存在する文字列に変更していただくと、より堅牢なテストになります。
        boolean containsExpectedVendor = false;
        for (String creator : safePrivateMap.keySet()) {
            if (creator.toLowerCase().contains("philips") || 
                creator.toLowerCase().contains("ge") || 
                creator.toLowerCase().contains("siemens") ||
                creator.toLowerCase().contains("agfa")) {
                containsExpectedVendor = true;
                break;
            }
        }
        assertTrue("Dictionary should contain major vendors like Philips, GE, or Siemens.", containsExpectedVendor);
    }
}
