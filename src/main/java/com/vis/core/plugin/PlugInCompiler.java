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
package com.vis.core.plugin;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.tools.JavaCompiler;

import com.vis.configuration.ConfigInfo;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.util.Utils;

public class PlugInCompiler {
	
	public static void selectAndCompile() {
		SwingUtilities.invokeLater(() -> {
			// JFileChooser のインスタンスを作成
			JFileChooser fileChooser = new JFileChooser();
			// .java ファイルのみを選択できるようにする
			FileNameExtensionFilter filter = new FileNameExtensionFilter("Java File", "java");
			fileChooser.setFileFilter(filter);
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.setMultiSelectionEnabled(false);
			// タイトルを設定
			fileChooser.setDialogTitle("Select .java file.");

			// ファイル選択ダイアログを表示
			int result = fileChooser.showOpenDialog(null);

			// ユーザーの選択を確認
			if (result == JFileChooser.APPROVE_OPTION) {
				// 選択されたファイルを取得
				File selectedFile = fileChooser.getSelectedFile();

				// ファイルのパスを表示
				Log.logger.fine("Selected file: " + selectedFile.getAbsolutePath());
				boolean res = compile(selectedFile.getAbsolutePath());
				if(!res) {
					PopUpMessage.showDialog(null, "Compile plugin", "Failed to complie. Please check java file and graphy environments.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
				}
			} else if (result == JFileChooser.CANCEL_OPTION) {
				Log.logger.fine("Compiling is canceled.");
			}
		});
	}

	public static boolean compile(String javaPath) {
		// Java compiler
		JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			Log.logger.severe("Cannot run Java Compiler. Try do that with JDK (JRE cannot compile).");
			return false;
		}

		// Set paths of all classes in graphy.
		String currentClassPath = System.getProperty("java.class.path");

		// Run compile
		int result = compiler.run(null, // 標準入力
				System.out, // 標準出力
				System.err, // 標準エラー出力
				"-classpath", currentClassPath, // クラスパスを指定
				"-d", Utils.getConfSubDirPath(ConfigInfo.PluginDirName),// 保存先ディレクトリを指定
				javaPath // コンパイルするファイル
		);

		if (result == 0) {
			return true;
		} else {
			System.out.println("Failed compile with error type: " + result);
			return false;
		}
	}
}
