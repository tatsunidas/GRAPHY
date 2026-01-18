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
package com.vis.cdw.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vis.configuration.ConfigInfo;

/**
 * 
 * @author tatsunidas
 *
 */
public class CDRToolsProperties {

    private static final Logger log = Logger.getLogger(CDRToolsProperties.class.getName());
    
    // キーの一部を定数化（検索用）
    private static final String KEY_PART_SPEED = "SPEED";
    private static final String KEY_PART_EJECT = "EJECT";
    private static final String KEY_PART_VIEWER = "WITH_VIEWER";

    /**
     * 書き込み速度を取得します。
     * @return 設定された速度。取得できない場合は null
     */
    public static Integer loadBurnSpeed() {
        String res = findValueByKeyPart(KEY_PART_SPEED);
        if (isValid(res)) {
            try {
                return Integer.parseInt(res);
            } catch (NumberFormatException e) {
                log.warning("Invalid speed format: " + res);
            }
        }
        return null;
    }

    /**
     * 焼き込み後のイジェクト設定を取得します。
     * @return イジェクトする場合は true, しない場合は false, 設定なしは null
     */
    public static Boolean loadEjectAfterBurn() {
        return loadBooleanSetting(KEY_PART_EJECT);
    }
    
    /**
     * Viewerを使用するかどうかを取得します。
     * @return 使用する場合は true
     */
    public static Boolean loadWithViewer() {
        return loadBooleanSetting(KEY_PART_VIEWER);
    }
    
    /**
     * プロパティを設定して保存します。
     */
    public static void setPropertiesAndSave(String key, String val) {
        Properties prop = loadProperties();
        prop.put(key, val);
        saveProperties(prop);
    }

    // --- Private Helper Methods ---

    /**
     * 共通のBoolean読み込みロジック (0=false, それ以外=true)
     */
    private static Boolean loadBooleanSetting(String keyPart) {
        String res = findValueByKeyPart(keyPart);
        if (isValid(res)) {
            try {
                int val = Integer.parseInt(res);
                return val != 0;
            } catch (NumberFormatException e) {
                log.warning("Invalid boolean(int) format for " + keyPart + ": " + res);
            }
        }
        return null;
    }

    /**
     * プロパティファイルを読み込みます。
     * 読み込み失敗時は空のPropertiesを返します。
     */
    private static Properties loadProperties() {
        Properties prop = new Properties();
        File file = getConfigFile();
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                prop.load(fis);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to load properties file", e);
            }
        }
        return prop;
    }

    /**
     * プロパティファイルを保存します。
     */
    private static boolean saveProperties(Properties prop) {
        try (FileOutputStream out = new FileOutputStream(getConfigFile())) {
            prop.store(out, null);
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to save properties file", e);
            return false;
        }
    }

    /**
     * キー名の一部を含むプロパティ値を検索して返します。
     * (元のコードの contains ロジックを踏襲)
     */
    private static String findValueByKeyPart(String keyPart) {
        Properties prop = loadProperties();
        for (String key : prop.stringPropertyNames()) {
            if (key.contains(keyPart)) {
                return prop.getProperty(key);
            }
        }
        return null;
    }

    private static File getConfigFile() {
        return new File(ConfigInfo.getPath(ConfigInfo.CDRTOOL_Props));
    }

    private static boolean isValid(String str) {
        return str != null && !str.isEmpty();
    }
}