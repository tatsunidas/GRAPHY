/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.media;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.imageio.Nifti_Reader;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import io.github.tatsunidas.ij.plugin.nifti.NiftiHeader;

public class NIfTIToDicomConverter {
	
	// ★ 新規追加: 進捗を通知するためのインターフェース
    public interface ProgressListener {
        void onProgress(int current, int total, String message);
    }
    
    final static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
    final static SimpleDateFormat sdfTime = new SimpleDateFormat("HHmmss");
    
    private static final Map<String, Integer> DICOM_DICT = buildDicomDictionary();
    
	private static Map<String, Integer> buildDicomDictionary() {
		Map<String, Integer> dict = new HashMap<>();
		try {
			Field[] fields = Tag.class.getFields();
			for (Field f : fields) {
				if (f.getType() == int.class) {
					// キーワード名を小文字に統一して保持（照合しやすくするため）
					dict.put(f.getName().toLowerCase(), f.getInt(null));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dict;
	}
    
    public static void saveAsDicom(
            File nifti, 
            File metaJSON, 
            String outputDir, 
            Modality mm, 
            String patientId,
            String patientName,
            String studyUID,
            String seriesUID,
            java.util.Date studyDate,
            int seriesNumber,
            ProgressListener listener // <--- ★追加
            ) throws IOException {
        
        Nifti_Reader nr = new Nifti_Reader();
        ImagePlus images = nr.load(nifti.getParentFile().getCanonicalPath(), nifti.getName());
        
		// ★ 万が一読み込み失敗（null）になったら例外を投げる
		if (images == null) {
			throw new IOException("Failed to load NIfTI image (Memory or Format issue).");
		}

		// ★ 新規追加: ロードが無事に終わったことをUIに通知
		if (listener != null) {
			listener.onProgress(0, 100, "Image loaded in memory. Preparing to convert...");
		}
        
        NiftiHeader hdr = nr.getHeader();
        FileInfo fi = nr.getFileInfo();
        
        // ★ 追加：ImageJが4D/5Dデータをフラットなスタックとして認識してしまう問題の補正
        // NIfTIの dim 配列は [0]=次元数, [1]=X, [2]=Y, [3]=Z, [4]=Time, [5]=Channel を表す
        int trueP = (hdr.dim[0] >= 3 && hdr.dim[3] > 0) ? hdr.dim[3] : 1;
        int trueT = (hdr.dim[0] >= 4 && hdr.dim[4] > 0) ? hdr.dim[4] : 1;
        int trueC = (hdr.dim[0] >= 5 && hdr.dim[5] > 0) ? hdr.dim[5] : 1;
        
        	// ヘッダの次元の掛け算がスタック総数と一致すれば、Hyperstackとして次元を再設定する
        if (trueP * trueT * trueC == images.getStackSize()) {
            images.setDimensions(trueC, trueP, trueT);
        }
        
        String sopClassUID = sopClassUidOf(mm).uid();
        boolean isColor = images.isRGB();
        int BitsAllocated = isColor ? 8 : images.getBitDepth();
        int w = images.getWidth();
        int h = images.getHeight();
        boolean isSigned = false;
        boolean bigEndian = !fi.intelByteOrder;
        
        Calibration cal = images.getCalibration();
        double[] spacingYX = new double[] {cal.pixelHeight, cal.pixelWidth};
        double sliceThickness = cal.pixelDepth;
        double rescaleIntercept = 0.0;
        double rescaleSlope = 1.0;
        double[] coeff = cal.getCoefficients();
        if (coeff != null && coeff.length >= 2) {
            rescaleIntercept = coeff[0];
            rescaleSlope = coeff[1];
        }
        
        // --- 座標変換 (RAS to LPS) ---
        double m00 = -hdr.srow_x[0]; double m01 = -hdr.srow_x[1]; double m02 = -hdr.srow_x[2]; double m03 = -hdr.srow_x[3];
        double m10 = -hdr.srow_y[0]; double m11 = -hdr.srow_y[1]; double m12 = -hdr.srow_y[2]; double m13 = -hdr.srow_y[3];
        double m20 =  hdr.srow_z[0]; double m21 =  hdr.srow_z[1]; double m22 =  hdr.srow_z[2]; double m23 =  hdr.srow_z[3];
        
        // right handed rule
     // 3x3行列の行列式(Determinant)を計算して空間の「手系（Handedness）」を判定
        double det = m00 * (m11 * m22 - m12 * m21) 
                   - m01 * (m10 * m22 - m12 * m20) 
                   + m02 * (m10 * m21 - m11 * m20);

        // 行列式がマイナス（左手系）なら、Y軸をフリップして右手系に矯正するフラグを立てる
        boolean needFlipToRightHanded = (det < 0);

        if (needFlipToRightHanded) {
            double orig_m01 = m01; double orig_m11 = m11; double orig_m21 = m21;
            m01 = -orig_m01; m11 = -orig_m11; m21 = -orig_m21;
            m03 = m03 + orig_m01 * (h - 1);
            m13 = m13 + orig_m11 * (h - 1);
            m23 = m23 + orig_m21 * (h - 1);
        }
        
        double rLen = Math.sqrt(m00*m00 + m10*m10 + m20*m20);
        double cLen = Math.sqrt(m01*m01 + m11*m11 + m21*m21);

        double[] iop;
        if (rLen == 0 || cLen == 0) {
            iop = new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0};
        } else {
            iop = new double[]{m00/rLen, m10/rLen, m20/rLen, m01/cLen, m11/cLen, m21/cLen};
        }
        
        // ★ ループの前にGsonでJSONを一度だけロード
        Map<String, Object> metaMap = loadJsonMetadata(metaJSON);
        
        int nChannels = images.getNChannels();
        int nSlices = images.getNSlices();
        int nFrames = images.getNFrames();
        int instNo = 1;
        
        Log.logger.log(Level.FINE, "NIfTI demension:(Channels, Slices, TimeFrame) "+"["+nChannels+","+nSlices+","+nFrames+"]");
        
        int totalImages = nFrames * nChannels * nSlices;
        
        for (int t = 1; t <= nFrames; t++) {
            for (int c = 1; c <= nChannels; c++) {
                for (int z = 1; z <= nSlices; z++) {
                    int k = z - 1;
                    double ippX = m03 + m02 * k;
                    double ippY = m13 + m12 * k;
                    double ippZ = m23 + m22 * k;
                    double[] ipp = new double[]{ippX, ippY, ippZ};
                    
                    images.setPosition(c, z, t);
                    if (needFlipToRightHanded) {
                        images.getProcessor().flipVertical();
                    }
                    
                    byte[] pixelDataBytes = null;
                    if(BitsAllocated == 8 && !isColor) {
                        ByteProcessor bp = (ByteProcessor) images.getProcessor();
                        pixelDataBytes = (byte[])bp.getPixels();
                    } else if(BitsAllocated == 16) {
                        ShortProcessor sp = (ShortProcessor) images.getProcessor();
                        pixelDataBytes = ByteUtils.shortToBytes((short[])sp.getPixels(), bigEndian);
                    } else if(BitsAllocated == 32) {
                        FloatProcessor fp = (FloatProcessor) images.getProcessor();
                        pixelDataBytes = ByteUtils.floatToBytes((float[])fp.getPixels(), bigEndian);
                    }
                                        
                    saveAsDicom(
                            outputDir, sopClassUID, patientId, patientName, studyUID, seriesUID, studyDate,
                            seriesNumber, instNo, isColor, BitsAllocated, w, h, isSigned,
                            rescaleIntercept, rescaleSlope, ipp, iop, spacingYX, sliceThickness,
                            pixelDataBytes, metaMap);
                    
                    if (listener != null) {
                        listener.onProgress(instNo, totalImages, "Converting...");
                    }
                    
                    /*
                     * instance numberはTime、Channelsで初期化せず、連番のままにすることで、時間軸を調整する。
                     */
                    instNo++;
                }
            }
        }
    }

    /**
     * Gsonを使用してJSONファイルをパースする
     */
    private static Map<String, Object> loadJsonMetadata(File jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) return new HashMap<>();
        try (FileReader reader = new FileReader(jsonFile)) {
            return new Gson().fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private static void saveAsDicom(String outputDirPath, String SOPClassUID, 
            String patientId, String patientName, String studyUID, String seriesUID, java.util.Date studyDate,
            int seriesNumber, int instanceNumber, boolean isColor, int BitsAllocated, int width, int height,
            boolean isSigned, double rescaleIntercept, double rescaleSlope, double[] ipp, double[] iop,
            double[] spacing, double sliceThickness, byte[] pixelDataBytes, Map<String, Object> metaMap) {
        
        try {
            File outDir = new File(outputDirPath);
            if (!outDir.exists()) outDir.mkdirs();
            
            DicomObject ds = DicomObject.newDicomObject();
            
            // --- 固定メタデータ ---
            ds.setString(Tag.PatientName, VR.PN, patientName);
            ds.setString(Tag.PatientID, VR.LO, patientId);
            ds.setDate(Tag.StudyDate, VR.DA, studyDate);
            ds.setDate(Tag.StudyTime, VR.TM, studyDate);
            ds.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            ds.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
            ds.setString(Tag.SOPClassUID, VR.UI, SOPClassUID);
            ds.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
            ds.setString(Tag.Modality, VR.CS, "MR");
            ds.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
            ds.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);

            // ★ JSONマップからメタデータを転記
//            if (metaMap != null) {
//                // String項目の転記
//                setDsString(ds, Tag.Manufacturer, VR.LO, metaMap.get("Manufacturer"));
//                setDsString(ds, Tag.ManufacturerModelName, VR.LO, metaMap.get("ManufacturersModelName"));//Manufacturer"s"
//                setDsString(ds, Tag.DeviceSerialNumber, VR.LO, metaMap.get("DeviceSerialNumber"));
//                setDsString(ds, Tag.SoftwareVersions, VR.LO, metaMap.get("SoftwareVersions"));
//                setDsString(ds, Tag.SequenceName, VR.SH, metaMap.get("SequenceName"));
//                
//                // Descriptionの優先順位付け
//                String desc = (String) metaMap.get("SeriesDescription");
//                if (desc == null) desc = (String) metaMap.get("ProtocolName");
//                ds.setString(Tag.SeriesDescription, VR.LO, desc != null ? desc : "Converted From NIfTI");
//
//                // 数値項目の転記と単位変換 (sec -> ms)
//                setDsDouble(ds, Tag.MagneticFieldStrength, VR.DS, metaMap.get("MagneticFieldStrength"), 1.0);
//                setDsDouble(ds, Tag.FlipAngle, VR.DS, metaMap.get("FlipAngle"), 1.0);
//                setDsDouble(ds, Tag.RepetitionTime, VR.DS, metaMap.get("RepetitionTime"), 1000.0); // msへ変換
//                setDsDouble(ds, Tag.EchoTime, VR.DS, metaMap.get("EchoTime"), 1000.0);             // msへ変換
//                setDsDouble(ds, Tag.InversionTime, VR.DS, metaMap.get("InversionTime"), 1000.0);   // msへ変換
//            }
			// ★ 自動マッピング処理
			if (metaMap != null) {
				for (Map.Entry<String, Object> entry : metaMap.entrySet()) {
					String jsonKey = entry.getKey();
					Object value = entry.getValue();
					if (value == null)
						continue;

					// 1. JSONのキーに最も近いDICOMキーワードを辞書から探す
					Integer tag = findBestMatchTag(jsonKey);

					if (tag != null) {
						// 2. 既知の「固定で処理すべきタグ」以外を自動セット
						// ※IPP, IOP, PixelDataなどは自動処理させないよう除外が必要
						if (isAutoMappable(tag)) {
							setDynamicTag(ds, tag, value);
						}
					}
				}
			}

            // --- 画像ピクセルデータ設定 (省略なし) ---
            ds.setInt(Tag.SamplesPerPixel, VR.US, isColor ? 3 : 1);
            ds.setString(Tag.PhotometricInterpretation, VR.CS, isColor ? "RGB" : "MONOCHROME2");
            if(isColor) ds.setInt(Tag.PlanarConfiguration, VR.US, 0);
            ds.setInt(Tag.Rows, VR.US, height);
            ds.setInt(Tag.Columns, VR.US, width);
            ds.setInt(Tag.BitsAllocated, VR.US, isColor ? 8 : BitsAllocated);
            ds.setInt(Tag.BitsStored, VR.US, isColor ? 8 : BitsAllocated);
            ds.setInt(Tag.HighBit, VR.US, isColor ? 7 : BitsAllocated - 1);
            ds.setInt(Tag.PixelRepresentation, VR.US, isSigned ? 1 : 0);
            ds.setDouble(Tag.RescaleSlope, VR.DS, rescaleSlope);
            ds.setDouble(Tag.RescaleIntercept, VR.DS, rescaleIntercept);
            ds.setDouble(Tag.ImagePositionPatient, VR.DS, ipp);
            ds.setDouble(Tag.ImageOrientationPatient, VR.DS, iop);
            ds.setDouble(Tag.PixelSpacing, VR.DS, spacing);
            ds.setDouble(Tag.SliceThickness, VR.DS, sliceThickness);
            ds.setDouble(Tag.SpacingBetweenSlices, VR.DS, sliceThickness);
            ds.setBytes(Tag.PixelData, VR.OW, pixelDataBytes);

            File outputFile = new File(outDir, String.format("slice_%04d.dcm", instanceNumber));
            DicomWriter.newDicomWriter().write(ds, UID.ExplicitVRLittleEndian.uid(), outputFile.getCanonicalPath());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- ヘルパーメソッド群 ---
    /**
     * 最も似ているタグを検索する（完全一致、または1文字の誤差を許容）
     */
	private static Integer findBestMatchTag(String key) {
		String normalizedKey = key.toLowerCase().replace("_", "").replace("-", "");

		// 1. まずは完全一致（大文字小文字無視）を探す（最も安全・高速）
		if (DICOM_DICT.containsKey(normalizedKey)) {
			return DICOM_DICT.get(normalizedKey);
		}

		// 2. 1文字の誤差（レーベンシュタイン距離が1）のタグを探す
		for (Map.Entry<String, Integer> entry : DICOM_DICT.entrySet()) {
			String dictKey = entry.getKey();

			// 処理を高速化するため、文字数の差が2以上の場合は絶対に距離1にならないので計算をスキップ
			if (Math.abs(normalizedKey.length() - dictKey.length()) > 1) {
				continue;
			}

			// 編集距離を計算
			int distance = calculateLevenshteinDistance(normalizedKey, dictKey);

			// 誤差がちょうど1文字（sの追加、1文字の欠落、1文字の置換）であれば採用
			if (distance <= 1) {
				// Log.logger.info("Fuzzy Matched: " + key + " -> " + dictKey); // 動作確認用
				return entry.getValue();
			}
		}

		return null;
	}

    /**
     * レーベンシュタイン距離（編集距離）を計算するアルゴリズム
     * @return 2つの文字列間で必要な変更（挿入、削除、置換）の最小回数
     */
    private static int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), // 挿入・削除
                        dp[i - 1][j - 1] + cost                       // 置換
                    );
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

	/**
	 * 値の型とDICOMのVR（Value Representation）に合わせて、適切なメソッドで値をセットする
	 */
	private static void setDynamicTag(DicomObject ds, int tag, Object val) {
		VR[] vrs = TagDict.vrType(tag);
		VR vr = (vrs != null && vrs.length > 0) ? vrs[0] : null;

		if (vr == null || val == null)
			return;

		double multiplier = isTimeTag(tag) ? 1000.0 : 1.0;

		// ★ 新規追加: Gsonが配列(List)として読み込んだ場合の処理
		if (val instanceof java.util.List) {
			java.util.List<?> list = (java.util.List<?>) val;
			if (list.isEmpty())
				return;

			if (vr == VR.IS || vr == VR.US || vr == VR.SS || vr == VR.UL || vr == VR.SL) {
				// 整数の配列としてセット
				int[] intArr = new int[list.size()];
				for (int i = 0; i < list.size(); i++) {
					Object item = list.get(i);
					if (item instanceof Number) {
						intArr[i] = (int) Math.round(((Number) item).doubleValue() * multiplier);
					} else if (item instanceof String) {
						try {
							intArr[i] = (int) Math.round(Double.parseDouble((String) item) * multiplier);
						} catch (Exception e) {
						}
					}
				}
				ds.setInt(tag, vr, intArr);
			} else if (vr == VR.FD || vr == VR.FL || vr == VR.DS) {
				// 浮動小数点の配列としてセット
				double[] dblArr = new double[list.size()];
				for (int i = 0; i < list.size(); i++) {
					Object item = list.get(i);
					if (item instanceof Number) {
						dblArr[i] = ((Number) item).doubleValue() * multiplier;
					} else if (item instanceof String) {
						try {
							dblArr[i] = Double.parseDouble((String) item) * multiplier;
						} catch (Exception e) {
						}
					}
				}
				ds.setDouble(tag, vr, dblArr);
			} else {
				// 文字列の配列としてセット
				String[] strArr = new String[list.size()];
				for (int i = 0; i < list.size(); i++) {
					strArr[i] = String.valueOf(list.get(i));
				}
				ds.setString(tag, vr, strArr);
			}
			return; // 配列の処理が終わったらここで終了
		}

		// --- 既存: 単一の値の場合の処理 ---
		if (val instanceof Number) {
			double dVal = ((Number) val).doubleValue() * multiplier;

			if (vr == VR.IS || vr == VR.US || vr == VR.SS || vr == VR.UL || vr == VR.SL) {
				ds.setInt(tag, vr, (int) Math.round(dVal));
			} else if (vr == VR.FD || vr == VR.FL || vr == VR.DS) {
				ds.setDouble(tag, vr, dVal);
			} else {
				ds.setString(tag, vr, String.valueOf(val));
			}
		} else if (val instanceof String) {
			// 文字列が渡ってきたが、VRが数値系の場合の安全策
			if (vr == VR.DS || vr == VR.FD || vr == VR.FL) {
				try {
					ds.setDouble(tag, vr, Double.parseDouble((String) val) * multiplier);
				} catch (NumberFormatException e) {
					ds.setString(tag, vr, (String) val);
				}
			} else if (vr == VR.IS || vr == VR.US || vr == VR.SS) {
				try {
					ds.setInt(tag, vr, (int) Math.round(Double.parseDouble((String) val) * multiplier));
				} catch (NumberFormatException e) {
					ds.setString(tag, vr, (String) val);
				}
			} else {
				ds.setString(tag, vr, (String) val);
			}
		} else {
			ds.setString(tag, vr, String.valueOf(val));
		}
	}

    private static boolean isAutoMappable(int tag) {
        // PixelData, IPP, IOP など、システムが独自に計算するタグの自動上書きを防ぐ
        return tag != Tag.PixelData && tag != Tag.ImagePositionPatient && 
               tag != Tag.ImageOrientationPatient && tag != Tag.Rows && tag != Tag.Columns;
    }

    private static boolean isTimeTag(int tag) {
        // TR, TE, TI など、秒からミリ秒への変換が必要なタグの判定
        return tag == Tag.RepetitionTime || tag == Tag.EchoTime || tag == Tag.InversionTime;
    }

    private static UID sopClassUidOf(Modality m) {
        if(m == Modality.CT) return UID.CTImageStorage;
        if(m == Modality.ST) return UID.NuclearMedicineImageStorage;
        if(m == Modality.PT) return UID.PositronEmissionTomographyImageStorage;
        return UID.MRImageStorage;
    }
}
