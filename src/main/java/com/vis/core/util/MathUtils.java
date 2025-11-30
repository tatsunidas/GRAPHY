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
package com.vis.core.util;

public class MathUtils {
	
	/**
     * 指定された小数を、指定された桁数で切り捨てるメソッド
     * * @param value 切り捨てたい数値
     * @param decimalPlaces 残したい小数点以下の桁数 (例: 3を指定すると小数点第3位まで残す)
     * @return 切り捨て後の数値
     */
    public static double truncateToDecimalPlace(double value, int decimalPlaces) {
        // 1. 10^(decimalPlaces) を計算 (例: 3桁残すなら 1000.0)
        double powerOfTen = Math.pow(10, decimalPlaces);
        
        // 2. 数値に powerOfTen を掛けて、残したい桁までを整数部に移動させる
        //    例: 1.234567 * 1000.0 = 1234.567
        double shifted = value * powerOfTen;
        
        // 3. Math.floor() で小数点以下を切り捨てる
        //    例: Math.floor(1234.567) = 1234.0
        double truncated = Math.floor(shifted);
        
        // 4. powerOfTen で割って、元のスケールに戻す
        //    例: 1234.0 / 1000.0 = 1.234
        return truncated / powerOfTen;
    }
}
