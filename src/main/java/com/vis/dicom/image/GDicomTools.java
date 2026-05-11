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
	 * ★ 追加：HyperStack（多次元）の場合に、スライス番号から正確な 1D インデックスを算出する
	 */
	public static int getRealIndex(ImagePlus imp, int slicePos/* 1 to N */) {
		if (imp != null && imp.isHyperStack()) {
			int c = imp.getChannel() > 0 ? imp.getChannel() : 1;
			int t = imp.getFrame() > 0 ? imp.getFrame() : 1;
			// CとTを現在の状態に固定し、目的のZスライスの1Dインデックスを取得する
			return imp.getStackIndex(c, slicePos, t);
		}
		return slicePos;
	}

	/**
	 * ImageJ標準の DicomTools.getTag はヘッダの厳格なバリデーション（PixelDataタグの有無など）を行い、
	 * 不正とみなすと強制的に1枚目のInfoを返すというバグがあるため、使用を禁止し独自パーサーに迂回させる。
	 */
	public static String getTag(ImagePlus imp, String tag/* gggg,eeee */) {
		if (imp == null)
			return null;
		// カレントスライスを取得し、独自パーサーへ
		int pos = imp.getCurrentSlice();
		return getTag(imp, pos, new String[] { tag });
	}

	public static String getTag(ImagePlus imp, int pos/* 1 to N */, String tag/* gggg,eeee */) {
		if (imp == null)
			return null;
		// ★ imp.setSlice(pos) による無駄な再描画とフリーズを防ぎつつ、独自パーサーへ直行
		return getTag(imp, pos, new String[] { tag });
	}

//	public static String getTag(ImagePlus imp, String tag/*gggg,eeee*/) {
	// SliceLabelの先頭に改行がないとInfoプロパティしか返さない。
//		String v = ij.util.DicomTools.getTag(imp, tag);
//		if(v != null) {
//			v = v.trim();
//		}
//		return v;
//	}
//	
//	public static String getTag(ImagePlus imp, int pos/*1 to N*/, String tag/*gggg,eeee*/) {
//		imp.setPosition(getRealIndex(imp, pos));
//		return getTag(imp, tag);
//	}

	/**
	 * 階層（シーケンス）のパスを指定してDICOMタグの値を取得する
	 * 
	 * @param imp  ImagePlus
	 * @param pos  スライス番号 (1 to N)
	 * @param tags タグの階層配列 (例: {"5200,9230", "0018,9117", "0018,9087"})
	 * @return 取得した値の文字列。見つからない場合は null
	 */
	public static String getTag(ImagePlus imp, int pos/* 1 to N */, String[] tags) {
		if (tags == null || tags.length == 0) {
			throw new IllegalArgumentException("Tags array cannot be null or empty.");
		}

		// 階層がない（1つだけ）の場合は、既存の単一タグ取得メソッドにフォールバック
//		if (tags.length == 1) {
//			return getTag(imp, pos, tags[0]);
//		}

		int realPos = getRealIndex(imp, pos);

		// 1. スライス固有のラベル、または全体プロパティのInfoからテキストを取得
		String headerText = null;
		if (imp != null && pos >= 1 && pos <= imp.getStackSize()) {
			headerText = imp.getStack().getSliceLabel(realPos);
		}
		if (headerText == null || headerText.trim().isEmpty()) {
			if (imp != null) {
				headerText = (String) imp.getProperty("Info");
			}
		}

		if (headerText == null || headerText.trim().isEmpty()) {
			return null;
		}

		// 2. 階層化されたテキストから値を抽出
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

	/**
	 * @param imp ImagePlus
	 * @param tag 32-bit DICOM Tag (e.g.,: 0x00080060)
	 * @return value string or null
	 */
	public static String getTag(ImagePlus imp, int tag) {
		// 上位16ビット（グループ番号）を抽出
		int group = (tag >> 16) & 0xFFFF;
		// 下位16ビット（エレメント番号）を抽出
		int element = tag & 0xFFFF;
		// "gggg,eeee" のフォーマットに変換（それぞれ4桁の16進数でゼロ埋め、小文字）
		// ※ ImageJのDicomToolsは小文字・大文字どちらでも大抵動きますが、念のため小文字の %04x にしています。
		String tagString = String.format("%04x,%04x", group, element);
		return getTag(imp, tagString);
	}

	public static Double getDouble(ImagePlus imp, int pos/* 1 to N */, String tag) {
		String value = getTag(imp, pos, tag);
		if (value == null)
			return Double.NaN;
		int index3 = value.indexOf("\\");
		if (index3 > 0)
			value = value.substring(0, index3);
		return Tools.parseDouble(value);
	}

	public static double[] getDoubles(ImagePlus imp, int pos/* 1 to N */, String tag) {
		String res = getTag(imp, pos, tag);
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

//	/**
//	 * Add / update meta data in ImagePlus.
//	 * Sequence tag are ignored.
//	 * @param imp
//	 * @param pos
//	 * @param tag = No sequence tag.
//	 * @param value
//	 */
//	public static void setTag(ImagePlus imp, int pos/*1 to N*/, String tag, String value) {
//		if(imp.getNSlices() > 1) {
//			ImageStack stack = imp.getStack();
//			String hdr = stack.getSliceLabel(pos);
//			if(hdr == null) {
//				hdr = "";
//			}
//			/*
//			 * Remove "\n" if "\n" in first line head
//			 * DicomTools.getTag() return null when hdr head "\n".
//			 */
//			if(hdr.indexOf("\n")==0) {
//				hdr = hdr.substring(1, hdr.length());
//			}
//			int index1 = hdr.indexOf(tag);
//			if (index1 != -1) {// found
//				if (hdr.charAt(index1 + 11) == '>') {
//					// ignore tags in sequences
//					index1 = hdr.indexOf(tag, index1 + 10);
//				}
//				index1 = hdr.indexOf(":", index1) + 1;
//				String upper = hdr.substring(0, index1);
//				int index2 = hdr.indexOf("\n", index1);
//				String after = hdr.substring(index2);
//				hdr = upper + value + after;
//			} else {// not found
//				if (!hdr.endsWith("\n")) {
//					hdr = hdr + "\n" + tag + ": " + value + "\n";
//				} else {
//					hdr = hdr + tag + ": " + value + "\n";
//				}
//			}
//			stack.setSliceLabel(hdr, pos);
//		}else {
//			String hdr = (String)imp.getProperty("Info");
//			if (hdr == null) {
//				hdr = "";
//			}
//			if(hdr.indexOf("\n")==0) {
//				hdr = hdr.substring(1, hdr.length());
//			}
//			int index1 = hdr.indexOf(tag);
//			if (index1 != -1 && hdr.endsWith(">")) {// found
//				if (hdr.charAt(index1 + 11) == '>') {
//					// ignore tags in sequences
//					index1 = hdr.indexOf(tag, index1 + 10);
//				}
//				index1 = hdr.indexOf(":", index1) + 1;
//				String upper = hdr.substring(0, index1);
//				int index2 = hdr.indexOf("\n", index1);
//				String after = hdr.substring(index2);
//				hdr = upper + value + after;
//			} else if(index1 != -1){ // found
//				// 行ごとに分割
//				String[] lines = hdr.split("\n");
//				// 結果を格納するためのStringBuilder
//				StringBuilder result = new StringBuilder();
//				// 各行を処理
//				for (String line : lines) {
//					if(line.length() ==0) {
//						continue;
//					}
//					// tag文字を取り出す
//					try {
//					String prefix = line.substring(0, 9);
//					String v = line.substring(11);// [gggg,eeee: ]
//					if (prefix.equals(tag)) {
//						v = value; // replace
//					}
//					result.append(prefix).append(": ").append(v).append("\n");
//					}catch(java.lang.StringIndexOutOfBoundsException e) {
//						Log.logger.severe(e.getLocalizedMessage());
//					}
//				}
//				hdr = result.toString();
//			}else {
//				if(hdr.equals("")) {
//					hdr = hdr + tag + ": " + value + "\n";
//				}else if (!hdr.endsWith("\n")) {
//					hdr = hdr + "\n" + tag + ": " + value + "\n";
//				} else {
//					hdr = hdr + tag + ": " + value + "\n";
//				}
//			}
//			imp.setProperty("Info", hdr);
//		}
//	}

	/**
	 * 既存コードとの互換性用（ルート階層のタグの更新・追加）
	 */
	public static void setTag(ImagePlus imp, int pos/* 1 to N */, String tag/* only one tag */, String value) {
		setTag(imp, pos, new String[] { tag }, value);
	}

	/**
	 * 階層（シーケンス）のパスを指定してDICOMタグの値を更新・追加する（完全版）
	 * 
	 * @param imp   ImagePlus
	 * @param pos   スライス番号 (1 to N)
	 * @param tags  タグの階層配列 (例: {"5200,9230", "0018,9117", "0018,9087"})
	 * @param value セットしたい値
	 */
	public static void setTag(ImagePlus imp, int pos, String[] tags, String value) {
		if (imp == null || tags == null || tags.length == 0)
			return;
		int realPos = getRealIndex(imp, pos);
		boolean isStack = imp.getStackSize() > 1;
		String hdr = isStack ? imp.getStack().getSliceLabel(realPos) : (String) imp.getProperty("Info");

		if (hdr == null)
			hdr = "";
		// 先頭の余分な改行をクリーンアップ
		if (hdr.startsWith("\n"))
			hdr = hdr.substring(1);

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

	public static void setDoubles(ImagePlus imp, int pos, String tag, double[] values) {
		String arr = "";
		for (double v : values) {
			arr += String.valueOf(v) + "\\";
		}
		// delete end of "\\"
		arr = arr.substring(0, arr.lastIndexOf('\\'));
		setTag(imp, pos, tag, arr);
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
		double spacingBetweenSlices = getDouble(imp, 1, "0018,0088");
		double sliceThickness = getDouble(imp, 1, "0018,0050");

		if (imp.getNSlices() > 1) {
			double[] ipp1 = getImagePositionPatient(imp, 1);
			double[] ipp2 = getImagePositionPatient(imp, 2);
			double[] iop = getImageOrientationPatient(imp, 1);

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
	 * @param RealIndex : from 1 to n, by getRealIndex()
	 * @return
	 */
	public static String getHeader(ImageStack stack, int RealIndex) {
		String hdr = stack.getSliceLabel(RealIndex);
		if ((hdr == null || hdr.length() < 100) && stack.isVirtual()) {
			String dir = ((VirtualStack) stack).getDirectory();
			String name = ((VirtualStack) stack).getFileName(RealIndex);
			ImagePlus reader = new ImagePlus(dir + name);
			hdr = reader.getInfoProperty();
			if (hdr != null)
				hdr = name + "\n" + hdr;
		}
		return hdr;
	}

	public static void headerCopy(ImagePlus from, ImagePlus to) {
		if (from.getNSlices() != to.getNSlices()) {
			Log.logger.info("Can not copy header, not matching stack sizes.");
			return;
		}
		if (from.getNSlices() == 1) {
			to.setProperty("Info", from.getInfoProperty());
		} else {
			int size = from.getNSlices();
			for (int i = 1; i <= size; i++) {
				int index = getRealIndex(from, i);
				String hdr = from.getStack().getSliceLabel(index);
				to.getStack().setSliceLabel(hdr, index);
			}
		}
	}

	/**
	 * old codes DicomObjectからImageJ形式("gggg,eeee: value\n")の文字列を一括生成する
	 */
//    private static String getDicomHeaderString(DicomObject header) {
//        StringBuilder sb = new StringBuilder();
//        int[] tags = header.tags();
//        
//        for (int t : tags) {
//            // 除外タグ
//            if (t == Tag.Pixel​Data || t == Tag.Float​​Pixel​​Data || t == Tag.Double​Float​Pixel​​Data) {
//                continue;
//            }
//            com.vis.dicom.VR vr = header.getVROn(t);
//            if (vr == com.vis.dicom.VR.SQ) {
//                continue;
//            }
//            
//            if(t == Tag.DiffusionBValue) {
//            	System.out.println();
//            }
//
//            String vmString = TagDict.vmOf(t);
//            if (vmString == null) { // private tag
//                continue;
//            }
//
//            String ts = TagUtils.toDicomToolsString(t); // "0008,0010" 形式
//            String valueStr = "";
//
//            if (vmString.equals("1")) {
//                String val = header.getString(t);
//                if (val != null) valueStr = val;
//            } else {
//                String[] vals = header.getStrings(t);
//                if (vals != null && vals.length > 0) {
//                    StringBuilder valSb = new StringBuilder();
//                    for (int k = 0; k < vals.length; k++) {
//                        valSb.append(vals[k]);
//                        if (k < vals.length - 1) {
//                            valSb.append("\\");
//                        }
//                    }
//                    valueStr = valSb.toString();
//                }
//            }
//
//            // ImageJのInfoプロパティ形式 "gggg,eeee: value\n" を構築
//            if (!valueStr.isEmpty()) {
//                sb.append(ts).append(": ").append(valueStr).append("\n");
//            }
//        }
//        return sb.toString();
//    }

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

			// タグを "gggg,eeee" 形式に変換（上位16ビットと下位16ビットをそれぞれ4桁の16進数でゼロ埋め）
			String tagStr = String.format("%04x,%04x", (tag >> 16) & 0xFFFF, tag & 0xFFFF);

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

			// 【重要】ここで親のImagePlusにInfoプロパティをセットしないとnullになります
			if (firstFrameHeader != null) {
				newImp.setProperty("Info", firstFrameHeader);
			}

			if (cal != null) {
				newImp.setCalibration(cal);
			}
			return newImp;
		}
	}

	public static ImagePlus dcmImgToImagePlusOld(DicomImage dcmImg, Calibration cal) {
		if (!dcmImg.isMultiFrame()) {
			ImagePlus imp = new ImagePlus("", dcmImg.getImageProcessor(0).duplicate());
			DicomObject header = dcmImg.getHeader();
			int[] tags = header.tags();
			for (int t : tags) {
				if (t == Tag.Pixel​Data || t == Tag.Float​​Pixel​​Data || t == Tag.Double​Float​Pixel​​Data) {
					continue;
				}
				com.vis.dicom.VR vr = header.getVROn(t);
				if (vr == com.vis.dicom.VR.SQ) {
					continue;
				}

				String ts = TagUtils.toDicomToolsString(t);
				String vmString = TagDict.vmOf(t);
				if (vmString == null) { // maybe private tag
					continue;
				}
				if (vmString.equals("1")) {
					String val = header.getString(t);
					if (val != null) {
						setTag(imp, 1, ts, val);
					}
				} else {
					String[] vals = header.getStrings(t);
					if (vals != null && vals.length > 0) {
						// 文字列連結をStringBuilderに変更（高速化・効率化）
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < vals.length; i++) {
							sb.append(vals[i]);
							if (i < vals.length - 1/* ignore appending to end of \\ */) {
								sb.append("\\");
							}
						}
						setTag(imp, 1, ts, sb.toString());
					}
				}
			}
			if (cal != null) {
				imp.setCalibration(cal);
			}
			return imp;
		} else {
			int size = dcmImg.getNumOfFrames();
			ImageStack stack = new ImageStack();
			for (int i = 0; i < size; i++) {
				/* single frame imp */
				ImagePlus imp = new ImagePlus("", dcmImg.getImageProcessor(i));
				DicomObject header = dcmImg.getHeader();
				int[] tags = header.tags();
				for (int t : tags) {
					if (t == Tag.Pixel​Data || t == Tag.Float​​Pixel​​Data || t == Tag.Double​Float​Pixel​​Data) {
						continue;
					}
					com.vis.dicom.VR vr = header.getVROn(t);
					if (vr == com.vis.dicom.VR.SQ) {
						continue;
					}

					String ts = TagUtils.toDicomToolsString(t);
					String vmString = TagDict.vmOf(t);
					if (vmString == null) { // maybe private tag
						continue;
					}
					if (vmString.equals("1")) {
						String val = header.getString(t);
						if (val != null) {
							setTag(imp, 1, ts, val);
						}
					} else {
						String[] vals = header.getStrings(t);
						if (vals != null && vals.length > 0) {
							// 文字列連結をStringBuilderに変更（高速化・効率化）
							StringBuilder sb = new StringBuilder();
							for (int q = 0; q < vals.length; q++) {
								sb.append(vals[q]);
								if (i < vals.length - 1/* ignore appending to end of \\ */) {
									sb.append("\\");
								}
							}
							setTag(imp, 1, ts, sb.toString());
						}
					}
				}
				setTag(imp, (i + 1), TagUtils.toDicomToolsString(Tag.Instance​Number), String.valueOf(i + 1));
				stack.addSlice(imp.getProcessor());
				stack.setSliceLabel(imp.getInfoProperty(), i + 1);
			}
			ImagePlus newImp = new ImagePlus("", stack);
			newImp.setCalibration(cal);
			return newImp;
		}
	}

//	public static HashMap<Integer, DicomImage> imagePlusToDcm(ImagePlus imp, boolean dealWithSecondaryCapture) {
//		if (imp == null) {
//			return null;
//		}
//		HashMap<Integer, DicomImage> images = new HashMap<>();
//		int w = imp.getWidth();
//		int h = imp.getHeight();
//		int samples = imp.getProcessor() instanceof ColorProcessor ? 3 : 1;
//		int bits = imp.isRGB() ? 8 : imp.getBitDepth();
//
//		int s = imp.getNSlices();
//
//		
//		boolean signed = isSignedImagePlus(imp);
//
//		for (int i = 0; i < s; i++) {
//			DicomObject core = DicomObject.newDicomObject();
//
//			// 注意: addAttributes 内で imp.setSlice(i + 1) をしている場合、
//			// 多次元ImagePlusでは「現在のCとT」におけるZスライスが切り替わるだけになることがあります。
//			// 先ほど GDicomTools で実装した getRealIndex などを活用し、
//			// 1Dインデックス(i + 1)から正確なスライスのメタデータを引き出せるようにしてください。
//			addAttributes(core, i, imp, dealWithSecondaryCapture);
//
//			DicomImage dcmImg = DicomImage.newDicomImage(null/* file path */, core, null/* fmi null-able */,
//					UID.ImplicitVRLittleEndian);
//			imp.setSlice(GDicomTools.getRealIndex(imp, i + 1));
//			Object pix = imp.getProcessor().getPixels();
//			if (signed) {
//				short[] originalPixels = (short[]) pix;
//				// ★ 元の配列を汚さないように、新しい配列を生成してコピー＆計算する
//				short[] copiedPixels = new short[originalPixels.length];
//				for (int k = 0; k < originalPixels.length; k++) {
//					//short processorで+32768されていた値を戻す
//					copiedPixels[k] = (short) (originalPixels[k] - (short) 32768);
//				}
//				pix = copiedPixels; // コピーした方をDICOMにセットする
//			}
//			dcmImg.setPixelData(0, w, h, samples, bits, pix);
//			images.put(i, dcmImg);
//		}
//		return images;
//	}
	
	public static HashMap<Integer, DicomImage> imagePlusToDcm(ImagePlus imp, boolean dealWithSecondaryCapture) {
	    if (imp == null) {
	        return null;
	    }
	    HashMap<Integer, DicomImage> images = new HashMap<>();
	    int w = imp.getWidth();
	    int h = imp.getHeight();
	    int samples = imp.getProcessor() instanceof ColorProcessor ? 3 : 1;
	    int bits = imp.isRGB() ? 8 : imp.getBitDepth();
	    int s = imp.getNSlices();

	    // 先ほど作成した独自の判定メソッドを使用する
	    boolean isSigned = isSignedImagePlus(imp);

	    for (int i = 0; i < s; i++) {
	        DicomObject core = DicomObject.newDicomObject();
	        addAttributes(core, i, imp, dealWithSecondaryCapture);

	        DicomImage dcmImg = DicomImage.newDicomImage(null, core, null, UID.ImplicitVRLittleEndian);
	        imp.setSlice(GDicomTools.getRealIndex(imp, i + 1));
	        
	        Object pix = imp.getProcessor().getPixels();

	        // 符号付き(Signed)の場合のみ、データ破壊を防ぐためにコピーして逆シフトする
	        if (isSigned) {
	            if (pix instanceof short[]) {
	                // --- 16 bit Signed の場合 (-32768シフトを戻す) ---
	                short[] originalPixels = (short[]) pix;
	                short[] copiedPixels = new short[originalPixels.length];
	                for (int k = 0; k < originalPixels.length; k++) {
	                    copiedPixels[k] = (short) (originalPixels[k] ^ 0x8000); // XORで安全に反転
	                }
	                pix = copiedPixels;
	                
	            } else if (pix instanceof byte[]) {
	                // --- 8 bit Signed の場合 (-128シフトを戻す) ---
	                byte[] originalPixels = (byte[]) pix;
	                byte[] copiedPixels = new byte[originalPixels.length];
	                for (int k = 0; k < originalPixels.length; k++) {
	                    copiedPixels[k] = (byte) (originalPixels[k] ^ 0x80); // XOR 0x80 で最上位ビットを反転
	                }
	                pix = copiedPixels;
	            }
	        }

	        // ※ Unsigned（符号なし）の場合はシフトされていないため、
	        // コピーせずに pix の参照をそのまま渡してOKです（Javaのbyteがマイナス値として扱ってもビット列は正しい）

	        dcmImg.setPixelData(0, w, h, samples, bits, pix);
	        images.put(i, dcmImg);
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
	 * @param slicePos
	 * @param imp
	 * @param dealWithSecondaryCapture
	 */
	private static void addAttributes(DicomObject dcm, int slicePos/* 0 to N-1 */,
			ImagePlus imp/* should be set current processor */, boolean dealWithSecondaryCapture) {

		imp.setSlice(getRealIndex(imp, slicePos+1));

		String sopClassUID = GDicomTools.getTag(imp, "0008,0016");
		if(sopClassUID != null) sopClassUID = sopClassUID.trim();
		if (dealWithSecondaryCapture) {
			sopClassUID = UID.SecondaryCaptureImageStorage.uid();
		}
		
		String sopInstUID = GDicomTools.getTag(imp, "0008,0018");
		if(sopInstUID != null) sopInstUID = sopInstUID.trim();
		if (sopInstUID == null || sopInstUID.length() == 0) {
			sopInstUID = UIDUtils.createUID();
		}
		setString(dcm, Tag.SOP​Class​UID, sopClassUID);
		setString(dcm, Tag.SOP​Instance​UID, sopInstUID);

		/*
		 * 0010,0010 Patient's Name: TEST^TARO 0010,0020 Patient ID: 0000012345
		 * 0010,0030 Patient's Birth Date: 19840405 0010,0032 Patient's Birth Time:
		 * 000000 0010,0040 Patient's Sex: M 0010,1030 Patient's Weight: 65
		 */
		String patID = GDicomTools.getTag(imp, "0010,0020");
		String patName = GDicomTools.getTag(imp, "0010,0010");
		String patBoD = GDicomTools.getTag(imp, "0010,0030");
		String patBoT = GDicomTools.getTag(imp, "0010,0032");
		String patSex = GDicomTools.getTag(imp, "0010,0040");
		String patWeight = GDicomTools.getTag(imp, "0010,1030");
		setString(dcm, Tag.Patient​Name, patName);
		setString(dcm, Tag.Patient​ID, patID);
		setDate(dcm, Tag.Patient​Birth​Date, patBoD);
		setTime(dcm, Tag.Patient​Birth​Time, patBoT);
		setString(dcm, Tag.Patient​Sex, patSex);
		setDouble(dcm, Tag.Patient​Weight, patWeight);

		/*
		 * UIDs
		 */
		// study uid
		String studyUID = GDicomTools.getTag(imp, "0020,000D");
		if (studyUID == null || studyUID.trim().length() == 0) {
			studyUID = UIDUtils.createUID();
		}
		// series uid
		String seriesUID = GDicomTools.getTag(imp, "0020,000E");
		if (seriesUID == null || seriesUID.trim().length() == 0) {
			seriesUID = UIDUtils.createUID();
		}
		// reference uid(Tag.Frame​Of​Reference​UID)
		String refUID = GDicomTools.getTag(imp, "0020,0052");
		setString(dcm, Tag.Study​Instance​UID, studyUID);
		setString(dcm, Tag.Series​Instance​UID, seriesUID);
		setString(dcm, Tag.Frame​Of​Reference​UID, refUID);

		/*
		 * 0008,0060 Modality: e.g., MR 0008, 0061 Modalities​In​Study 0008,0070
		 * Manufacturer: Visionary Imaging Services,Inc. 0018,1000 Device Serial Number:
		 * 40115 0008,1010 Station Name: GRAPHY 0018,1020 Software Versions(s): V7.0B
		 * 0008,1030 Study Descreption 0008,103E Series Description: Scano 3plane_SAG
		 * 0008,1090 Manufacturer's Model Name: GRAPHY
		 */
		String modality = GDicomTools.getTag(imp, "0008,0060");
		String modalities = GDicomTools.getTag(imp, "0008,0061");
		String manu = GDicomTools.getTag(imp, "0008,0070");
		String deviceNo = GDicomTools.getTag(imp, "0018,1000");
		String station = GDicomTools.getTag(imp, "0008,1010");
		String softVer = GDicomTools.getTag(imp, "0018,1020");
		String studyDesc = GDicomTools.getTag(imp, "0008,1030");
		String seriesDesc = GDicomTools.getTag(imp, "0008,103E");
		String modelName = GDicomTools.getTag(imp, "0008,1090");
		setString(dcm, Tag.Modality, modality);
		setString(dcm, Tag.Modalities​In​Study, modalities);
		setString(dcm, Tag.Manufacturer, manu);
		setString(dcm, Tag.Device​Serial​Number, deviceNo);// long string
		setString(dcm, Tag.Station​Name, station);
		setString(dcm, Tag.Software​Versions, softVer);// long string
		setString(dcm, Tag.Study​Description, studyDesc);
		setString(dcm, Tag.Series​Description, seriesDesc);
		setString(dcm, Tag.Manufacturer​Model​Name, modelName);

		/*
		 * 0008, 0008 ImageType 0008,0012 Instance Creation Date: 20221022 0008,0013
		 * Instance Creation Time: 121105.117 0008,0020 Study Date: 20221022 0008,0021
		 * Series Date: 20221022 0008,0022 Acquisition Date: 20221022 0008,0023 Content
		 * Date: 20221022 0008,0030 Study Time: 121008.0 0008,0031 Series Time:
		 * 121054.967 0008,0032 Acquisition Time: 121054.967 0008,0033 Content Time:
		 * 121105.0
		 */
		String imageType = GDicomTools.getTag(imp, "0008,0008");
		String instanceCreationDate = GDicomTools.getTag(imp, "0008,0012");
		String instanceCreationTime = GDicomTools.getTag(imp, "0008,0013");
		String studyDate = GDicomTools.getTag(imp, "0008,0020");
		String seriesDate = GDicomTools.getTag(imp, "0008,0021");
		String acquiDate = GDicomTools.getTag(imp, "0008,0022");
		String contDate = GDicomTools.getTag(imp, "0008,0023");
		String studyTime = GDicomTools.getTag(imp, "0008,0030");
		String seriesTime = GDicomTools.getTag(imp, "0008,0031");
		String acquiTime = GDicomTools.getTag(imp, "0008,0032");
		String contTime = GDicomTools.getTag(imp, "0008,0033");
		setString(dcm, Tag.Image​Type, imageType);
		setDate(dcm, Tag.Instance​Creation​Date, instanceCreationDate);
		setTime(dcm, Tag.Instance​Creation​Time, instanceCreationTime);
		setDate(dcm, Tag.Study​Date, studyDate);
		setDate(dcm, Tag.Series​Date, seriesDate);
		setDate(dcm, Tag.Acquisition​Date, acquiDate);
		setDate(dcm, Tag.Content​Date, contDate);
		setTime(dcm, Tag.Study​Time, studyTime);
		setTime(dcm, Tag.Series​Time, seriesTime);
		setTime(dcm, Tag.Acquisition​Time, acquiTime);
		setTime(dcm, Tag.Content​Time, contTime);

		/*
		 * 0020,0010 Study ID: 20221001-123 0020,0011 Series Number: 2 0020,0012
		 * Acquisition Number: 0 0020,0013 InstanceNumber: 3 0020,0032 image position
		 * patient 0020,0037 image orientation patient
		 */
		String studyID = GDicomTools.getTag(imp, "0020,0010");
		String seriesNo = GDicomTools.getTag(imp, "0020,0011");
		String acquiNo = GDicomTools.getTag(imp, "0020,0012");
		String instNo = GDicomTools.getTag(imp, "0020,0013");
		String imgPosPat = GDicomTools.getTag(imp, "0020,0032");
		String imgOriPat = GDicomTools.getTag(imp, "0020,0037");
		setString(dcm, Tag.Study​ID, studyID);
		setInt(dcm, Tag.Series​Number, seriesNo);
		setInt(dcm, Tag.Acquisition​Number, acquiNo);
		setInt(dcm, Tag.Instance​Number, instNo);
		setDoubles(dcm, Tag.Image​Position​Patient, imgPosPat);
		setDoubles(dcm, Tag.Image​Orientation​Patient, imgOriPat);

//		String samplesPerPixel = GDicomTools.getTag(imp, "0028,0002");
		int samplesPerPixel = imp.getProcessor() instanceof ColorProcessor ? 3 : 1; // DO NOT USE imp.getNChannels()
//		String planarConfigurationString = GDicomTools.getTag(imp, "0028,0006");
		int rows = imp.getHeight();// GDicomTools.getTag(imp, "0028,0010");
		int cols = imp.getWidth();// GDicomTools.getTag(imp, "0028,0011");
		String pixelSpacingYX = GDicomTools.getTag(imp, "0028,0030");
		if (pixelSpacingYX == null) {
			Calibration cal = imp.getCalibration();
			pixelSpacingYX = cal.pixelHeight + "\\\\" + cal.pixelWidth;
		}
		Double pixelSpacingZ = GDicomTools.getVoxelDepth(imp);// SpacingBetweenSlices
		if (pixelSpacingZ <= 0.0) {
			pixelSpacingZ = imp.getCalibration().pixelDepth;
		}
		/*
		 * When RGB, imp.getBitDepth() will return 24.
		 * In DICOM, RGB image should be 8-bit per-channel.
		 */
		int bitsAllocated = samplesPerPixel == 1 ? imp.getBitDepth():8;// GDicomTools.getTag(imp, "0028,0100");
		String bitsStored = GDicomTools.getTag(imp, "0028,0101");
		String highBit = GDicomTools.getTag(imp, "0028,0102");
		boolean signed = isSignedImagePlus(imp);
		String pixelRepresentationString = signed ? "1" : "0";
		
		//Dicom tag is prefer.
		String intercept = GDicomTools.getTag(imp, "0028,1052");
		String slope = GDicomTools.getTag(imp, "0028,1053");
		
		if(intercept==null || slope==null) {
			Calibration cal = imp.getCalibration();
			double[] coeffs = cal.getCoefficients();
			if (coeffs != null) {
				if(signed && coeffs[0]<=-32768) {
					coeffs[0] += 32768;//remove -32768 from intercept.
				}
				intercept = String.valueOf(coeffs[0]);
				slope = String.valueOf(coeffs[1]);
			}
		}
		
		setInt(dcm, Tag.Samples​Per​Pixel, samplesPerPixel);
		
		if (samplesPerPixel == 3) {
			setInt(dcm, Tag.Bits​Allocated, 8);
			setInt(dcm, Tag.Bits​Stored, 8);
			setInt(dcm, Tag.High​Bit, 7);
			setString(dcm, Tag.Photometric​Interpretation, "RGB");
			setInt(dcm, Tag.Planar​Configuration, 0); // 0 = RGBがピクセルごとに並んでいる(RGBRGBRGB...)
		} else {
			setString(dcm, Tag.Photometric​Interpretation, "MONOCHROME2");
		}
		
		setInt(dcm, Tag.Rows, rows);
		setInt(dcm, Tag.Columns, cols);
		setDoubles(dcm, Tag.Pixel​Spacing, pixelSpacingYX);
		setDouble(dcm, Tag.Spacing​Between​Slices, pixelSpacingZ);
		setInt(dcm, Tag.Bits​Allocated, bitsAllocated);
		setInt(dcm, Tag.Bits​Stored, bitsStored);
		setInt(dcm, Tag.High​Bit, highBit);
		setInt(dcm, Tag.Pixel​Representation, pixelRepresentationString);// signed 1
		setDouble(dcm, Tag.Rescale​Intercept, intercept);
		setDouble(dcm, Tag.Rescale​Slope, slope);
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
	 * See, SlideGlass:initCalibrationAndLUT
	 * 
	 * @param imp
	 * @param header
	 */
//	public static void calibrate(ImagePlus imp/*No calibrated imageplus*/, DicomObject header) {
//		if(imp == null) {
//			throw new NullPointerException();
//		}
//		if(header == null) {
//			return;
//		}
//		/*No calibrated imageplus*/
//		Calibration originalCal = imp.getCalibration();
//		boolean isRGB = imp.getType() == ImagePlus.COLOR_RGB;
//		if(isRGB) {
//			imp.getProcessor().snapshot();
//		}
//		/*
//		 * Spatial calibrations
//		 */
//		// x-y-z
//		double pixelSpacingX = 1.0;
//		double pixelSpacingY = 1.0;
//		double pixelSpacingZ = 1.0;
//		// Pixel Spacing = Row Spacing [PY] \ Column Spacing [PX] = 0.30\0.25.
//		double[] pixelSpacing = header.getDoubles(Tag.Pixel​Spacing);
//		double spacingBetweenSlices = header.getDouble(Tag.Spacing​Between​Slices, -1);
//		if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
//			pixelSpacingX = pixelSpacing[1];// column
//			pixelSpacingY = pixelSpacing[0];// row
//			if (spacingBetweenSlices != -1) {
//				pixelSpacingZ = spacingBetweenSlices;
//			} else {
//				double sliceThickness = header.getDouble(Tag.Slice​Thickness, -1);
//				if (sliceThickness != -1) {
//					pixelSpacingZ = sliceThickness;
//				}
//			}
//			/*
//			 * Units is mm, that is dicom default. see, Pixel Spacing Attribute (0028,0030)
//			 * definition.
//			 */
//			originalCal.setUnit("mm");//
//		}
//		// then, set to cal
//		originalCal.pixelWidth = pixelSpacingX;
//		originalCal.pixelHeight = pixelSpacingY;
//		originalCal.pixelDepth = pixelSpacingZ;
//		
//		/*
//		 * density calibration
//		 */
//		Double slope = header.getDouble(Tag.Rescale​Slope, Double.NaN);
//		Double intercept = header.getDouble(Tag.Rescale​Intercept, Double.NaN);
//		String modality = header.getString(Tag.Modality);
//		boolean isSigned = header.getInt(Tag.Pixel​Representation, 0) != 0;
//		if (header.getInt(Tag.Bits​Allocated, -1) == 16 && isSigned) {
//			if (!intercept.isNaN() && !slope.isNaN()) {
//				//y = a + bx
//				double[] coeff = new double[2];//[a,b]
//				coeff[0] = intercept - 32768*slope;
//				coeff[1] = slope;
//				originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
//				//add another modalities unit...
//				//...
//			}else {
//				originalCal.setSigned16BitCalibration();
//			}
//			if(modality != null && modality.equals("CT")) {
//				originalCal.setValueUnit("HU");
//			}
//		}else if (!intercept.isNaN() && !slope.isNaN()) {
//			double[] coeff = new double[2];
//			coeff[0] = intercept;
//			coeff[1] = slope;
//			originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
//		}
//		imp.setCalibration(originalCal);
//		// adjust WW/WL
//		int WL = header.getInt(Tag.Window​Center, Integer.MIN_VALUE);
//		int WW = header.getInt(Tag.Window​Width, Integer.MIN_VALUE);	
//		if (WL == Integer.MIN_VALUE || WW == Integer.MIN_VALUE) {
//			// do nothing
//		}else {
//			double newMin = WL - (.5 * WW);
//			double newMax = WL + (.5 * WW);
//			if (newMin > newMax) {
//				Log.logger.log(Level.WARNING,"SlideGlass::changeWindow() problem occured :" + newMin + " " + newMax);
//			}else {
//				imp.setDisplayRange(newMin, newMax);
//			}
//		}
//	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int pos, Vector3d ipp) {
		if (ipp == null) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setImagePositionPatient(dcms, pos, new double[] { ipp.x(), ipp.y(), ipp.z() });
	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int pos, double[] ipp) {
		if (ipp == null || ipp.length != 3) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setDoubles(dcms, pos, "0020,0032", ipp);
	}

	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImageOrientationPatient(ImagePlus dcms, int pos, double[] iop) {
		if (iop == null || iop.length != 6) {
			Log.logger.info("ImageOrientationPatient must have 6 component y axis(Row) : xyz, x axis(Col) : xyz");
			return;
		}
		setDoubles(dcms, pos, "0020,0037", iop);
	}

	public static void setImageOrientationPatient(ImagePlus dcms, int pos, Vector3d row, Vector3d col) {
		setImageOrientationPatient(dcms, pos, new double[] { row.x, row.y, row.z, col.x, col.y, col.z });
	}

	public static double[] getImagePositionPatient(ImagePlus imp, int pos/* 1 to N */) {
		double[] ipp = getDoubles(imp, pos, "0020,0032");
		return ipp;
	}

	public static double[] getImageOrientationPatient(ImagePlus imp, int pos/* 1 to N */) {
		double[] iop = getDoubles(imp, pos, "0020,0037");
		return iop;
	}

//	/**
//	 * シリーズを構成するファイルのリストから、代表となる1ファイルを選出する。
//	 * ・DICOMDIR は除外
//	 * ・非DICOMファイルは除外
//	 * ・SCやKOが通常画像と混ざっている場合は、通常画像を優先して返す。
//	 * ・SCやKOしか存在しない場合は、それを代表ファイルとして返す。
//	 * * @param seriesFiles 同じシリーズに属するファイルのリスト
//	 * @param errorLog エラーやスキップ情報を記録するリスト
//	 * @return 代表ファイル（有効なファイルがない場合は null）
//	 */
//	public static File getRepresentativeFileOfSeries(List<File> seriesFiles, List<String> errorLog) {
//		File fallbackFile = null;
//
//		for (File f : seriesFiles) {
//			if (!f.exists() || f.isDirectory()) continue;
//			
//			// 1. DICOMDIR は除外
//			if (DicomUtilities.isDICOMDIR(f)) {
//				if (com.vis.core.util.Utils.isDebug) errorLog.add("Skipped DICOMDIR: " + f.getAbsolutePath());
//				continue;
//			}
//			
//			// 2. DICOMファイルかどうかのチェック
//			if (!DicomUtilities.isDicomFile(f)) {
//				errorLog.add("Not a DICOM file: " + f.getAbsolutePath());
//				continue;
//			}
//
//			// 3. SCやKOなどの非標準画像かどうかのチェック
//			if (isSecondaryCaptureOrNonImage(f)) {
//				// SC/KOしかない場合の保険として、最初の1つをキープしておく
//				if (fallbackFile == null) fallbackFile = f;
//				continue;
//			}
//
//			// 4. ここに到達したということは、「純粋な標準画像（CT/MR等）」！これを最優先で返す。
//			return f;
//		}
//
//		// 標準画像が1枚も見つからなかったが、SC/KOは存在した場合（「それしかない」パターン）
//		if (fallbackFile != null) {
//			errorLog.add("Note: Series contains only Secondary Capture or Non-Image data. Using as representative: " + fallbackFile.getAbsolutePath());
//			return fallbackFile;
//		}
//
//		// 有効なDICOMファイルが1つもなかった場合
//		return null;
//	}

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
