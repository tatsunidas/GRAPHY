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
package com.vis.core.ui.settings;

import javax.swing.JFormattedTextField.AbstractFormatter;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IPv4アドレス形式を検証するカスタムフォーマッター。
 */
public class IpAddressFormatter extends AbstractFormatter {

    private static final long serialVersionUID = 1L;

    @Override
    public Object stringToValue(String text) throws ParseException {
        if (text == null || text.trim().isEmpty()) {
            return null; // 空の入力を許可
        }

        // ドットで分割（-1を指定することで末尾の空文字列も保持し "192." のような入力を扱える）
        String[] parts = text.split("\\.", -1);

        // 4オクテットを超えている場合は不正
        if (parts.length > 4) {
            throw new ParseException("IPアドレスのオクテットは4つまでです。", 0);
        }

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // "192.168..1" のような、空のオクテットは許容しない
            // ただし、"192.168.1." のように末尾がドットで終わる入力途中の場合は許容する
            if (part.isEmpty() && i < parts.length - 1) {
                 throw new ParseException("ドットが連続しています。", 0);
            }
            // 末尾の空オクテットは入力途中なのでOK
            if (part.isEmpty()) {
                continue;
            }

            // 数字以外が含まれている場合は不正
            if (!part.matches("\\d+")) {
                throw new ParseException("オクテットには数字のみ使用できます。", 0);
            }

            // 3桁を超えている場合は不正
            if (part.length() > 3) {
                throw new ParseException("オクテットは3桁までです。", 0);
            }

            // 0から始まる複数桁は不正 (例: "01", "012")
            if (part.startsWith("0") && part.length() > 1) {
                throw new ParseException("0から始まる複数桁のオクテットは無効です。", 0);
            }

            // 255を超えている場合は不正
            int intValue = Integer.parseInt(part);
            if (intValue > 255) {
                throw new ParseException("オクテットの値は255以下である必要があります。", 0);
            }
        }

        // すべてのチェックをパスすれば、その文字列を（途中でも）有効な値として返す
        return text;
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        if (value instanceof String) {
            return (String) value;
        }
        return "";
    }
}