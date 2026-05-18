/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.dicom.image;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.TagUtils;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;

import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.util.Tools;

/**
 * 
 * @author tatsunidas
 *
 */
public class GDicomTools extends ij.util.DicomTools {
	
	/**
	 * Stack index is an order of ImageStack.
	 * Stack index is calculate c-z-t based.
	 * z,c,t are 1-based.
	 */
	public static int getStackIndex(ImagePlus imp, int z, int c, int t) {
		if (imp != null && imp.isHyperStack()) {
			// 1-base
			int validC = Math.max(1, Math.min(c, imp.getNChannels()));
			int validZ = Math.max(1, Math.min(z, imp.getNSlices()));
			int validT = Math.max(1, Math.min(t, imp.getNFrames()));
			return imp.getStackIndex(validC, validZ, validT);
		}
		// 単純なスタックの場合はそのままZを返す
		return z;
	}

	public static String getTag(ImagePlus imp, String tag/* gggg,eeee */) {
		if (imp == null)
			return null;
		/*
		 * getCurrentSlice return zct index(=stack index). 
		 */
		int stackIndex = imp.getCurrentSlice();
		int[] czt = imp.convertIndexToPosition(stackIndex);
		return getTag(imp, czt[1], czt[0], czt[2], tag);
	}

	/**
	 * 
	 * @param imp
	 * @param zct : stack index
	 * @param tag
	 * @return
	 */
	public static String getTag(ImagePlus imp, int zct/* 1 to N */, String tag/* gggg,eeee */) {
		if (imp == null)
			return null;
		return getTag(imp, zct, new String[] { tag });
	}

	/**
	 * @param imp ImagePlus
	 * @param tag 32-bit DICOM Tag (e.g.,: 0x00080060)
	 * @return value string or null
	 */
	public static String getTag(ImagePlus imp, int tag) {
		String tagString = TagUtils.toString(tag);
		tagString = tagString.substring(1,10);
		return getTag(imp, tagString);
	}
	
	public static String getTag(ImagePlus imp, int z, int c, int t, int tag) {
		String tagStr = TagUtils.toString(tag);
		tagStr = tagStr.substring(1,10);
		return getTag(imp, z, c, t, new String[] { tagStr });
	}
	
	public static String getTag(ImagePlus imp, int z, int c, int t, String tag) {
		return getTag(imp, z, c, t, new String[] { tag });
	}

	/**
	 * 階層（シーケンス）のパスを指定してDICOMタグの値を取得する
	 * 
	 * @param imp  ImagePlus
	 * @param zct  Stack index
	 * @param tags タグの階層配列 (例: {"5200,9230", "0018,9117", "0018,9087"})
	 * @return 取得した値の文字列。見つからない場合は null
	 */
	public static String getTag(ImagePlus imp, int zct/* 1 to N */, String[] tags) {
		if(imp == null) {
			return null;
		}
		int czt[] = imp.convertIndexToPosition(zct);
		return getTag(imp, czt[1], czt[0], czt[2], tags);
	}
	
	/**
	 * シーケンスパスとC,Z,Tを指定してDICOMタグの値を取得する
	 */
	public static String getTag(ImagePlus imp, int z, int c, int t, String[] tags) {
		if (tags == null || tags.length == 0) {
			throw new IllegalArgumentException("Tags array cannot be null or empty.");
		}

		// 安全なZCT指定メソッドを使う
		int stackIndex = getStackIndex(imp, z, c, t);

		String headerText = null;
		if (imp != null && stackIndex >= 1 && stackIndex <= imp.getStackSize()) {
			headerText = imp.getStack().getSliceLabel(stackIndex);
		}
		if (headerText == null || headerText.trim().isEmpty()) {
			if (imp != null) {
				headerText = (String) imp.getProperty("Info");
			}
		}

		if (headerText == null || headerText.trim().isEmpty()) {
			return null;
		}

		return getTagInSequence(headerText, tags);
	}

	/**
	 * インデント('>')で表現された階層構造のテキストから、指定されたタグパスの値を抽出するヘルパーメソッド
	 */
	private static String getTagInSequence(String headerText, String[] tags) {
		String[] lines = headerText.split("\n");
		int currentTargetIndex = 0;
		int[] sequenceDepths = new int[tags.length];

		for (String line : lines) {
			if (line.trim().isEmpty())
				continue;

			// 1. 行の深さ（'>' の数）をカウントする
			int depth = 0;
			while (depth < line.length() && line.charAt(depth) == '>') {
				depth++;
			}

			// 2. 階層の抜け出し判定
			// 現在の行の深さが、探索中のシーケンスの深さ「以下」になった場合、
			// そのシーケンスから外に出たと判断し、探索ターゲットを親階層に戻す
			while (currentTargetIndex > 0 && depth <= sequenceDepths[currentTargetIndex - 1]) {
				currentTargetIndex--;
			}

			// 3. インデントを取り除いた文字列でタグを検証
			String lineContent = line.substring(depth).trim();
			String targetTag = tags[currentTargetIndex];

			// タグが見つかった場合 (例: "0018,9087: 900.0" または "0018,9117 SQ")
			if (lineContent.startsWith(targetTag)) {
				if (currentTargetIndex == tags.length - 1) {
					// 探していた最後のタグ（値を持つタグ）に到達した場合
					int colonIndex = lineContent.indexOf(":");
					if (colonIndex != -1) {
						return lineContent.substring(colonIndex + 1).trim();
					}
					return null; // コロンがない場合は取得不能
				} else {
					// 途中のシーケンスタグを見つけた場合、深さを記録して次の階層のタグを探す
					sequenceDepths[currentTargetIndex] = depth;
					currentTargetIndex++;
				}
			}
		}

		return null; // 結局見つからなかった場合
	}

	public static Double getDouble(ImagePlus imp, int zct/* 1 to N */, String tag) {
		String value = getTag(imp, zct, tag);
		if (value == null)
			return Double.NaN;
		int index3 = value.indexOf("\\");
		if (index3 > 0)
			value = value.substring(0, index3);
		return Tools.parseDouble(value);
	}
	
	/**
	 * for HyperStack
	 * @param imp
	 * @param z
	 * @param c
	 * @param t
	 * @param tag
	 * @return
	 */
	public static double[] getDoubles(ImagePlus imp, int z, int c, int t, String tag) {
		String res = getTag(imp, z, c, t, tag);
		if (res == null) return null;
		String[] xyz = res.split("\\\\");
		double[] arr = new double[xyz.length];
		for (int i = 0; i < xyz.length; i++) {
			arr[i] = ij.util.Tools.parseDouble(xyz[i]);
		}
		return arr;
	}

	/**
	 * Single stack
	 * @param imp
	 * @param zct
	 * @param tag
	 * @return
	 */
	public static double[] getDoubles(ImagePlus imp, int zct/* 1 to N */, String tag) {
		String res = getTag(imp, zct, tag);
		if (res == null)
			return null;
		String[] xyz = res.split("\\\\");
		if (xyz == null || xyz.length < 1) {
			return null;
		}
		double[] arr = new double[xyz.length];
		for (int i = 0; i < xyz.length; i++) {
			arr[i] = Tools.parseDouble(xyz[i]);// can keep minus in case of -0.0.
		}
		return arr;
	}

	public static double[] getDoubles(ImagePlus imp, String tag) {
		return getDoubles(imp, imp.getCurrentSlice(), tag);
	}
	
	public static void setTag(ImagePlus imp, int zct/* 1 to N */, int tag/* only one tag */, String value) {
		/*
		 * DICOM tag is hex, be keep upper case.
		 */
		String tagStr = TagUtils.toString(tag);
		if(tagStr != null) {
			//(gggg,eeee)
			tagStr = tagStr.substring(1, 10);
			setTag(imp, zct, tagStr, value);
		}
	}

	/**
	 * 既存コードとの互換性用（ルート階層のタグの更新・追加）
	 */
	public static void setTag(ImagePlus imp, int zct/* 1 to N */, String tag, String value) {
		setTag(imp, zct, new String[] { tag }, value);
	}
	
	/**
	 * For simple stack
	 */
	public static void setTag(ImagePlus imp, int zct, String[] tags, String value) {
		if(imp == null) {
			return;
		}
		int[] czt = imp.convertIndexToPosition(zct);
		setTag(imp, czt[1], czt[0], czt[2], tags, value);
	}
	
	/*
	 * zct 1-based of each.
	 */
	public static void setTag(ImagePlus imp, int z ,int c, int t, int tag/* only one tag */, String value) {
		/*
		 * DICOM tag is hex, be keep upper case.
		 */
		String tagStr = TagUtils.toString(tag);
		int zct = getStackIndex(imp, z, c, t);
		if(tagStr != null) {
			//(gggg,eeee)
			tagStr = tagStr.substring(1, 10);
			setTag(imp, zct, tagStr, value);
		}
	}

	/**
	 * 
	 */
	public static void setTag(ImagePlus imp, int z, int c, int t, String[] tags, String value) {
		if (imp == null || tags == null || tags.length == 0)
			return;
			
		// ★ 安全なZCT指定メソッドを使う
		int realPos = getStackIndex(imp, z, c, t);
		boolean isStack = imp.getStackSize() > 1;
		
		String hdr = isStack ? imp.getStack().getSliceLabel(realPos) : (String) imp.getProperty("Info");

		if (hdr == null) hdr = "";
		if (hdr.startsWith("\n")) hdr = hdr.substring(1);

		String newHdr;
		if (tags.length == 1) {
			newHdr = updateOrAddTagFlat(hdr, tags[0], value);
		} else {
			newHdr = updateOrAddTagSequence(hdr, tags, value);
		}

		if (isStack) {
			imp.getStack().setSliceLabel(newHdr, realPos);
		} else {
			imp.setProperty("Info", newHdr);
		}
	}
	
	public static void setTag(ImagePlus imp, int z, int c, int t, String tag, String value) {
		setTag(imp, z, c, t, new String[] { tag }, value);
	}
	
	public static void setDoubles(ImagePlus imp, int z, int c, int t, String tag, double[] values) {
		String arr = "";
		for (double v : values) {
			arr += String.valueOf(v) + "\\";
		}
		// delete end of "\\"
		arr = arr.substring(0, arr.lastIndexOf('\\'));
		setTag(imp, z, c, t, tag, arr);
	}

	public static void setDoubles(ImagePlus imp, int zct, String tag, double[] values) {
		String arr = "";
		for (double v : values) {
			arr += String.valueOf(v) + "\\";
		}
		// delete end of "\\"
		arr = arr.substring(0, arr.lastIndexOf('\\'));
		setTag(imp, zct, tag, arr);
	}

	/**
	 * 単一タグ（ルート階層）の更新・追記処理
	 */
	private static String updateOrAddTagFlat(String headerText, String targetTag, String value) {
		String[] lines = headerText.split("\n");
		StringBuilder sb = new StringBuilder();
		boolean found = false;

		for (String line : lines) {
			if (line.trim().isEmpty())
				continue;

			// '>' で始まる行（シーケンス内のタグ）は無視し、ルート階層だけを対象にする
			if (!found && !line.trim().startsWith(">") && line.trim().startsWith(targetTag)) {
				sb.append(targetTag).append(": ").append(value).append("\n");
				found = true;
			} else {
				sb.append(line).append("\n");
			}
		}

		// 見つからなかった場合は末尾に追記
		if (!found) {
			sb.append(targetTag).append(": ").append(value).append("\n");
		}
		return sb.toString();
	}

	/**
	 * シーケンス階層を含むタグの更新・追記処理（状態遷移アルゴリズム）
	 */
	private static String updateOrAddTagSequence(String headerText, String[] tags, String value) {
		String[] lines = headerText.split("\n");
		StringBuilder sb = new StringBuilder();

		int currentTargetIndex = 0;
		int[] sequenceDepths = new int[tags.length];
		boolean found = false;

		for (String line : lines) {
			if (line.trim().isEmpty())
				continue;

			int depth = 0;
			while (depth < line.length() && line.charAt(depth) == '>') {
				depth++;
			}

			// --- 階層の抜け出し判定と、未発見タグの【挿入】処理 ---
			if (!found && currentTargetIndex > 0 && depth <= sequenceDepths[currentTargetIndex - 1]) {
				// 目的のシーケンス内にいたのに、タグが見つからないままシーケンスの外に出ようとしている場合
				if (currentTargetIndex == tags.length - 1) {
					// ここでタグを挿入する！
					String indent = "";
					for (int j = 0; j < sequenceDepths[currentTargetIndex - 1] + 1; j++)
						indent += ">";
					if (!indent.isEmpty())
						indent += " ";

					sb.append(indent).append(tags[currentTargetIndex]).append(": ").append(value).append("\n");
					found = true;
				}
				// 探索階層を親に戻す
				while (currentTargetIndex > 0 && depth <= sequenceDepths[currentTargetIndex - 1]) {
					currentTargetIndex--;
				}
			}

			String lineContent = line.substring(depth).trim();

			// --- 目的のタグ（または中継するシーケンスタグ）の探索と【上書き】処理 ---
			if (!found && currentTargetIndex < tags.length) {
				String targetTag = tags[currentTargetIndex];

				if (lineContent.startsWith(targetTag)) {
					if (currentTargetIndex == tags.length - 1) {
						// 目的の最終タグを発見！行を新しい値で上書きする
						String indentStr = line.substring(0, line.indexOf(targetTag));
						line = indentStr + targetTag + ": " + value;
						found = true;
					} else {
						// 中継地点となるシーケンスタグを発見。深さを記録して次へ。
						sequenceDepths[currentTargetIndex] = depth;
						currentTargetIndex++;
					}
				}
			}

			sb.append(line).append("\n");
		}

		// --- ループ終了後、まだ追加できていない場合の【完全新規追加】処理 ---
		if (!found) {
			if (currentTargetIndex == tags.length - 1) {
				// 親シーケンス内に入ったままテキストが終了した場合
				String indent = "";
				for (int j = 0; j < sequenceDepths[currentTargetIndex - 1] + 1; j++)
					indent += ">";
				if (!indent.isEmpty())
					indent += " ";
				sb.append(indent).append(tags[currentTargetIndex]).append(": ").append(value).append("\n");
			} else {
				// そもそも親のシーケンスすら存在しなかった場合、末尾にシーケンス構造ごと新規作成する
				for (int i = currentTargetIndex; i < tags.length; i++) {
					String indent = "";
					for (int j = 0; j < i; j++)
						indent += ">";
					if (!indent.isEmpty())
						indent += " ";

					if (i == tags.length - 1) {
						sb.append(indent).append(tags[i]).append(": ").append(value).append("\n");
					} else {
						sb.append(indent).append(tags[i]).append("  SQ (Sequence)\n");
						sb.append(indent).append(" Item #1\n");
					}
				}
			}
		}

		return sb.toString();
	}

	public static double getVoxelDepth(DicomObject header) {
		double spacingBetweenSlices = header.getDouble(Tag.Spacing​Between​Slices, Double.NaN);
		double sliceThickness = header.getDouble(Tag.Slice​Thickness, Double.NaN);
		if (!Double.isNaN(spacingBetweenSlices)) {
			return spacingBetweenSlices;// prior
		}
		if (!Double.isNaN(sliceThickness)) {
			return sliceThickness;
		}
		return 1d;
	}

	public static double getVoxelDepth(ImagePlus imp) {
		double z = imp.getCalibration().pixelDepth;
		// 共通タグは1枚目(Z=1, C=1, T=1)から取得
		int zct_1 = getStackIndex(imp, 1, 1, 1);
		double spacingBetweenSlices = getDouble(imp, zct_1, "0018,0088");
		double sliceThickness = getDouble(imp, zct_1, "0018,0050");

		if (imp.getNSlices() > 1) {
			// 確実に「Z=1」と「Z=2」の座標を比較するように ZCT 指定メソッドを使用
			double[] ipp1 = getImagePositionPatient(imp, 1, 1, 1);
			double[] ipp2 = getImagePositionPatient(imp, 2, 1, 1);
			double[] iop = getImageOrientationPatient(imp, 1, 1, 1);

			if (iop == null || iop.length != 6) {
				throw new IllegalArgumentException("ImageOrientationPatient must be a non-null 6-element array.");
			}
			if (ipp1 == null || ipp1.length != 3) {
				throw new IllegalArgumentException("ImagePositionPatient1 must be a non-null 3-element array.");
			}
			if (ipp2 == null || ipp2.length != 3) {
				throw new IllegalArgumentException("ImagePositionPatient2 must be a non-null 3-element array.");
			}

			Vector3d rowVector = new Vector3d(iop[0], iop[1], iop[2]);
			Vector3d colVector = new Vector3d(iop[3], iop[4], iop[5]);

			Vector3d normalVector = new Vector3d();
			rowVector.cross(colVector, normalVector); // normalVector = rowVector x colVector

			double norm = normalVector.length();

			// JOML default error
			final double EPSILON = 1e-9;
			if (norm < EPSILON) {
				throw new ArithmeticException(
						"Normal vector derived from ImageOrientationPatient is close to zero (length < " + EPSILON
								+ "). Cannot calculate distance reliably.");
			}

			Vector3d pos1 = new Vector3d(ipp1);
			Vector3d pos2 = new Vector3d(ipp2);

			// (P2 - P1)
			Vector3d positionDifferenceVector = new Vector3d();
			pos2.sub(pos1, positionDifferenceVector); // positionDifferenceVector = pos2 - pos1

			double dotProduct = positionDifferenceVector.dot(normalVector);

			// |(P2 - P1)・N| / |N|
			double distance = Math.abs(dotProduct) / norm;

			// if zero, output error message
			if (distance <= EPSILON) {
				String msg = "Voxel depth is close to zero (distance < " + distance
						+ "). Please check Image orientation patient slice by slice.";
				msg += "ipp1:" + Arrays.toString(ipp1) + "\n";
				msg += "ipp2:" + Arrays.toString(ipp2) + "\n";
				msg += "Now, will return spacingBetweenSlices instead.";
				Log.logger.log(Level.WARNING, msg);
				return spacingBetweenSlices;
			}

			return distance;
		}

		if (!Double.isNaN(spacingBetweenSlices)) {
			return spacingBetweenSlices;// prior
		}
		if (!Double.isNaN(sliceThickness)) {
			return sliceThickness;
		}
		return z;
	}

	/**
	 * 
	 * @param stack
	 * @param zct : from 1 to n, StackIndex.
	 * @return
	 */
	public static String getHeader(ImageStack stack, int zct) {
		String hdr = stack.getSliceLabel(zct);
		if ((hdr == null || hdr.length() < 100) && stack.isVirtual()) {
			String dir = ((VirtualStack) stack).getDirectory();
			String name = ((VirtualStack) stack).getFileName(zct);
			ImagePlus reader = new ImagePlus(dir + name);
			hdr = reader.getInfoProperty();
			if (hdr != null)
				hdr = name + "\n" + hdr;
		}
		return hdr;
	}

	public static void headerCopy(ImagePlus from, ImagePlus to) {
		// ★ 1. 次元（C, Z, T）と全体サイズが完全に一致しているかを厳密にチェック
		if (from.getNChannels() != to.getNChannels() || 
			from.getNSlices() != to.getNSlices() || 
			from.getNFrames() != to.getNFrames() ||
			from.getStackSize() != to.getStackSize()) {
			Log.logger.info("Can not copy header, not matching dimensions or stack sizes.");
			return;
		}

		to.setProperty("Info", from.getInfoProperty());
		
		int cTotal = from.getNChannels();
		int zTotal = from.getNSlices();
		int tTotal = from.getNFrames();
		
		if (from.isHyperStack() || cTotal > 1 || zTotal > 1 || tTotal > 1) {
			to.setDimensions(cTotal, zTotal, tTotal);
			to.setOpenAsHyperStack(from.getOpenAsHyperStack());
		}

		// ZCTを明示して処理する
		for (int t = 1; t <= tTotal; t++) {
			for (int z = 1; z <= zTotal; z++) {
				for (int c = 1; c <= cTotal; c++) {
					// ImageJのAPIに「C, Z, T」を渡して、stack index (実際の1Dインデックス)を安全に取得
					int index = from.getStackIndex(c, z, t);
					String hdr = from.getStack().getSliceLabel(index);
					to.getStack().setSliceLabel(hdr, index);
				}
			}
		}
	}

	public static String getHeaderAsString(DicomObject header, StringBuilder sb, int depth) {

		if (header == null)
			return null;

		int[] tags = header.tags();
		// DICOMの仕様通りタグ番号順に並べる
		java.util.Arrays.sort(tags);

		for (int tag : tags) {
			// ピクセルデータ（巨大バイナリ）はスキップ
			if (tag == Tag.PixelData || tag == Tag.FloatPixelData || tag == Tag.DoubleFloatPixelData) {
				continue;
			}

			// 階層に応じたインデント（> ）を作成
			String indent = "";
			for (int i = 0; i < depth; i++)
				indent += ">";
			if (depth > 0)
				indent += " ";
			
			String tagStr = TagUtils.toString(tag);
			if(tagStr == null) continue;
			tagStr = tagStr.substring(1,10);

			VR vr = header.getVROn(tag);
			if (vr == VR.SQ) {
				// シーケンスの場合：タグ名だけ出力して中身を再帰処理
				sb.append(indent).append(tagStr).append("  SQ (Sequence)\n");
				DicomObject seq = header.getNestedDataset(tag);
				if (seq != null) {
					for (int i = 0; i < seq.tags().length; i++) {
						sb.append(indent).append(" Item #").append(i + 1).append("\n");
						getHeaderAsString(seq, sb, depth + 1);
					}
				}
			} else {
				// ★修正箇所：配列タグの場合は "\" で結合し、単一値の場合はそのまま取得する
				String valueStr = "";
				String[] vals = header.getStrings(tag);

				if (vals != null && vals.length > 0) {
					StringBuilder valSb = new StringBuilder();
					for (int k = 0; k < vals.length; k++) {
						valSb.append(vals[k]);
						if (k < vals.length - 1) {
							valSb.append("\\");
						}
					}
					valueStr = valSb.toString();
				} else {
					String val = header.getString(tag);
					if (val != null)
						valueStr = val;
				}

				// ImageJ互換フォーマット: "gggg,eeee: value"
				if (!valueStr.isEmpty()) {
					sb.append(indent).append(tagStr).append(": ").append(valueStr).append("\n");
				}
			}
		}
		return sb.toString();
	}

	public static ImagePlus dcmImgToImagePlus(DicomImage dcmImg, Calibration cal) {
		if (!dcmImg.isMultiFrame()) {
			// --- Single Frame ---
			ImagePlus imp = new ImagePlus("", dcmImg.getRawImageProcessor(0).duplicate());

			// ヘッダー情報の文字列を一括生成
			String headerInfo = getHeaderAsString(dcmImg.getHeader(), new StringBuilder(), 0);

			// プロパティにセット (これで getInfoProperty() で取得できるようになります)
			if (headerInfo.length() > 0) {
				imp.setProperty("Info", headerInfo);
			}

			if (cal != null) {
				imp.setCalibration(cal);
			}
			return imp;

		} else {
			// --- Multi Frame (Stack) ---
			int size = dcmImg.getNumOfFrames();
			ImageStack stack = new ImageStack();

			// 最初のフレームのヘッダー情報を保持しておく（ImagePlus全体のInfoとして使うため）
			String firstFrameHeader = null;
			DicomObject header = dcmImg.getHeader();
			for (int i = 0; i < size; i++) {
				// 各フレームのプロセッサを取得
				ij.process.ImageProcessor ip = dcmImg.getImageProcessor(i);

				// 必要であれば、各スライスごとのヘッダーも生成する
				String frameHeader = getHeaderAsString(header, new StringBuilder(), 0);

				// インスタンス番号などを追記したい場合
				frameHeader += TagUtils.toDicomToolsString(Tag.Instance​Number) + ": " + (i + 1) + "\n";

				stack.addSlice(frameHeader, ip); // ラベル付きでスライス追加

				// 1枚目のヘッダーを全体のInfo用として確保
				if (i == 0) {
					firstFrameHeader = frameHeader;
				}
			}

			ImagePlus newImp = new ImagePlus("", stack);

			//　ここで親のImagePlusにInfoプロパティをセット
			if (firstFrameHeader != null) {
				newImp.setProperty("Info", firstFrameHeader);
			}

			if (cal != null) {
				newImp.setCalibration(cal);
			}
			return newImp;
		}
	}

	
	public static HashMap<Integer, DicomImage> imagePlusToDcm(ImagePlus imp, boolean dealWithSecondaryCapture) {
	    if (imp == null) {
	        return null;
	    }
	    HashMap<Integer, DicomImage> images = new HashMap<>();
	    int w = imp.getWidth();
	    int h = imp.getHeight();
	    int samples = imp.getProcessor() instanceof ColorProcessor ? 3 : 1;
	    int bits = imp.isRGB() ? 8 : imp.getBitDepth();

	    // 全次元のサイズを取得
	    int cTotal = imp.getNChannels();
	    int zTotal = imp.getNSlices();
	    int tTotal = imp.getNFrames();

	    boolean isSigned = isSignedImagePlus(imp);

	    for (int t = 1; t <= tTotal; t++) {
	        for (int z = 1; z <= zTotal; z++) {
	            for (int c = 1; c <= cTotal; c++) {
	                // 1. ZCTから実際の1Dインデックスを取得 (1-based)
	                int stackIndex = imp.getStackIndex(c, z, t);
	                // 2. HashMap格納用のキー (0-based)
	                int mapKey = stackIndex - 1;

	                DicomObject core = DicomObject.newDicomObject();
	                
	                // 3. C, Z, T を明示して属性を追加する新メソッドを呼ぶ
	                addAttributes(core, stackIndex, imp, dealWithSecondaryCapture);

	                DicomImage dcmImg = DicomImage.newDicomImage(null, core, null, UID.ImplicitVRLittleEndian);
	                
	                // 4. ImageStackから直接ピクセル配列を抜く（超高速化）
	                Object pix = imp.getStack().getProcessor(stackIndex).getPixels();

	                // 5. 符号付き(Signed)の場合の逆シフト処理
	                if (isSigned) {
	                    if (pix instanceof short[]) {
	                        short[] originalPixels = (short[]) pix;
	                        short[] copiedPixels = new short[originalPixels.length];
	                        for (int k = 0; k < originalPixels.length; k++) {
	                            copiedPixels[k] = (short) (originalPixels[k] ^ 0x8000);
	                        }
	                        pix = copiedPixels;
	                    } else if (pix instanceof byte[]) {
	                        byte[] originalPixels = (byte[]) pix;
	                        byte[] copiedPixels = new byte[originalPixels.length];
	                        for (int k = 0; k < originalPixels.length; k++) {
	                            copiedPixels[k] = (byte) (originalPixels[k] ^ 0x80);
	                        }
	                        pix = copiedPixels;
	                    }
	                }

	                dcmImg.setPixelData(0, w, h, samples, bits, pix);
	                images.put(mapKey, dcmImg);
	            }
	        }
	    }
	    return images;
	}
	
	public static boolean isSignedImagePlus(ImagePlus imp) {
		// タグ (0028,0103) Pixel Representation を取得
	    String pixelRep = com.vis.dicom.image.GDicomTools.getTag(imp, "0028,0103");
	    if(pixelRep != null) {
			// "1" であれば Signed（符号付き）
			return "1".equals(pixelRep);
	    }
	    if(imp.getBitDepth()==8) {
	    	return isEffectivelySigned8Bit(imp);
	    }else if(imp.getBitDepth()==16) {
	    	return isEffectivelySigned16Bit(imp);
	    }
	    return false;
	}
	
	private static boolean isEffectivelySigned16Bit(ImagePlus imp) {
	    // 16 bit 画像でなければ false
	    if (imp.getBitDepth() != 16) return false;

	    ij.measure.Calibration cal = imp.getCalibration();
	    if (cal == null) return false;

	    // キャリブレーションが「Straight Line (y = a + bx)」であることを確認
	    if (cal.getFunction() == ij.measure.Calibration.STRAIGHT_LINE) {
	        double[] coefficients = cal.getCoefficients();
	        if (coefficients != null && coefficients.length >= 1) {
	            // 切片 (coefficients[0]) が -32768.0 以下であれば、
	            // ImageJ内部で Signed 16-bit 用に +32768 のシフトが行われていると判断できる
	            return coefficients[0] <= -32768.0;
	        }
	    }
	    return false;
	}
	
	private static boolean isEffectivelySigned8Bit(ImagePlus imp) {
	    // 8 bit 画像でなければ false
	    if (imp.getBitDepth() != 8) return false;

	    ij.measure.Calibration cal = imp.getCalibration();
	    if (cal == null) return false;

	    // キャリブレーションが「Straight Line (y = a + bx)」であることを確認
	    if (cal.getFunction() == ij.measure.Calibration.STRAIGHT_LINE) {
	        double[] coefficients = cal.getCoefficients();
	        if (coefficients != null && coefficients.length >= 1) {
	            // 切片 (coefficients[0]) が -128.0 以下であれば、
	            // ImageJ内部で Signed 8-bit 用に +128 のシフトが行われていると判断できる
	            return coefficients[0] <= -128.0;
	        }
	    }
	    return false;
	}

	/**
	 * Add attributes from ImagePlus to DicomObject 
	 * @param dcm
	 * @param zctIndex (same as stack index)
	 * @param imp
	 * @param dealWithSecondaryCapture
	 */
	private static void addAttributes(DicomObject dcm, int zctindex/* 1 to N */,
			ImagePlus imp/* should be set current processor */, boolean dealWithSecondaryCapture) {

		int[] czt = imp.convertIndexToPosition(zctindex);
		addAttributes(dcm, czt[1], czt[0], czt[2], imp, dealWithSecondaryCapture);
	}
	
	private static void addAttributes(DicomObject dcm, int z, int c, int t,
			ImagePlus imp, boolean dealWithSecondaryCapture) {

		// UI更新を伴わず、サイレントに現在位置を移動させる（キャリブレーション取得等のために安全）
		imp.setPositionWithoutUpdate(c, z, t);

		String sopClassUID = GDicomTools.getTag(imp, z, c, t, "0008,0016");
		if(sopClassUID != null) sopClassUID = sopClassUID.trim();
		if (dealWithSecondaryCapture) {
			sopClassUID = UID.SecondaryCaptureImageStorage.uid();
		}
		
		String sopInstUID = GDicomTools.getTag(imp, z, c, t, "0008,0018");
		if(sopInstUID != null) sopInstUID = sopInstUID.trim();
		if (sopInstUID == null || sopInstUID.length() == 0) {
			sopInstUID = UIDUtils.createUID();
		}
		setString(dcm, Tag.SOPClassUID, sopClassUID);
		setString(dcm, Tag.SOPInstanceUID, sopInstUID);

		String patID = GDicomTools.getTag(imp, z, c, t, "0010,0020");
		String patName = GDicomTools.getTag(imp, z, c, t, "0010,0010");
		String patBoD = GDicomTools.getTag(imp, z, c, t, "0010,0030");
		String patBoT = GDicomTools.getTag(imp, z, c, t, "0010,0032");
		String patSex = GDicomTools.getTag(imp, z, c, t, "0010,0040");
		String patWeight = GDicomTools.getTag(imp, z, c, t, "0010,1030");
		setString(dcm, Tag.PatientName, patName);
		setString(dcm, Tag.PatientID, patID);
		setDate(dcm, Tag.PatientBirthDate, patBoD);
		setTime(dcm, Tag.PatientBirthTime, patBoT);
		setString(dcm, Tag.PatientSex, patSex);
		setDouble(dcm, Tag.PatientWeight, patWeight);

		String studyUID = GDicomTools.getTag(imp, z, c, t, "0020,000D");
		if (studyUID == null || studyUID.trim().length() == 0) studyUID = UIDUtils.createUID();
		
		String seriesUID = GDicomTools.getTag(imp, z, c, t, "0020,000E");
		if (seriesUID == null || seriesUID.trim().length() == 0) seriesUID = UIDUtils.createUID();
		
		String refUID = GDicomTools.getTag(imp, z, c, t, "0020,0052");
		setString(dcm, Tag.StudyInstanceUID, studyUID);
		setString(dcm, Tag.SeriesInstanceUID, seriesUID);
		setString(dcm, Tag.FrameOfReferenceUID, refUID);

		String modality = GDicomTools.getTag(imp, z, c, t, "0008,0060");
		String modalities = GDicomTools.getTag(imp, z, c, t, "0008,0061");
		String manu = GDicomTools.getTag(imp, z, c, t, "0008,0070");
		String deviceNo = GDicomTools.getTag(imp, z, c, t, "0018,1000");
		String station = GDicomTools.getTag(imp, z, c, t, "0008,1010");
		String softVer = GDicomTools.getTag(imp, z, c, t, "0018,1020");
		String studyDesc = GDicomTools.getTag(imp, z, c, t, "0008,1030");
		String seriesDesc = GDicomTools.getTag(imp, z, c, t, "0008,103E");
		String modelName = GDicomTools.getTag(imp, z, c, t, "0008,1090");
		setString(dcm, Tag.Modality, modality);
		setString(dcm, Tag.ModalitiesInStudy, modalities);
		setString(dcm, Tag.Manufacturer, manu);
		setString(dcm, Tag.DeviceSerialNumber, deviceNo);
		setString(dcm, Tag.StationName, station);
		setString(dcm, Tag.SoftwareVersions, softVer);
		setString(dcm, Tag.StudyDescription, studyDesc);
		setString(dcm, Tag.SeriesDescription, seriesDesc);
		setString(dcm, Tag.ManufacturerModelName, modelName);

		String imageType = GDicomTools.getTag(imp, z, c, t, "0008,0008");
		String instanceCreationDate = GDicomTools.getTag(imp, z, c, t, "0008,0012");
		String instanceCreationTime = GDicomTools.getTag(imp, z, c, t, "0008,0013");
		String studyDate = GDicomTools.getTag(imp, z, c, t, "0008,0020");
		String seriesDate = GDicomTools.getTag(imp, z, c, t, "0008,0021");
		String acquiDate = GDicomTools.getTag(imp, z, c, t, "0008,0022");
		String contDate = GDicomTools.getTag(imp, z, c, t, "0008,0023");
		String studyTime = GDicomTools.getTag(imp, z, c, t, "0008,0030");
		String seriesTime = GDicomTools.getTag(imp, z, c, t, "0008,0031");
		String acquiTime = GDicomTools.getTag(imp, z, c, t, "0008,0032");
		String contTime = GDicomTools.getTag(imp, z, c, t, "0008,0033");
		setString(dcm, Tag.ImageType, imageType);
		setDate(dcm, Tag.InstanceCreationDate, instanceCreationDate);
		setTime(dcm, Tag.InstanceCreationTime, instanceCreationTime);
		setDate(dcm, Tag.StudyDate, studyDate);
		setDate(dcm, Tag.SeriesDate, seriesDate);
		setDate(dcm, Tag.AcquisitionDate, acquiDate);
		setDate(dcm, Tag.ContentDate, contDate);
		setTime(dcm, Tag.StudyTime, studyTime);
		setTime(dcm, Tag.SeriesTime, seriesTime);
		setTime(dcm, Tag.AcquisitionTime, acquiTime);
		setTime(dcm, Tag.ContentTime, contTime);

		String studyID = GDicomTools.getTag(imp, z, c, t, "0020,0010");
		String seriesNo = GDicomTools.getTag(imp, z, c, t, "0020,0011");
		String acquiNo = GDicomTools.getTag(imp, z, c, t, "0020,0012");
		String instNo = GDicomTools.getTag(imp, z, c, t, "0020,0013");
		String imgPosPat = GDicomTools.getTag(imp, z, c, t, "0020,0032");
		String imgOriPat = GDicomTools.getTag(imp, z, c, t, "0020,0037");
		setString(dcm, Tag.StudyID, studyID);
		setInt(dcm, Tag.SeriesNumber, seriesNo);
		setInt(dcm, Tag.AcquisitionNumber, acquiNo);
		setInt(dcm, Tag.InstanceNumber, instNo);
		setDoubles(dcm, Tag.ImagePositionPatient, imgPosPat);
		setDoubles(dcm, Tag.ImageOrientationPatient, imgOriPat);

		int samplesPerPixel = imp.getProcessor() instanceof ColorProcessor ? 3 : 1; 
		int rows = imp.getHeight();
		int cols = imp.getWidth();
		
		String pixelSpacingYX = GDicomTools.getTag(imp, z, c, t, "0028,0030");
		if (pixelSpacingYX == null) {
			Calibration cal = imp.getCalibration();
			pixelSpacingYX = cal.pixelHeight + "\\\\" + cal.pixelWidth;
		}
		
		Double pixelSpacingZ = GDicomTools.getVoxelDepth(imp);
		if (pixelSpacingZ <= 0.0) {
			pixelSpacingZ = imp.getCalibration().pixelDepth;
		}

		int bitsAllocated = samplesPerPixel == 1 ? imp.getBitDepth() : 8;
		String bitsStored = GDicomTools.getTag(imp, z, c, t, "0028,0101");
		String highBit = GDicomTools.getTag(imp, z, c, t, "0028,0102");
		boolean signed = isSignedImagePlus(imp);
		String pixelRepresentationString = signed ? "1" : "0";
		
		String intercept = GDicomTools.getTag(imp, z, c, t, "0028,1052");
		String slope = GDicomTools.getTag(imp, z, c, t, "0028,1053");
		
		if(intercept==null || slope==null) {
			Calibration cal = imp.getCalibration();
			double[] coeffs = cal.getCoefficients();
			if (coeffs != null && coeffs.length >= 2) {
				if(signed && coeffs[0] <= -32768) {
					coeffs[0] += 32768; // remove -32768 from intercept.
				}
				intercept = String.valueOf(coeffs[0]);
				slope = String.valueOf(coeffs[1]);
			}
		}
		
		setInt(dcm, Tag.SamplesPerPixel, samplesPerPixel);
		if (samplesPerPixel == 3) {
			setInt(dcm, Tag.BitsAllocated, 8);
			setInt(dcm, Tag.BitsStored, 8);
			setInt(dcm, Tag.HighBit, 7);
			setString(dcm, Tag.PhotometricInterpretation, "RGB");
			setInt(dcm, Tag.PlanarConfiguration, 0); 
		} else {
			setString(dcm, Tag.PhotometricInterpretation, "MONOCHROME2");
		}
		
		setInt(dcm, Tag.Rows, rows);
		setInt(dcm, Tag.Columns, cols);
		setDoubles(dcm, Tag.PixelSpacing, pixelSpacingYX);
		setDouble(dcm, Tag.SpacingBetweenSlices, pixelSpacingZ);
		setInt(dcm, Tag.BitsAllocated, bitsAllocated);
		setInt(dcm, Tag.BitsStored, bitsStored);
		setInt(dcm, Tag.HighBit, highBit);
		setInt(dcm, Tag.PixelRepresentation, pixelRepresentationString);
		setDouble(dcm, Tag.RescaleIntercept, intercept);
		setDouble(dcm, Tag.RescaleSlope, slope);
	}

	static public void setString(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		dcm.setString(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setInt(DicomObject dcm, int tag, int v) {
		dcm.setInt(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setInt(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		try {
			int intV = Integer.parseInt(v);
			setInt(dcm, tag, intV);
		} catch (NumberFormatException e) {
			// do nothing
		}
	}

	static public void setDouble(DicomObject dcm, int tag, Double v) {
		if (v == null) {
			return;
		}
		dcm.setDouble(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setDouble(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		try {
			double dV = Double.parseDouble(v);
			dcm.setDouble(tag, TagDict.vrType(tag)[0], dV);
		} catch (NumberFormatException e) {
			// do nothing
		}
	}

	static public void setDoubles(DicomObject dcm, int tag, String vals) {
		if (vals == null || vals.trim().length() == 0) {
			return;
		}
		vals = vals.trim();
		try {
			String[] vals_ = vals.split("\\\\+");
			double[] array = new double[vals_.length];
			for (int j = 0; j < array.length; j++) {
				array[j] = Double.parseDouble(vals_[j].trim());
			}
			dcm.setDouble(tag, TagDict.vrType(tag)[0], array);
		} catch (NumberFormatException e) {
			// do nothing
			Log.logger.fine("setDouble was failed");
		}
	}

	static public void setDate(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
		try {
			dcm.setDate(tag, TagDict.vrType(tag)[0], sdfDate.parse(v));
		} catch (ParseException e) {
			// do nothing
			Log.logger.fine("setDate was failed");
		}
	}

	static public void setTime(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		dcm.setString(tag, TagDict.vrType(tag)[0], v);
	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int zct, Vector3d ipp) {
		if (ipp == null) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setImagePositionPatient(dcms, zct, new double[] { ipp.x(), ipp.y(), ipp.z() });
	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int zct, double[] ipp) {
		if (ipp == null || ipp.length != 3) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setDoubles(dcms, zct, "0020,0032", ipp);
	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImageOrientationPatient(ImagePlus dcms, int zct, double[] iop) {
		if (iop == null || iop.length != 6) {
			Log.logger.info("ImageOrientationPatient must have 6 component y axis(Row) : xyz, x axis(Col) : xyz");
			return;
		}
		setDoubles(dcms, zct, "0020,0037", iop);
	}

	public static void setImageOrientationPatient(ImagePlus dcms, int zct, Vector3d row, Vector3d col) {
		setImageOrientationPatient(dcms, zct, new double[] { row.x, row.y, row.z, col.x, col.y, col.z });
	}
	
	public static double[] getImagePositionPatient(ImagePlus imp, int z, int c, int t) {
		return getDoubles(imp, z, c, t, "0020,0032");
	}

	public static double[] getImageOrientationPatient(ImagePlus imp, int z, int c, int t) {
		return getDoubles(imp, z, c, t, "0020,0037");
	}

	public static double[] getImagePositionPatient(ImagePlus imp, int zct/* 1 to N */) {
		double[] ipp = getDoubles(imp, zct, "0020,0032");
		return ipp;
	}

	public static double[] getImageOrientationPatient(ImagePlus imp, int zct/* 1 to N */) {
		double[] iop = getDoubles(imp, zct, "0020,0037");
		return iop;
	}

	/**
	 * SOP Class UID をもとに、Secondary Capture や 非画像データ(KO, PR, SR等) であるかを判定する。
	 */
	public static boolean isSecondaryCaptureOrNonImage(File f) {
		String sopClassUID = DicomUtilities.getSOPClassUID(f.getAbsolutePath());
		if (sopClassUID == null)
			return true; // UIDが読めない場合は非正規として扱う
		sopClassUID = sopClassUID.trim();

		// 代表的な非画像・SC系のSOP Class UIDプレフィックス
		if (sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.7"))
			return true; // SC (Secondary Capture)
		if (sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.88"))
			return true; // SR, KO (Key Object)
		if (sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.11"))
			return true; // PR (Presentation State)
		if (sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.104"))
			return true;// Encapsulated PDF

		return false; // 上記以外は標準画像とみなす
	}

	/**
	 * シリーズを構成するファイルのリストから、代表となる1ファイルを選出する。 ・SCやKOが通常画像と混ざっている場合は、通常画像を優先して返す。
	 * ・SCやKOしか存在しない場合は、それを代表ファイルとして返す。
	 */
	public static File getRepresentativeFileOfSeries(java.util.List<File> seriesFiles,
			java.util.List<String> errorLog) {

		File fallbackFile = null;

		for (File f : seriesFiles) {
			// SCやKOなどの非標準画像かどうかのチェック
			if (isSecondaryCaptureOrNonImage(f)) {
				// SC/KOしかない場合の保険として、最初の1つをキープしておく
				if (fallbackFile == null)
					fallbackFile = f;
				continue;
			}

			// ここに到達したということは、「純粋な標準画像（CT/MR等）」！これを最優先で返す。
			return f;
		}

		// 標準画像が1枚も見つからなかったが、SC/KOは存在した場合（「それしかない」パターン）
		if (fallbackFile != null) {
			errorLog.add("通知: このシリーズには画像以外のデータ（SC/KO等）のみが存在します。抽出対象とします: " + fallbackFile.getAbsolutePath());
			return fallbackFile;
		}

		return null;
	}
}
