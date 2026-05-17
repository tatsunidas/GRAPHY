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

public class ImagePairingEngine {

    /**
     * マスク（SEG等）をオリジナル画像の空間座標（Z軸・スライス枚数）に完全に一致するように再構成します。
     * マスクが存在しないスライスには空のByteProcessorが挿入されます。
     *
     * @param originalPrap オリジナル画像のPraparat
     * @param maskPrap     マスク画像のPraparat (SEG)
     * @param targetC      マスクの対象チャンネル (部位ごとに抽出する場合)
     * @param targetT      マスクの対象タイムフレーム
     * @return オリジナル画像とスライス数が完全に一致したマスクのImagePlus
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

		// 法線ベクトル（Z軸方向）を計算するためのIOP取得
		SlideGlass orgFirstSg = orgSlides.get(0);
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
			String sliceLabel = "Empty_Mask";

			if (orgSg != null) {
				String orgSopUid = orgSg.getSOPInstanceUID();
				int orgFrameIdx = orgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;

				double[] orgIpp = getSafeIPP(orgSg.getHeader(), orgFrameIdx);
				double orgZPos = (orgIpp != null) ? (orgIpp[0] * nx + orgIpp[1] * ny + orgIpp[2] * nz) : z;

				// 【ハイブリッド方式】マスク側から該当するスライスを探索
				SlideGlass matchedMaskSg = findMatchingMaskSlide(maskSlides, maskPrap, orgSopUid, orgZPos, targetC,
						targetT, nx, ny, nz);

				if (matchedMaskSg != null && matchedMaskSg.getDicomImage().ensurePixelDataLoaded()) {
					int maskFrameIdx = matchedMaskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
					matchedProcessor = matchedMaskSg.getDicomImage().getImageProcessor(maskFrameIdx);
					sliceLabel = "Matched_Mask_" + orgSopUid;
				}
			} else {
				throw new IllegalArgumentException("This praparat is blank. Please load images first.");
			}

			// マッチするマスクが無い場合、空のプロセッサを生成してパディング（穴埋め）
			if (matchedProcessor == null) {
				matchedProcessor = new ByteProcessor(width, height); // 全て0（黒）の空きマス
			}

			alignedMaskStack.addSlice(sliceLabel, matchedProcessor);
		}

		ImagePlus alignedMaskImp = new ImagePlus("Aligned_Mask", alignedMaskStack);
		alignedMaskImp.setCalibration(originalPrap.getImagePlus().getCalibration());
		return alignedMaskImp;
	}

    /**
     * 【ハイブリッド方式】規格準拠のUIDマッチングを優先し、フォールバックとして空間座標（IPP）マッチングを行います。
     */
    private static SlideGlass findMatchingMaskSlide(ConcurrentHashMap<Integer, SlideGlass> maskSlides, Praparat maskPrap, 
                                                    String targetSopUid, double targetZPos, 
                                                    int targetC, int targetT, double nx, double ny, double nz) {
        double EPSILON = 1e-3; // 許容誤差 (0.001mm)
        
        for (Integer idx : maskSlides.keySet()) {
            int[] zct = maskPrap.calcZCTArrayFromIndex(idx);
            if (zct[1] != targetC || zct[2] != targetT) continue;

            SlideGlass maskSg = maskSlides.get(idx);
            if (maskSg == null) continue;

            int maskFrameIdx = maskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;

            // ==========================================
            // Step 1: 参照SOP Instance UIDによる完全マッチング
            // ==========================================
            String refSopUid = getReferencedSopInstanceUid(maskSg.getHeader(), maskFrameIdx);
            if (targetSopUid != null && targetSopUid.equals(refSopUid)) {
                return maskSg; // 規格準拠の完全一致！
            }

            // ==========================================
            // Step 2: 空間座標(IPP)と法線ベクトルによる位置マッチング
            // ==========================================
            double[] maskIpp = getSafeIPP(maskSg.getHeader(), maskFrameIdx);
            if (maskIpp != null) {
                double maskZPos = (maskIpp[0]*nx + maskIpp[1]*ny + maskIpp[2]*nz);
                if (Math.abs(maskZPos - targetZPos) < EPSILON) {
                    return maskSg; // 空間座標による一致（フォールバック成功）
                }
            }
        }
        
        // Step 3: どちらも無ければ「このスライスにはマスクが存在しない」
        return null; 
    }

    /**
     * DICOM SEGの複雑なシーケンス階層から、特定のフレームが参照している元画像のSOP Instance UIDを安全に抽出します。
     */
    private static String getReferencedSopInstanceUid(DicomObject header, int frameIndex) {
        // DICOM SEGの標準的な格納場所: 
        // PerFrameFunctionalGroupsSequence -> DerivationImageSequence -> SourceImageSequence -> ReferencedSOPInstanceUID
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

    /**
     * Praparat互換：DICOMの階層から安全にImage Position (Patient)を抽出します。
     */
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

    /**
     * Praparat互換：DICOMの階層から安全にImage Orientation (Patient)を抽出します。
     */
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