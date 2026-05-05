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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.vis.core.log.Log;

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
		if(!isDeletable(dir.getAbsolutePath())) {
			Log.logger.severe("Cannot delete folder: Folder is in use, or has no access rights.->" + dir.getAbsolutePath());
			return false;
		}

		// collect sub folders/files
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				// delete recursively
				if (file.isDirectory()) {
					deleteDirectory(file);
				} else {
					deleteFile(file);
				}
			}
		}
		boolean success = dir.delete();
		if(success) {
			Log.logger.fine("Success folder deletion: " + dir.getAbsolutePath());
			return true;
		} else {
			Log.logger.severe("Cannot delete folder: Folder may have child files, or is in use, or has no access rights.->" + dir.getAbsolutePath());
			return false;
		}
	}
	
	public static boolean deleteFile(File file) {
		return deleteFile(file.getAbsolutePath());
	}

	public static boolean deleteFile(String filePath) {
		if (!isDeletable(filePath)) {
			System.out.println("Cannot delete: File is in use or has no access rights.");
			return false;
		}
		try {
			Files.delete(Paths.get(filePath));
			Log.logger.fine("Success file deletion: " + filePath);
			return true;
		} catch (IOException e) {
			Log.logger.info("Cannot delete: File is in use or has no access rights.->" + filePath);
			return false;
		}
	}

	/**
	 * Check exists and in referencing.
	 * When target file referencing others, return false.
	 * 
	 * @param folder/filePath
	 * @return
	 */
	public static boolean isDeletable(String filePath) {
		Path path = Paths.get(filePath);
		return Files.exists(path) && Files.isWritable(path);
	}
	
    public static void deleteDirectoryRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        dir.delete();
    }
}
