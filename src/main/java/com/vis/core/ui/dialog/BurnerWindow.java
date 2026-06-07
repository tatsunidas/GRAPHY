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
package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JComboBox;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.apache.commons.io.FileUtils;

import com.vis.cdw.cdrecord.BurnCD;
import com.vis.cdw.common.CDRToolsProperties;
import com.vis.cdw.common.DriveUtil;
import com.vis.cdw.common.MediaCreationException;
import com.vis.configuration.ConfigInfo;
import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomUtilities;

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * 
 * @author tatsunidas
 *
 */

@SuppressWarnings("serial")
public class BurnerWindow extends JFrame implements WindowListener{

	private JComboBox<String> comboBoxDrive;
	private JTree treeBurnList;
	private JCheckBox chckbxViewer;
	private JCheckBox chckbxEject;
	private JComboBox<Integer> comboBoxSpeed;
	private JButton btnAnonymize;

	private boolean simulate = false;
//	private int speed;
	
	private DcmAnonymizer2 anonymizer = null;

	// debug
	public static void main(String[] args) {
		//new BurnerWindow(new File("temp/DICOM-CD-TEST"), true);
		
		//resource copy test
		
		
	}

	public BurnerWindow(final File burnDestDirInTemp, boolean debug) {
		super("Burn to CD/DVD");

		this.simulate = debug;

		if (!burnDestDirInTemp.exists()) {
			Log.logger.warning("BurnerWindow: burn target folder not found: " + burnDestDirInTemp.getAbsolutePath());
			JOptionPane.showMessageDialog(MainScreen.getInstance(), Resources.i18n("BurnerWindow.error.noBurnTarget"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		//Check file size
		// ウィンドウを表示する前にサイズを測り、ユーザーに判断を仰ぐ
		if (!checkInitialDataSize(burnDestDirInTemp)) {
			// ユーザーがキャンセルした場合
			deleteAfterBurn(); // クリーンアップして終了
			return; // ウィンドウを表示せずにコンストラクタを抜ける
		}

		setSize(400, 400);

		JPanel panelSelectDrive = new JPanel();
		getContentPane().add(panelSelectDrive, BorderLayout.NORTH);
		panelSelectDrive.setLayout(new BorderLayout(0, 0));

		JLabel lblDrive = new JLabel("Select Drive");
		panelSelectDrive.add(lblDrive, BorderLayout.NORTH);

		List<String> drives = DriveUtil.getAvailableDriveNames();
		if (drives == null || drives.size() < 1) {
			Log.logger.warning("BurnerWindow: no drive devices detected.");
			JOptionPane.showMessageDialog(null, Resources.i18n("BurnerWindow.error.noDevices"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		comboBoxDrive = new JComboBox<String>(drives.toArray(new String[0]));
		panelSelectDrive.add(comboBoxDrive, BorderLayout.SOUTH);

		JScrollPane scrollPaneBurnList = new JScrollPane();
		getContentPane().add(scrollPaneBurnList, BorderLayout.CENTER);

		treeBurnList = new JTree();
		DefaultTreeModel treeModel = new DefaultTreeModel(buildTree(burnDestDirInTemp), false);
		treeBurnList.setModel(treeModel);
		for (int i = 0; i < treeBurnList.getRowCount(); i++) {
		    treeBurnList.expandRow(i);
		}
		scrollPaneBurnList.setViewportView(treeBurnList);

		JPanel panelSettings = new JPanel();
		getContentPane().add(panelSettings, BorderLayout.EAST);
		GridBagLayout gbl_panelSettings = new GridBagLayout();
		gbl_panelSettings.columnWidths = new int[] { 83, 0 };
		gbl_panelSettings.rowHeights = new int[] { 21, 21, 0, 0, 0, 0, 0, 0, 0, 0 };
		gbl_panelSettings.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
		gbl_panelSettings.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panelSettings.setLayout(gbl_panelSettings);

		chckbxViewer = new JCheckBox("with viewer");
		chckbxViewer.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_chckbxViewer = new GridBagConstraints();
		gbc_chckbxViewer.anchor = GridBagConstraints.WEST;
		gbc_chckbxViewer.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxViewer.gridx = 0;
		gbc_chckbxViewer.gridy = 0;
		panelSettings.add(chckbxViewer, gbc_chckbxViewer);

		chckbxEject = new JCheckBox("auto eject");
		GridBagConstraints gbc_chckbxEject = new GridBagConstraints();
		gbc_chckbxEject.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxEject.anchor = GridBagConstraints.WEST;
		gbc_chckbxEject.gridx = 0;
		gbc_chckbxEject.gridy = 1;
		panelSettings.add(chckbxEject, gbc_chckbxEject);

		JLabel lblSpeed = new JLabel(" Speed");
		GridBagConstraints gbc_lblSpeed = new GridBagConstraints();
		gbc_lblSpeed.anchor = GridBagConstraints.WEST;
		gbc_lblSpeed.insets = new Insets(0, 0, 5, 0);
		gbc_lblSpeed.gridx = 0;
		gbc_lblSpeed.gridy = 2;
		panelSettings.add(lblSpeed, gbc_lblSpeed);

		comboBoxSpeed = new JComboBox<Integer>(new Integer[] { 1, 2, 4, 5, 8, 12, 16, 24 });
		GridBagConstraints gbc_comboBoxSpeed = new GridBagConstraints();
		gbc_comboBoxSpeed.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBoxSpeed.insets = new Insets(0, 0, 5, 0);
		gbc_comboBoxSpeed.gridx = 0;
		gbc_comboBoxSpeed.gridy = 3;
		panelSettings.add(comboBoxSpeed, gbc_comboBoxSpeed);
		
		btnAnonymize = new JButton("Anonymize");
		GridBagConstraints gbc_btnAnonymize = new GridBagConstraints();
		gbc_btnAnonymize.insets = new Insets(0, 0, 5, 0);
		gbc_btnAnonymize.anchor = GridBagConstraints.WEST;
		gbc_btnAnonymize.gridx = 0;
		gbc_btnAnonymize.gridy = 4;
		panelSettings.add(btnAnonymize, gbc_btnAnonymize);
		btnAnonymize.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				if(anonymizer == null) {
//					anonymizer = new DcmAnonymizer2();
//				}else {
//					anonymizer.setVisible(true);
//				}
				JOptionPane.showMessageDialog(null, Resources.i18n("BurnerWindow.info.underDevelopment"), Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
			}
		});

		JPanel panelOKCancel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) panelOKCancel.getLayout();
		flowLayout.setAlignment(FlowLayout.RIGHT);
		getContentPane().add(panelOKCancel, BorderLayout.SOUTH);

		JButton btnOK = new JButton("OK");
		btnOK.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// BURN
				if(anonymizer != null && anonymizer.isProceeded()) {
					anonymizer.mtranscode(burnDestDirInTemp, new File(burnDestDirInTemp.getAbsolutePath()+"_deident"));
					startBurinig(new File(burnDestDirInTemp.getAbsolutePath()+"_deident"), simulate);
				}else {
					startBurinig(burnDestDirInTemp, simulate);
				}
			}
		});
		panelOKCancel.add(btnOK);

		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// close window and delete temp files
				deleteAfterBurn();
			}
		});
		btnCancel.setHorizontalAlignment(SwingConstants.RIGHT);
		panelOKCancel.add(btnCancel);

		loadSettings();

		java.awt.Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);
		setVisible(true);
	}

	private TreeNode buildTree(File burnFileInTemp) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("DICOM");
		File dcmFolder = new File(burnFileInTemp.getAbsolutePath() + File.separator + "DICOM");
		if (!dcmFolder.exists()) {
			return null;
		}
		File[] plist = dcmFolder.listFiles();
		if (plist != null) {
			for (File patDir : plist) {
				DefaultMutableTreeNode patNode = new DefaultMutableTreeNode(patDir.getName());
				root.add(patNode);
				File[] studyList = patDir.listFiles();
				if (studyList != null) {
					for (File study : studyList) {
						DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(study.getName());
						patNode.add(studyNode);
						File[] seriesList = study.listFiles();
						if (seriesList != null) {
							for (File series : seriesList) {
								DefaultMutableTreeNode seriesNode = new DefaultMutableTreeNode(series.getName());
								studyNode.add(seriesNode);
								File[] imageList = series.listFiles();
								if (imageList != null) {
									for (File image : imageList) {
										seriesNode.add(new DefaultMutableTreeNode(image.getName()));
									}
								}
							}
						}
					}
				}
			}
		}
		return root;
	}

	private void loadSettings() {
		// set eject
		setCurrentEjectState();
		// set with viewer
		setCurrentWithViewerState();
		// set speed
		setCurrentSpeed2Combo();
	}

	// write speed
	private void setCurrentSpeed2Combo() {
		Integer speed = CDRToolsProperties.loadBurnSpeed();
		comboBoxSpeed.setSelectedItem(speed);
		comboBoxSpeed.revalidate();
	}

	private void setCurrentEjectState() {
		if (CDRToolsProperties.loadEjectAfterBurn()) {
			chckbxEject.setSelected(true);
		} else {
			chckbxEject.setSelected(false);
		}
		chckbxEject.revalidate();
	}

	private void setCurrentWithViewerState() {
		if (CDRToolsProperties.loadWithViewer()) {
			chckbxViewer.setSelected(true);
		} else {
			chckbxViewer.setSelected(false);
		}
		chckbxViewer.revalidate();
	}

	private boolean withViewer() {
		return chckbxViewer.isSelected();
	}

	// BurnerWindow.java 内部

    private String getDeviveScsi(String nickname) {
        // nullチェック
        if (nickname == null) return "0,0,0";

        // パターン1: "Generic Drive (0,0,0)" のようにカッコ付きの場合
        if (nickname.contains("(") && nickname.endsWith(")")) {
            try {
                int start = nickname.lastIndexOf("(") + 1;
                int end = nickname.lastIndexOf(")");
                return nickname.substring(start, end).trim();
            } catch (Exception e) {
                // パース失敗時はそのまま返すか、デフォルトを返す
            }
        }

        // パターン2: "Type_Vendor_Model_0,0,0" のようにアンダースコア区切りの場合（旧仕様）
        if (nickname.contains("_")) {
            return nickname.substring(nickname.lastIndexOf("_") + 1);
        }

        // パターン3: すでに "0,0,0" のような形式の場合
        if (nickname.matches("^[0-9,]+$")) {
            return nickname;
        }

        // 救済措置: そのまま返す（ただしこれだとエラーになる可能性大）
        return nickname;
    }

	/*
	 * this is not good idea, TODO
	 */
	private boolean isCD(String nickname) {
		return nickname.contains("CD");
	}

	private void startBurinig(File burnFileInTemp, boolean debug) {
		// 1. デバイス情報の取得
		String deviceName = (String) comboBoxDrive.getSelectedItem();
		String device = getDeviveScsi(deviceName);
		
		// attach viewer and DICOMDIR
		if (withViewer()) {
			try {
				DicomUtilities.attachDICOMDIRTo(burnFileInTemp.getAbsolutePath());
				Utils.copyDirectory(ConfigInfo.WEASIS.toString(), burnFileInTemp.getAbsolutePath());
			} catch (Exception e) {
				Log.logger.severe("BurnerWindow: data preparation failed: " + e.getMessage());
				JOptionPane.showMessageDialog(this, Resources.i18n("BurnerWindow.error.dataPreparation") + " " + e.getMessage(), Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		long requiredBlocks = DriveUtil.getIsoSizeInBlocks(burnFileInTemp);

		if (requiredBlocks == -1) {
			Log.logger.severe("BurnerWindow: failed to calculate data size.");
			JOptionPane.showMessageDialog(this, Resources.i18n("BurnerWindow.error.calcSize"), Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		Log.logger.info("Required blocks: " + requiredBlocks);

		// 2. 書き込み速度の設定
		int speed = (Integer) comboBoxSpeed.getSelectedItem();
		if (!isCD(deviceName)) {
			Log.logger.info("WriteSpeed changed to 8X for wrinting Non CD-R media.");
			speed = 8;
		}
		
		final int speed_final = speed;
		
		boolean eject = chckbxEject.isSelected();

		while (true) {
            boolean readyToBurn = false;
            
            // 1. 空ディスクチェック
            if (DriveUtil.isDiskEmpty(device)) {
                // ディスクが正しく認識された場合
                long freeBlocks = DriveUtil.getMediaFreeSpaceInBlocks(device);
                Log.logger.info("Media free blocks: " + freeBlocks);

                if (freeBlocks > requiredBlocks) {
                    // OK: capacity is sufficient
                    readyToBurn = true;
                } else if (freeBlocks == -1) {
                    // capacity retrieval failed -> user decision
                    int ret = JOptionPane.showConfirmDialog(this,
                            Resources.i18n("BurnerWindow.error.mediaCapacityUnknown"),
                            Resources.i18n("dialog.title.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ret == JOptionPane.YES_OPTION) readyToBurn = true;
                } else {
                    // NG: insufficient capacity
                    long diffMB = (requiredBlocks - freeBlocks) * 2048 / 1024 / 1024;
                    String msg = String.format(Resources.i18n("BurnerWindow.error.mediaInsufficient"), diffMB);
                    int option = JOptionPane.showConfirmDialog(this, msg, Resources.i18n("dialog.title.warning"),
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (option != JOptionPane.YES_OPTION) {
                        cleanUp(); return;
                    }
                    continue; // continue loop
                }
            } else {
                // ディスクが入っていない、または検出エラー(Windowsで頻発)
                // ★ここを修正：YESなら再試行、NOなら中止、CANCEL(またはOptions)で強制実行
                
                String message = "空のCD/DVDが見つかりません。\n\n"
                        + "・ディスクを挿入済みの場合: ドライブの認識に失敗している可能性があります。\n"
                        + "・未挿入の場合: 新しいディスクを挿入してください。\n\n"
                        + "「はい」: 再スキャンします\n"
                        + "「いいえ」: 書き込みを中止します\n"
                        + "「キャンセル」: 警告を無視して書き込みを強行します(上級者向)";

                int option = JOptionPane.showConfirmDialog(this, message, "メディア確認",
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

                if (option == JOptionPane.YES_OPTION) {
                    // 再スキャン (ループ継続)
                    continue;
                } else if (option == JOptionPane.CANCEL_OPTION) {
                    // ★強制実行 (Force Burn)
                    // 検出できていなくても、ユーザーが「入ってる」と言うなら進む
                    Log.logger.info("User selected FORCE BURN ignoring disk check.");
                    readyToBurn = true; 
                } else {
                    // いいえ -> 中止
                    cleanUp();
                    return;
                }
            }

            // 書き込みフラグが立ったらループを抜けて本番へ
            if (readyToBurn) {
                break;
            }
        }

		// 5. 書き込み実行 (UIをフリーズさせないため、別スレッドで実行)
		new Thread(new Runnable() {
			@Override
			public void run() {
				BurnCD burner = new BurnCD(device, speed_final, eject, false);
				burner.setSimulate(false);
				try {
					// ここで時間がかかる処理を実行
					burner.burn(burnFileInTemp, new File(burnFileInTemp.getAbsolutePath() + ".iso"));

					// Update UI on EDT after completion
					javax.swing.SwingUtilities.invokeLater(() -> {
						Log.logger.info("BurnerWindow: writing completed successfully.");
						JOptionPane.showMessageDialog(null, Resources.i18n("BurnerWindow.done"), Resources.i18n("dialog.title.complete"), JOptionPane.INFORMATION_MESSAGE);
						cleanUp();
					});

				} catch (MediaCreationException e) {
					Log.logger.severe("BurnerWindow: writing failed: " + e.getMessage());
					javax.swing.SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(null, Resources.i18n("BurnerWindow.error.writeFailed") + "\n" + e.getMessage(), Resources.i18n("dialog.title.error"),
								JOptionPane.ERROR_MESSAGE);
						cleanUp();
					});
				}
			}
		}).start();
	}
	
	/**
     * 書き込み対象データの概算サイズを計算し、CD/DVDの容量を超える場合に警告を出します。
     * @param targetDir 書き込み対象ディレクトリ
     * @return ユーザーが「キャンセル」を選んだ場合は false, 「続行」または問題ない場合は true
     */
	private boolean checkInitialDataSize(File targetDir) {
		try {
			// ディレクトリ内の合計サイズを計算 (バイト単位)
			long sizeBytes = FileUtils.sizeOfDirectory(targetDir);
			long sizeMB = sizeBytes / (1024 * 1024); // MB換算

			// 基準値 (安全マージンを考慮して少し少なめに設定)
			final long WEASIS_MB = 85;//weasis 前提
			final long CD_LIMIT_MB = 670 - WEASIS_MB; // 700MBメディアの安全圏
			final long DVD_LIMIT_MB = 4400 - WEASIS_MB; // 4.7GBメディアの安全圏 (約4480MBだが安全を見て)

			String message = null;
			String title = "データ容量確認";
			int messageType = JOptionPane.INFORMATION_MESSAGE;

			if (sizeMB > DVD_LIMIT_MB) {
				// DVD(一層)すら超える場合
				message = String.format("書き込み対象のデータサイズは約 %,d MB です。\n\n" + "一般的なDVD-R (4.7GB) の容量を超えています。\n"
						+ "2層DVD (DL) が必要か、書き込みきれない可能性があります。\n\n" + "このまま処理を続行しますか？", sizeMB);
				messageType = JOptionPane.WARNING_MESSAGE;

			} else if (sizeMB > CD_LIMIT_MB) {
				// CDには収まらないがDVDなら入る場合
				message = String.format("書き込み対象のデータサイズは約 %,d MB です。\n\n" + "一般的なCD-R (700MB) には収まりません。\n"
						+ "DVD-R などの大容量メディアをご用意ください。\n\n" + "このまま処理を続行しますか？", sizeMB);
				messageType = JOptionPane.QUESTION_MESSAGE;
			}

			// 警告メッセージがある場合のみダイアログ表示
			if (message != null) {
				int option = JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION,
						messageType);

				if (option != JOptionPane.YES_OPTION) {
					return false; // キャンセル
				}
			}

			return true; // 問題なし、またはユーザーが承諾

		} catch (Exception e) {
			e.printStackTrace();
			// サイズ計算に失敗しても、とりあえずプロセス自体は止めない（後続の正確なチェックに任せる）
			return true;
		}
	}

	private void deleteAfterBurn() {
		// delete burnFileInTemp
		File[] files = new File(ConfigInfo.getPath(ConfigInfo.TemporalDirName)).listFiles();
		if (files != null && files.length > 0) {
			for (File del : files) {
				try {
					if(del.isFile()) {
						FileUtils.delete(del);
					}else if(del.isDirectory()){
						FileUtils.deleteDirectory(del);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if(anonymizer != null) {
			anonymizer.dispose();
		}
		dispose();
	}

	private void cleanUp() {
		// update state
		CDRToolsProperties.setPropertiesAndSave("SPEED", String.valueOf((int) comboBoxSpeed.getSelectedItem()));
		CDRToolsProperties.setPropertiesAndSave("EJECT", String.valueOf(chckbxEject.isSelected() ? 1 : 0));
		CDRToolsProperties.setPropertiesAndSave("WITH_VIEWER", String.valueOf(chckbxViewer.isSelected() ? 1 : 0));
		deleteAfterBurn();// clean up tmp dir and dispose
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		cleanUp();
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}
}
