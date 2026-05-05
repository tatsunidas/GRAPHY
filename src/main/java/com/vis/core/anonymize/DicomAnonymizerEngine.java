/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Sequence;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;

public class DicomAnonymizerEngine {

    // =========================================================================
    // 高速検索用のルールマップ (O(1) でのルール検索用)
    // =========================================================================
    private static final Map<Integer, DicomTagRule> RULE_MAP = new HashMap<>();
    
    // ★ 追加: 匿名化の対象外とする（絶対に保護する）DICOM必須UIDのリスト
    private static final Set<Integer> PROTECTED_UIDS = new HashSet<>(Arrays.asList(
            Tag.TransferSyntaxUID,          // (0002,0010)
            Tag.MediaStorageSOPClassUID,    // (0002,0002)
            Tag.ImplementationClassUID,     // (0002,0012)
            Tag.SOPClassUID,                // (0008,0016)
            Tag.RelatedGeneralSOPClassUID,  // (0008,001A)
            Tag.OriginalSpecializedSOPClassUID // (0008,001B)
    ));
    
    static {
        // クラスロード時にTAG_RULESをHashMapに変換しておく
        for (DicomTagRule rule : AnonymizeTagDictionary.TAG_RULES) {
            RULE_MAP.put(rule.getTag(), rule);
        }
    }

    // =========================================================================
    // 内部マッピング用クラス群 (ツリー構造)
    // =========================================================================

    private static class InstanceMapping {
        @SuppressWarnings("unused")
        String origSopUid;
        String newSopUid;
        File sourceFile; // 処理対象ファイル
    }

    private static class SeriesMapping {
        @SuppressWarnings("unused")
        String origSeriesUid;
        String newSeriesUid;
        Map<String, InstanceMapping> instances = new HashMap<>();
    }

    private static class StudyMapping {
        String origStudyUid;
        String newStudyUid;
        String studyDate;
        String studyTime;
        String modality; 
        Map<String, SeriesMapping> series = new HashMap<>();
    }

    private static class PatientMapping {
        String origPatId;
        String origPatName;
        String newPatId;
        String newPatName;
        Map<String, StudyMapping> studies = new HashMap<>();
    }

    private static class CsvRecord {
        String origPatId, origPatName, studyDate, studyTime, modality, origStudyUid;
        String newPatName, newPatId, newStudyUid;
    }
    
	public interface ProgressListener {
		void onProgress(int current, int total, String message);
	}

	private ProgressListener progressListener;

	public void setProgressListener(ProgressListener listener) {
		this.progressListener = listener;
	}

	private void notifyProgress(int current, int total, String message) {
		if (progressListener != null) {
			progressListener.onProgress(current, total, message);
		}
	}

    // =========================================================================
    // メイン処理
    // =========================================================================

    public void transcodeDirectory(File srcDir, File destDir, AnonymizeConfig config) throws IOException {
        if (!srcDir.exists() || !srcDir.isDirectory()) return;
        if (!destDir.exists()) destDir.mkdirs();

        Map<String, PatientMapping> patientTree = new HashMap<>();
        List<CsvRecord> csvRecords = new ArrayList<>();
        
        // バッチ全体で一貫性を保つためのUIDマッピング辞書
        Map<String, String> globalUidMap = new HashMap<>();
        
        List<File> dicomFiles = findAllDicomFiles(srcDir);
        
        buildTree(dicomFiles, patientTree, csvRecords, config, globalUidMap);
        executeAnonymization(patientTree, destDir, config, globalUidMap);
        writeCsvMapping(destDir, csvRecords);
    }
    
    private void buildTree(List<File> files, Map<String, PatientMapping> patientTree, List<CsvRecord> csvRecords,
                           AnonymizeConfig config, Map<String, String> globalUidMap) {

        // --- フェーズ1: ツリー構造構築 ---
        for (File f : files) {
            if (!DicomUtilities.isDicomFile(f)) continue;

            try {
                DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
                reader.read(f.getCanonicalPath(), true);
                DicomObject dataset = reader.getHeader();

                String origPatId_ = dataset.getString(Tag.PatientID);
                if (origPatId_ == null || origPatId_.trim().isEmpty()) origPatId_ = "UNKNOWN";
                final String origPatId = origPatId_;
                
                String origStudyUid = dataset.getString(Tag.StudyInstanceUID);
                String origSeriesUid = dataset.getString(Tag.SeriesInstanceUID);
                String origSopUid = dataset.getString(Tag.SOPInstanceUID);

                if (origStudyUid == null || origSeriesUid == null || origSopUid == null) {
                    Log.logger.log(Level.WARNING, "Skipping file missing necessary UIDs: " + f.getName());
                    continue;
                }

                // Patient レベル
                PatientMapping pMap = patientTree.computeIfAbsent(origPatId, k -> {
                    PatientMapping newMap = new PatientMapping();
                    newMap.origPatId = origPatId;
                    newMap.origPatName = dataset.getString(Tag.PatientName);
                    return newMap;
                });

                // Study レベル
                StudyMapping stMap = pMap.studies.get(origStudyUid);
                if (stMap == null) {
                    stMap = new StudyMapping();
                    stMap.origStudyUid = origStudyUid;
                    stMap.newStudyUid = globalUidMap.computeIfAbsent(origStudyUid, k -> UIDUtils.createUID());
                    stMap.studyDate = dataset.getString(Tag.StudyDate);
                    stMap.studyTime = dataset.getString(Tag.StudyTime);
                    stMap.modality = dataset.getString(Tag.Modality);
                    pMap.studies.put(origStudyUid, stMap);
                }

                // Series レベル
                SeriesMapping seMap = stMap.series.get(origSeriesUid);
                if (seMap == null) {
                    seMap = new SeriesMapping();
                    seMap.origSeriesUid = origSeriesUid;
                    seMap.newSeriesUid = globalUidMap.computeIfAbsent(origSeriesUid, k -> UIDUtils.createUID());
                    stMap.series.put(origSeriesUid, seMap);
                }

                // Instance レベル (重複排除)
                if (seMap.instances.containsKey(origSopUid)) {
                    Log.logger.log(Level.INFO, "Duplicate SOPInstanceUID found, skipping file: " + f.getName());
                    continue;
                }

                InstanceMapping iMap = new InstanceMapping();
                iMap.origSopUid = origSopUid;
                iMap.newSopUid = globalUidMap.computeIfAbsent(origSopUid, k -> UIDUtils.createUID());
                iMap.sourceFile = f;
                seMap.instances.put(origSopUid, iMap);

            } catch (Exception e) {
                Log.logger.log(Level.SEVERE, "Failed to pre-scan: " + f.getName(), e);
            }
        }

        // --- フェーズ2: 採番とCSVレコード作成 ---
        String prefix = config.getReplacePatientId();
        if (prefix == null || prefix.isEmpty()) prefix = "ANON";

        String namePrefix = config.getReplacePatientName();
        if (namePrefix == null || namePrefix.isEmpty()) namePrefix = "ANON";

        boolean isSinglePatient = (patientTree.size() == 1);
        int patientCounter = 1;
        
		// ★ 1. HashMapの要素をListに抽出する
		List<PatientMapping> patientList = new ArrayList<>(patientTree.values());

		// ★ 2. まず元のPatientIDでソートし、順序を確定させる（HashMapの環境依存な揺らぎを完全に排除する）
		patientList.sort((p1, p2) -> p1.origPatId.compareTo(p2.origPatId));

		// ★ 3. シード値が設定されていれば、そのシードを使ってシャッフルする
		Number seed = config.getRandomSeed();
		if (seed != null) {
			Collections.shuffle(patientList, new java.util.Random(config.getRandomSeed()));
		}

        for (PatientMapping pMap : patientList) {
            if (isSinglePatient) {
                pMap.newPatId = prefix;
                pMap.newPatName = namePrefix;
            } else {
                pMap.newPatId = String.format("%s%03d", prefix, patientCounter);
                pMap.newPatName = namePrefix + "^" + patientCounter;
            }
            patientCounter++;
            
			// Studyレベルでも必要であれば同様にソート＆シャッフルが可能です
			// （通常は患者単位でIDがランダム化されていれば十分なことが多いです）
			List<StudyMapping> studyList = new ArrayList<>(pMap.studies.values());
//			studyList.sort((s1, s2) -> s1.origStudyUid.compareTo(s2.origStudyUid));
//			if (config.getRandomSeed() != null) {
//				Collections.shuffle(studyList, new java.util.Random(config.getRandomSeed()));
//			}

            for (StudyMapping stMap : studyList) {
                CsvRecord rec = new CsvRecord();
                rec.origPatId = pMap.origPatId;
                rec.origPatName = pMap.origPatName;
                rec.studyDate = stMap.studyDate;
                rec.studyTime = stMap.studyTime;
                rec.modality = stMap.modality;
                rec.origStudyUid = stMap.origStudyUid;
                rec.newPatId = pMap.newPatId;
                rec.newPatName = pMap.newPatName;
                rec.newStudyUid = stMap.newStudyUid;
                csvRecords.add(rec);
            }
        }
    }

	// executeAnonymization メソッドを以下のように調整します
	private void executeAnonymization(Map<String, PatientMapping> patientTree, File destDir, AnonymizeConfig config,
			Map<String, String> globalUidMap) {

		// 全ファイル数を計算（プログレスバーの分母用）
		int totalFiles = 0;
		for (PatientMapping p : patientTree.values())
			for (StudyMapping st : p.studies.values())
				for (SeriesMapping se : st.series.values())
					totalFiles += se.instances.size();

		int processedCount = 0;

		for (PatientMapping pMap : patientTree.values()) {
			for (StudyMapping stMap : pMap.studies.values()) {
				for (SeriesMapping seMap : stMap.series.values()) {
					File outDir = new File(destDir,
							pMap.newPatId + File.separator + stMap.newStudyUid + File.separator + seMap.newSeriesUid);
					outDir.mkdirs();

					for (InstanceMapping iMap : seMap.instances.values()) {
						// ★ユーザーが中断したかどうかのチェック
						if (Thread.currentThread().isInterrupted()) {
							notifyProgress(processedCount, totalFiles, "Process interrupted by user.");
							return; // 即座にメソッドを抜けて処理を中止
						}

						try {
							DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
							reader.read(iMap.sourceFile.getCanonicalPath(), true);
							DicomObject fmi = reader.getFileMetaInfomation();
							DicomObject dataset = reader.getHeader();

							deidentify(dataset, config, pMap, globalUidMap);

							String tsuid = fmi.getString(Tag.TransferSyntaxUID);
							DicomWriter writer = DicomWriter.newDicomWriter();
							writer.write(dataset, tsuid, new File(outDir, iMap.newSopUid + ".dcm").getCanonicalPath());

							// ★プログレスバーの更新とログの通知
							processedCount++;
							notifyProgress(processedCount, totalFiles, "Processed: " + iMap.sourceFile.getName());

						} catch (Exception e) {
							Log.logger.log(Level.SEVERE, "Failed to process file: " + iMap.sourceFile.getName(), e);
							notifyProgress(processedCount, totalFiles, "Failed: " + iMap.sourceFile.getName());
						}
					}
				}
			}
		}
	}
    
	private void deidentify(DicomObject dataset, AnonymizeConfig config, PatientMapping pMap,
			Map<String, String> globalUidMap) {
		deidentifyRecursive(dataset, config, pMap, globalUidMap);

		dataset.setString(Tag.PatientName, VR.PN, pMap.newPatName);
		dataset.setString(Tag.PatientID, VR.LO, pMap.newPatId);

		// ---------------------------------------------------------
		// 匿名化フラグとメソッドの記録（PS 3.15 E.1.1 準拠）
		// ---------------------------------------------------------
		// 1. Patient Identity Removed フラグ
		dataset.setString(Tag.PatientIdentityRemoved, VR.CS, "YES");

		// De-identification Method (0012,0063) へのテキスト記述追加
		String methodText = "Basic Application Level Confidentiality Profile";
		dataset.setString(Tag.DeidentificationMethod, VR.LO, methodText);

		// De-identification Method Code Sequence (0012,0064) の構築
		Sequence methodCodeItems = (Sequence) dataset.newDicomSequence(Tag.DeidentificationMethodCodeSequence, 0);
		addCodeSequenceItem(methodCodeItems,"113100", "DCM", "Basic Application Confidentiality Profile");

		// 各オプションのコード追加
		// Table CID 7050. De-identification Method
		if (config.hasOption(AnonymizeConfig.Option.CleanPixelData)) {
			addCodeSequenceItem(methodCodeItems, "113101", "DCM", "Clean Pixel Data Option");
			// このオプションが適用された場合、Burned In Annotation (0028,0301) を "NO" に設定する義務があります 。
			dataset.setString(Tag.BurnedInAnnotation, VR.CS, "NO");
		}
		if (config.hasOption(AnonymizeConfig.Option.CleanRecognizableVisualFeatures)) {
			addCodeSequenceItem(methodCodeItems, "113102", "DCM", "Clean Recognizable Visual Features Option");
		}

		if (config.hasOption(AnonymizeConfig.Option.CleanGraphics)) {
			addCodeSequenceItem(methodCodeItems,"113103", "DCM", "Clean Graphics Option");
		}

		if (config.hasOption(AnonymizeConfig.Option.CleanStructuredContent)) {
			addCodeSequenceItem(methodCodeItems,"113104", "DCM", "Clean Structured Content Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.CleanDescriptors)) {
			addCodeSequenceItem(methodCodeItems,"113105", "DCM", "Clean Descriptors Option");
		}

		if (config.hasOption(AnonymizeConfig.Option.RetainLongitudinalTemporalInformationFullDates)) {
			addCodeSequenceItem(methodCodeItems,"113106", "DCM",
					"Retain Longitudinal Temporal Information Full Dates Option");
		}

		if (config.hasOption(AnonymizeConfig.Option.RetainLongitudinalTemporalInformationModifiedDates)) {
			addCodeSequenceItem(methodCodeItems, "113107", "DCM",
					"Retain Longitudinal Temporal Information Modified Dates Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.RetainPatientCharacteristics)) {
			addCodeSequenceItem(methodCodeItems,"113108", "DCM", "Retain Patient Characteristics Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.RetainDeviceIdentity)) {
			addCodeSequenceItem(methodCodeItems,"113109", "DCM", "Retain Device Identity Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.RetainUIDs)) {
			addCodeSequenceItem(methodCodeItems,"113110", "DCM", "Retain UIDs Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.RetainSafePrivate)) {
			addCodeSequenceItem(methodCodeItems,"113111", "DCM", "Retain Safe Private Option");
		}
		if (config.hasOption(AnonymizeConfig.Option.RetainInstitutionIdentity)) {
			addCodeSequenceItem(methodCodeItems,"113112", "DCM",
					"Retain Longitudinal Temporal Information Full Dates Option");
		}
	}

	private void deidentifyRecursive(DicomObject dataset, AnonymizeConfig config, PatientMapping pMap,
			Map<String, String> globalUidMap) {

		cleanPrivateTags(dataset, config);

		int[] tags = dataset.tags();
		for (int tag : tags) {
			VR[] vrTypes = TagDict.vrType(tag);
			if (vrTypes == null || vrTypes.length == 0)
				continue;
			VR vr = vrTypes[0];

			DicomTagRule rule = RULE_MAP.get(tag); // ★ O(1)で高速検索

			// ==========================================================
			// ★ Clean Structured Content Option の処理
			// ==========================================================
			if (vr == VR.SQ && config.hasOption(AnonymizeConfig.Option.CleanStructuredContent)) {
				if (tag == Tag.ContentSequence || tag == Tag.AcquisitionContextSequence
						|| tag == Tag.SpecimenPreparationSequence) {

					cleanStructuredContentSequence(dataset, tag, config, pMap, globalUidMap);
					continue; // 構造化コンテンツ専用の処理を行ったので、通常のSQ処理はスキップ
				}
			}

			// シーケンス(SQ)の再帰処理
			if (vr == VR.SQ) {
				if (rule != null && !config.isRetain(rule)) {
					if (rule.getDefaultAction() == DicomTagRule.Action.X) {
						dataset.remove(tag);
						continue;
					} else if (rule.getDefaultAction() == DicomTagRule.Action.Z) {
						dataset.setNull(tag, vr);
						continue;
					}
				}

				// ★ 注意: getNestedDataset が単一のDicomObjectではなく、複数のItemを返すような仕様の場合は
				// ここで配列やリストを受け取って、全Itemに対してループで deidentifyRecursive を呼ぶ必要があります。
				DicomObject sq = dataset.getNestedDataset(tag);
				if (sq != null) {
					deidentifyRecursive(sq, config, pMap, globalUidMap);
				}
				continue;
			}

			// UIDの安全な置換処理
			if (vr == VR.UI) {
				// 1. ファイルの構造や形式を示す「必須UID」は絶対に置換せずスキップする
				if (PROTECTED_UIDS.contains(tag)) {
					continue;
				}

				// 2. Table E.1-1 に記載がある場合は、ルールに従う
				if (rule != null && !config.isRetain(rule)) {
					if (rule.getDefaultAction() == DicomTagRule.Action.U) {
						replaceUidGlobally(dataset, tag, vr, globalUidMap);
					} else {
						applyTagAction(dataset, tag, vr, rule, config, pMap);
					}
				}
				// 3. Tableに記載がない謎のUIDは、PS3.15の安全方針に則り、Retainオプションが無ければ置換する
				else if (rule == null && !config.hasOption(AnonymizeConfig.Option.RetainUIDs)) {
					replaceUidGlobally(dataset, tag, vr, globalUidMap);
				}
				continue;
			}

			// その他のタグ
			if (rule != null && !config.isRetain(rule)) {
				applyTagAction(dataset, tag, vr, rule, config, pMap);
			}
		}
	}
	
	/**
     * Code SequenceのItemを生成するヘルパーメソッド
     */
    private void addCodeSequenceItem(Sequence sq, String codeValue, String codingScheme, String codeMeaning) {
        DicomObject item = DicomObject.newDicomObject();
        item.setString(Tag.CodeValue, VR.SH, codeValue);
        item.setString(Tag.CodingSchemeDesignator, VR.SH, codingScheme);
        item.setString(Tag.CodeMeaning, VR.LO, codeMeaning);
        sq.add(item);
    }

	/**
	 * プライベートタグの安全判定を行い、不要なものを削除する
	 */
	private void cleanPrivateTags(DicomObject dataset, AnonymizeConfig config) {
		boolean retainSafe = config.hasOption(AnonymizeConfig.Option.RetainSafePrivate);
		int[] tags = dataset.tags();

		for (int tag : tags) {
			int group = (tag >>> 16) & 0xFFFF;
			if (group % 2 == 0)
				continue; // 偶数グループ（標準タグ）はスキップ

			int element = tag & 0xFFFF;

			if (element >= 0x0010 && element <= 0x00FF) {
				// Private Creator タグ (例: 0x0019, 0x0010)
				if (!retainSafe) {
					dataset.remove(tag);
				}
			} else if (element > 0x00FF) {
				// Private Data タグ (例: 0x0019, 0x1023)
				boolean isSafe = false;

				if (retainSafe) {
					// クリエイターIDを参照して安全か判定する
					int block = element >>> 8; // e.g. 0x10 (from 0x1023)
					int creatorTag = (group << 16) | block; // 該当するCreatorタグの番号
					String creatorStr = dataset.getString(creatorTag);

					if (creatorStr != null) {
						creatorStr = creatorStr.trim();
						// 辞書からこのCreatorの安全なタグシグネチャ一覧を取得
						Set<Integer> safeSignatures = AnonymizeTagDictionary.SAFE_PRIVATE_ATTRIBUTES.get(creatorStr);
						if (safeSignatures != null) {
							int elemLower = element & 0xFF; // e.g. 0x23 (from 0x1023)
							int signature = (group << 16) | elemLower;
							// シグネチャが辞書に含まれていれば安全と判定
							if (safeSignatures.contains(signature)) {
								isSafe = true;
							}
						}
					}
				}

				if (!isSafe) {
					dataset.remove(tag); // 安全でない、またはオプションOFFの場合は削除
				}
			}
		}
	}

	/**
	 * 構造化コンテンツ（SR等）のシーケンスを安全にクリーニングする
	 */
	private void cleanStructuredContentSequence(DicomObject parentDataset, int sqTag, AnonymizeConfig config,
			PatientMapping pMap, Map<String, String> globalUidMap) {

		// ※お使いのAPIに合わせてItemのリストを取得するループ処理に書き換えてください。
		// ここでは1つのDicomObjectを返す仕様を想定して記述しています。
		DicomObject item = parentDataset.getNestedDataset(sqTag);
		if (item == null)
			return;

		// 1. Concept Name をチェックして、個人情報アイテムかどうか判定
		if (isIdentifiableContentItem(item)) {
			// 2. アイテムが持つ「値」の型 (Value Type) を見て、適切なダミー値で上書きする
			String valueType = item.getString(Tag.ValueType);
			if (valueType != null) {
				switch (valueType) {
				case "PNAME":
					item.setString(Tag.PersonName, VR.PN, "ANONYMOUS");
					break;
				case "TEXT":
					item.setString(Tag.TextValue, VR.UT, "DUMMY");
					break;
				case "UIDREF":
					replaceUidGlobally(item, Tag.UID, VR.UI, globalUidMap);
					break;
				case "DATETIME":
					item.setString(Tag.DateTime, VR.DT, "20000101000000");
					break;
				case "DATE":
					item.setString(Tag.Date, VR.DA, "20000101");
					break;
				// 必要に応じて追加
				}
			}
		}

		// 3. 構造化コンテンツ自体がツリー構造のため、アイテム自体に対しても通常の再帰匿名化をかける
		deidentifyRecursive(item, config, pMap, globalUidMap);
	}

	/**
	 * Itemが持つ Concept Name Code Sequence を見て、クリーニング対象か判定する
	 */
	private boolean isIdentifiableContentItem(DicomObject item) {
		try {
			// ※APIに合わせてConceptNameCodeSequence(0040,A043)を取得してください
			DicomObject conceptCodeItem = item.getNestedDataset(Tag.ConceptNameCodeSequence);
			if (conceptCodeItem != null) {
				String codeValue = conceptCodeItem.getString(Tag.CodeValue);
				String codingScheme = conceptCodeItem.getString(Tag.CodingSchemeDesignator);

				if (codeValue != null && codingScheme != null) {
					String key = codingScheme.trim() + ":" + codeValue.trim();
					// 辞書に含まれていれば個人情報アイテム
					return AnonymizeTagDictionary.SR_CLEAN_CODES.contains(key);
				}
			}
		} catch (Exception e) {
			// 無視してfalseを返す
		}
		return false;
	}

    private void applyTagAction(DicomObject dataset, int tag, VR vr, DicomTagRule rule, AnonymizeConfig config, PatientMapping pMap) {
        String customVal = config.getCustomTagReplacements().get(tag);
        
        switch (rule.getDefaultAction()) {
            case X:
                dataset.remove(tag);
                break;
            case Z:
            	dataset.setNull(tag, vr); 
                break;
            case D:
            case C:
                if (tag == Tag.PatientID) {
                	dataset.setString(tag, vr, pMap.newPatId);
                } else if (tag == Tag.PatientName) {
                	dataset.setString(tag, vr, pMap.newPatName);
                } else if (customVal != null && !customVal.trim().isEmpty()) {
                	dataset.setString(tag, vr, customVal.trim());
                } else {
                	dataset.setString(tag, vr, getDefaultDummyValue(vr));
                }
                break;
            default:
                break;
        }
    }

    private void replaceUidGlobally(DicomObject dataset, int tag, VR vr, Map<String, String> globalUidMap) {
        String origUid = dataset.getString(tag);
        if (origUid == null || origUid.trim().isEmpty()) return;

        String newUid = globalUidMap.computeIfAbsent(origUid, k -> UIDUtils.createUID());
        dataset.setString(tag, vr, newUid);
    }

    private void writeCsvMapping(File destDir, List<CsvRecord> records) {
        File csvFile = new File(destDir, "Anonymize_Mapping.csv");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"))) {
            pw.write('\ufeff'); // BOM
            pw.println("Original PatientID,Original PatientName,StudyDate,StudyTime,Modality,Original StudyInstUID,New PatientName,New PatientID,New StudyInstUID");
            
            for (CsvRecord r : records) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        r.origPatId, r.origPatName, r.studyDate, r.studyTime, r.modality, r.origStudyUid,
                        r.newPatName, r.newPatId, r.newStudyUid);
            }
        } catch (IOException e) {
            Log.logger.log(Level.SEVERE, "Failed to write CSV mapping.", e);
        }
    }

    private List<File> findAllDicomFiles(File dir) {
        List<File> results = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    results.addAll(findAllDicomFiles(f));
                } else if (DicomUtilities.isDicomFile(f) && !DicomUtilities.isDICOMDIR(f)) {
                    results.add(f);
                }
            }
        }
        return results;
    }
    
    private String getDefaultDummyValue(VR vr) {
        if (vr == null) return "DUMMY";
        String vrName = vr.toString(); 

        switch (vrName) {
            case "DA": return "20000101";
            case "TM": return "000000";
            case "DT": return "20000101000000";
            case "AS": return "000Y";
            case "PN": return "ANONYMOUS";
            case "SH": 
            case "LO": 
            case "ST": 
            case "LT": 
            case "UT": 
            case "CS": return "DUMMY";
            case "IS": 
            case "US": 
            case "SS": 
            case "SL": 
            case "UL": return "0";
            case "DS": 
            case "FL": 
            case "FD": return "0.0";
            default:   return "DUMMY";
        }
    }
}