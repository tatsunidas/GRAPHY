package com.vis.core.fusion;

import java.util.concurrent.ConcurrentHashMap;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * @author tatsunidas
 */
public class ImagePairingEngine {

    /**
     * マスク（SEG等）をオリジナル画像の空間座標（Z軸・スライス枚数）に完全に一致するように再構成します。
     * マスクが存在しないスライスには空のByteProcessorが挿入されます。
     *
     * @param originalPrap オリジナル画像のPraparat
     * @param maskPrap     マスク画像のPraparat (SEG)
     * @param targetC      マスクの対象チャンネル (部位ごとに抽出する場合)
     * @param targetT      マスクの対象タイムフレーム
     * @return オリジナル画像とスライス数が完全に一致したSingle Stack マスクのImagePlus
     */
	public static ImagePlus alignMaskToOriginalSpace(Praparat originalPrap, int orgC, int orgT, Praparat maskPrap,
			int targetC, int targetT) {
		if (originalPrap == null || maskPrap == null)
			return null;

		int orgSlices = originalPrap.getImagePlus().getNSlices();
		int width = originalPrap.getImageWidth();
		int height = originalPrap.getImageHeight();

		ImageStack alignedMaskStack = new ImageStack(width, height);
		ConcurrentHashMap<Integer, SlideGlass> orgSlides = originalPrap.getAllSlides();
		ConcurrentHashMap<Integer, SlideGlass> maskSlides = maskPrap.getAllSlides();
		
		String maskPatID = "";
		String maskStudyUID = "";
		String maskSeriesUID = "";

		for (SlideGlass sg : maskSlides.values()) {
			if (sg != null && sg.getHeader() != null) {
				maskPatID = sg.getHeader().getString(Tag.PatientID, "");
				maskStudyUID = sg.getHeader().getString(Tag.StudyInstanceUID, "");
				maskSeriesUID = sg.getHeader().getString(Tag.SeriesInstanceUID, "");
				break;
			}
		}

		// 法線ベクトル（Z軸方向）を計算するためのIOP取得
		// ★ 修正: ループで確実にヘッダーを持つスライドを探す（空きマス対応）
		SlideGlass orgFirstSg = null;
		for (SlideGlass sg : orgSlides.values()) {
			if (sg != null && sg.getHeader() != null) {
				orgFirstSg = sg;
				break;
			}
		}
		if (orgFirstSg == null) {
			Log.logger.warning("All original slides are blank (no metadata). Cannot align mask.");
			return null;
		}
		
		int firstOrgFrameIdx = orgFirstSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
		double[] iop = getSafeIOP(orgFirstSg.getHeader(), firstOrgFrameIdx);

		double nx = 0, ny = 0, nz = 1; // Fallback
		if (iop != null && iop.length == 6) {
			nx = iop[1] * iop[5] - iop[2] * iop[4];
			ny = iop[2] * iop[3] - iop[0] * iop[5];
			nz = iop[0] * iop[4] - iop[1] * iop[3];
		}

		// オリジナル画像の全スライス(Z)を基準にループ
		for (int z = 0; z < orgSlices; z++) {
			int orgZCTIndex = originalPrap.calcZctIndex(new int[] { z, orgC, orgT });
			SlideGlass orgSg = originalPrap.getSlideGlassAt(orgZCTIndex);

			ImageProcessor matchedProcessor = null;
			double[] orgIpp = null;
			int orgInstNo = z + 1; // フォールバック用のインスタンス番号
			String orgSopUid = null;

			// ★ 修正: orgSg が null、またはメタデータを持たない空きマスの場合は例外を投げず安全にスキップ（パディング）する
			if (orgSg != null && orgSg.getHeader() != null) {
				orgSopUid = orgSg.getSOPInstanceUID();
				int orgFrameIdx = orgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
				orgInstNo = orgSg.getHeader().getInt(Tag.InstanceNumber, z + 1);
				
				orgIpp = getSafeIPP(orgSg.getHeader(), orgFrameIdx);
				double orgZPos = (orgIpp != null) ? (orgIpp[0] * nx + orgIpp[1] * ny + orgIpp[2] * nz) : z;

				// 【ハイブリッド方式】マスク側から該当するスライスを探索
				SlideGlass matchedMaskSg = findMatchingMaskSlide(maskSlides, maskPrap, orgSopUid, orgZPos, targetC,
						targetT, nx, ny, nz);

				if (matchedMaskSg != null && matchedMaskSg.getDicomImage().ensurePixelDataLoaded()) {
					int maskFrameIdx = matchedMaskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
					matchedProcessor = matchedMaskSg.getDicomImage().getImageProcessor(maskFrameIdx);
				}
			} else {
				// ★ ここが一番の修正ポイント！
				// orgSg が存在しない（Empty）ということは、このZ位置のこのチャンネルには画像がないということ。
				// つまり、合わせるべきマスクも「空」にするのが正しい挙動。
				Log.logger.fine("Original slide at Z=" + z + " is empty. Skipping mask alignment for this slice.");
			}

			// マッチするマスクが無い、またはオリジナルが空の場合は、空のプロセッサを生成してパディング（穴埋め）
			if (matchedProcessor == null) {
				matchedProcessor = new ByteProcessor(width, height); // 全て0（黒）の空きマス
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append("\n");//DICOMToolsの仕様に合わせる
			
			// 1. マスク画像の共通メタデータ (PatientID, Study, Series)
			sb.append("0010,0020: ").append(maskPatID).append("\n");
			sb.append("0020,000D: ").append(maskStudyUID).append("\n");
			sb.append("0020,000E: ").append(maskSeriesUID).append("\n");
			
			// 2. 新規生成するUID
			String newSopUid = com.vis.dicom.UIDUtils.createUID();
			sb.append("0008,0018: ").append(newSopUid).append("\n");
			
			// 3. 【最重要】オリジナル画像の番号と空間座標に「完全に同期」させる
			sb.append("0020,0013: ").append(orgInstNo).append("\n");
			
			if (orgIpp != null) {
				sb.append("0020,0032: ")
				  .append(orgIpp[0]).append("\\")
				  .append(orgIpp[1]).append("\\")
				  .append(orgIpp[2]).append("\n");
			}
			if (iop != null && iop.length == 6) {
				sb.append("0020,0037: ")
				  .append(iop[0]).append("\\").append(iop[1]).append("\\").append(iop[2]).append("\\")
				  .append(iop[3]).append("\\").append(iop[4]).append("\\").append(iop[5]).append("\n");
			}
			String sliceLabel = sb.toString();
			alignedMaskStack.addSlice(sliceLabel, matchedProcessor);
		}
		
		ImagePlus alignedMaskImp = new ImagePlus("Aligned_Mask", alignedMaskStack);
		
		if (orgSlices == 1) {
			String firstSliceMeta = alignedMaskStack.getSliceLabel(1);
			if (firstSliceMeta != null) {
				alignedMaskImp.setProperty("Info", firstSliceMeta);
			}
		}
		
		alignedMaskImp.setDimensions(1, alignedMaskStack.getSize(), 1);
		alignedMaskImp.setOpenAsHyperStack(false);
		alignedMaskImp.copyScale(originalPrap.getImagePlus(1,1)); // 安全のためチャンネル1・フレーム1からスケールを取る
		return alignedMaskImp;
	}

    // --- 以下、元のまま ---

    private static SlideGlass findMatchingMaskSlide(ConcurrentHashMap<Integer, SlideGlass> maskSlides, Praparat maskPrap, 
                                                    String targetSopUid, double targetZPos, 
                                                    int targetC, int targetT, double nx, double ny, double nz) {
        double EPSILON = 1e-3; 
        
        for (Integer idx : maskSlides.keySet()) {
            int[] zct = maskPrap.calcZCTArrayFromIndex(idx);
            if (zct[1] != targetC || zct[2] != targetT) continue;

            SlideGlass maskSg = maskSlides.get(idx);
            if (maskSg == null) continue;

            int maskFrameIdx = maskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;

            String refSopUid = getReferencedSopInstanceUid(maskSg.getHeader(), maskFrameIdx);
            if (targetSopUid != null && targetSopUid.equals(refSopUid)) {
                return maskSg; 
            }

            double[] maskIpp = getSafeIPP(maskSg.getHeader(), maskFrameIdx);
            if (maskIpp != null) {
                double maskZPos = (maskIpp[0]*nx + maskIpp[1]*ny + maskIpp[2]*nz);
                if (Math.abs(maskZPos - targetZPos) < EPSILON) {
                    return maskSg; 
                }
            }
        }
        return null; 
    }

    private static String getReferencedSopInstanceUid(DicomObject header, int frameIndex) {
        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
        if (perFrameSeq != null) {
            DicomObject derivationSeq = perFrameSeq.getNestedDataset(Tag.DerivationImageSequence, 0);
            if (derivationSeq != null) {
                DicomObject sourceImageSeq = derivationSeq.getNestedDataset(Tag.SourceImageSequence, 0);
                if (sourceImageSeq != null) {
                    String uid = sourceImageSeq.getString(Tag.ReferencedSOPInstanceUID);
                    if (uid != null && !uid.isEmpty()) {
                        return uid;
                    }
                }
            }
        }
        return null;
    }

    private static double[] getSafeIPP(DicomObject header, int frameIndex) {
        double[] ipp = header.getDoubles(Tag.ImagePositionPatient);
        if (ipp != null && ipp.length == 3) return ipp;

        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
        if (perFrameSeq != null) {
            DicomObject planePosSeq = perFrameSeq.getNestedDataset(Tag.PlanePositionSequence, 0);
            if (planePosSeq != null) {
                return planePosSeq.getDoubles(Tag.ImagePositionPatient);
            }
        }
        return null;
    }

    private static double[] getSafeIOP(DicomObject header, int frameIndex) {
        double[] iop = header.getDoubles(Tag.ImageOrientationPatient);
        if (iop != null && iop.length == 6) return iop;

        DicomObject sharedSeq = header.getNestedDataset(Tag.SharedFunctionalGroupsSequence, 0);
        if (sharedSeq != null) {
            DicomObject planeOriSeq = sharedSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
            if (planeOriSeq != null) {
                iop = planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
                if (iop != null && iop.length == 6) return iop;
            }
        }

        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
        if (perFrameSeq != null) {
            DicomObject planeOriSeq = perFrameSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
            if (planeOriSeq != null) {
                return planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
            }
        }
        return null;
    }
}