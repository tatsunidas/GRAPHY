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
	public static final String Resampling = "Resampling_BOOL";
	public static final String ResamplingX = "ResamplingX_DOUBLE";
	public static final String ResamplingY = "ResamplingY_DOUBLE";
	public static final String ResamplingZ = "ResamplingZ_DOUBLE";
	public static final String RangFiltering = "RangeFiltering_BOOL";
	public static final String RangFilteringMin = "ResamplingMin_DOUBLE";
	public static final String RangFilteringMax = "ResamplingMax_DOUBLE";
	
	/**
	 * Use for all texture
	 */
	public static final String UseBinCount = "BINCOUNT_BOOL";
	public static final String UseBinWidth = "BINWIDTH_BOOL";
	public static final String BinCount = "BINCOUNT_INT";
	public static final String BinWidth = "BINWIDTH_DOUBLE";
	
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
