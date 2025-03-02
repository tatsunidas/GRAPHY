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

import java.io.File;

/**
 * 
 * @author tatsunidas
 *
 */
public class DeleteFolder {
    public static void main(String[] args) {
        File seriesDir = new File("path/to/folder"); // 削除したいフォルダのパスを指定

        if (deleteDirectory(seriesDir)) {
            System.out.println("フォルダを削除しました: " + seriesDir.getAbsolutePath());
        } else {
            System.out.println("フォルダを削除できませんでした: " + seriesDir.getAbsolutePath());
        }
    }

    public static boolean deleteDirectory(File dir) {
        if (!dir.exists()) {
            return false;
        }

        // フォルダ内のファイル・サブフォルダを取得
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                // ファイルなら削除、フォルダなら再帰的に削除
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        // 最後にフォルダ自体を削除
        return dir.delete();
    }
}

