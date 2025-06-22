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
package com.vis.core.radiomics;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import com.vis.core.log.Log;
import com.vis.core.util.PropertiesUtil;

/**
 * to use load/save radiomics settings.
 * 
 * @author tatsunidas
 *
 */
public class SettingsContext {
	
	/**
	 * calculation
	 */
	public static final String CLASSIFICATION = "CLASSIFICATION_STRING";
	public static final String REGRESSION = "REGRESSION_STRING";
	public static final String D3Basis = "3DBASIS_BOOL";
	
	/**
	 * mask settings
	 */
	public static final String MASK_LABEL = "MASK_LABEL_INT";
	public static final String RemoveOutliers = "RemoveOutliers_BOOL";
	public static final String RemoveOutliersSigma = "Sigma_INT";
	public static final String RangeFiltering = "RangeFiltering_BOOL";
	public static final String RangeFilteringMin = "ResamplingMin_DOUBLE";
	public static final String RangeFilteringMax = "ResamplingMax_DOUBLE";
	public static final String Resampling = "Resampling_BOOL";
	public static final String ResamplingX = "ResamplingX_DOUBLE";
	public static final String ResamplingY = "ResamplingY_DOUBLE";
	public static final String ResamplingZ = "ResamplingZ_DOUBLE";
	
	//operationals
	public static final String OPERATIONAL = "Operational";
	public static final String DIAGNOSTICS = "Diagnostics";
	
	/**
	 * Feature names
	 */
	public static final String MORPHOLOGICAL = "Molphological";
	public static final String LOCALINTENSITY = "LocalIntensity";
	public static final String INTENSITYSTATS = "IntensityStats";
	public static final String INTENSITYHISTOGRAM = "IntensityHistogram";
	public static final String INTENSITYVOLUMEHISTOGRAM = "VolumeHistogram";
	public static final String GLCM = "GLCM";
	public static final String GLRLM = "GLRLM";
	public static final String GLSZM = "GLSZM";
	public static final String GLDZM = "GLDZM";
	public static final String NGTDM = "NGTDM";
	public static final String NGLDM = "NGLDM";
	public static final String FRACTAL = "Fractal";
	public static final String SHAPE2D = "Shape2D";
	
	//TODO
	public static final String UseBinCountHISTOGRAM = "BINCOUNT_HIST_BOOL";
	public static final String BinCountHISTOGRAM = "BINCOUNT_HIST_INT";
	public static final String BinWidthHISTOGRAM = "BINWIDTH_HIST_DOUBLE";
	
	public static final String UseOriginalIVH = "USEORIGINAL_IVH_BOOL";
	public static final String UseBinCountIVH = "BINCOUNT_IVH_BOOL";
	public static final String BinCountIVH = "BINCOUNT_IVH_INT";
	public static final String BinWidthIVH = "BINWIDTH_IVH_DOUBLE";
	
	public static final String UseBinCountGLCM = "BINCOUNT_GLCM_BOOL";
	public static final String BinCountGLCM = "BINCOUNT_GLCM_INT";
	public static final String BinWidthGLCM = "BINWIDTH_GLCM_DOUBLE";
	public static final String DeltaGLCM = "DELTA_GLCM_DOUBLE";
	//public static final String NormGLCM = "NORM_GLCM_STRING";
	
	public static final String UseBinCountGLRLM = "BINCOUNT_GLRLM_BOOL";
	public static final String BinCountGLRLM = "BINCOUNT_GLRLM_INT";
	public static final String BinWidthGLRLM = "BINWIDTH_GLRLM_DOUBLE";
//	public static final String NormGLRLM = "NORM_GLRLM_STRING";
	
	public static final String UseBinCountGLSZM = "BINCOUNT_GLSZM_BOOL";
	public static final String BinCountGLSZM = "BINCOUNT_GLSZM_INT";
	public static final String BinWidthGLSZM = "BINWIDTH_GLSZM_DOUBLE";
//	public static final String NormGLSZM = "NORM_GLSZM_STRING";
	
	public static final String UseBinCountGLDZM = "BINCOUNT_GLDZM_BOOL";
	public static final String BinCountGLDZM = "BINCOUNT_GLDZM_INT";
	public static final String BinWidthGLDZM = "BINWIDTH_GLDZM_DOUBLE";
//	public static final String NormGLDZM = "NORM_GLDZM_STRING";
	
	public static final String UseBinCountNGTDM = "BINCOUNT_NGTDM_BOOL";
	public static final String BinCountNGTDM = "BINCOUNT_NGTDM_INT";
	public static final String BinWidthNGTDM = "BINWIDTH_NGTDM_DOUBLE";
	public static final String DeltaNGTDM = "DELTA_NGTDM_DOUBLE";
//	public static final String NormNGTDM = "NORM_NGTDM_STRING";
	
	public static final String UseBinCountNGLDM = "BINCOUNT_NGLDM_BOOL";
	public static final String BinCountNGLDM = "BINCOUNT_NGLDM_INT";
	public static final String BinWidthNGLDM = "BINWIDTH_NGLDM_DOUBLE";
	public static final String AlphaNGLDM = "ALPHA_NGLDM_DOUBLE";
	public static final String DeltaNGLDM = "DELTA_NGLDM_DOUBLE";
//	public static final String NormNGLDM = "NORM_NGLDM_STRING";
	
	public static final String BoxSizesFRACTAL = "BOXSIZES_FRACTAL";
	
	/**
     * 指定されたオブジェクトからString型のインスタンスフィールドの値をリストとして取得します。
     *
     * @param obj フィールド値を取得したいオブジェクト
     * @return Stringフィールドの値のリスト
     * @throws IllegalAccessException privateフィールドへのアクセスに失敗した場合
     */
	public static List<String> getStringFieldValues() throws IllegalAccessException {
		List<String> values = new ArrayList<>();
		// クラスに定義されているすべてのフィールドを取得
		Field[] fields = SettingsContext.class.getDeclaredFields();
		for (Field field : fields) {
			// フィールドがString型であり、かつstaticであることを確認
			if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
				// privateなフィールドにもアクセスできるように設定
				field.setAccessible(true);
				// フィールドの値を取得してリストに追加
				Object value = field.get(SettingsContext.class);
				if (value != null) {
					values.add((String) value);
				}
			}
		}
		return values;
	}
	
	public static Properties loadSettings(String path) {
		Properties prop = PropertiesUtil.loadProperties(path);
		if(prop == null) {
			Log.logger.log(Level.WARNING, "radiomics proprties file can not load...");
			return null;
		}
		return prop;
	}
	
	public static void saveSettings(Properties prop, String path) {
		PropertiesUtil.saveProperties(prop, path);
	}
}
