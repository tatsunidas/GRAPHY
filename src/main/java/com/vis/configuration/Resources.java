package com.vis.configuration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.ResourceBundle;

import javax.swing.ImageIcon;

import com.vis.core.log.Log;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Platform;
import com.vis.core.util.Utils;

import ij.plugin.LutLoader;

/**
 * Handle resources.
 * This class has a role of handling resources.
 * Main purpose is copy resources to configuration folders when starting-up.
 * 
 * @author tatsunidas
 *
 */
public enum Resources {
	
	Splash("icon/splash.png"),
	RotateCursor("icon/rotateIcon.png"),
	
	GraphyIcon("icon/GRAPHY-512.png"),
	LocalIcon("icon/outline_house_black_18dp.png"),
	QRIcon("icon/ic_import_export_black_36dp.png"),
	QR_Ready_Icon("icon/25px-Faenza-emblem-downloads.png"),
	ArchivedIcon("icon/outline_house_black_18dp.png"),
	LinkIcon("icon/ic_link_black_18dp.png"),
	AnchorIcon("icon/outlined_flag-24px.png"),
	FlagIcon("icon/flag-24px.png"),
	TreeStudyLevelCloseIcon("icon/folder_FILL0_wght400_GRAD0_opsz24.png"),
	TreeStudyLevelOpenIcon("icon/folder_open_FILL0_wght400_GRAD0_opsz24.png"),
	TreeSeriesLevelIcon("icon/stack_FILL0_wght400_GRAD0_opsz24.png"),
	TreeImageLevelIcon("icon/dcm_32x32x32.png"),
	MainWindowIcon("icon/GRAPHY-128.png"),
	MenuBarImportNoDcmIcon("icon/outline_add_photo_alternate_black_48dp.png"),
	MenuBarImportIcon("icon/ic_archive_black_48dp.png"),
	MenuBarExportIcon("icon/ic_save_black_48dp.png"),
	MenuBarBrowseDBIcon("icon/ic_view_list_black_48dp.png"),
	MenuBarBurnCDIcon("icon/ic_album_black_48dp.png"),
	MenuBarDeleteIcon("icon/ic_delete_black_48dp.png"),
	MenuBarMetadataIcon("icon/ic_art_track_black_48dp.png"),
	MenuBarSendIcon("icon/ic_send_black_48dp.png"),
	MenuBarSettingsIcon("icon/ic_settings_black_48dp.png"),
	MenuBarViewer2DIcon("icon/ic_desktop_windows_black_48dp.png"),
	MenuBarTagExtractor("icon/tag_extractor2_48dp_1F1F1F.png"),
	MenuBarConditionalSeriesExtractor("icon/ConditionalSeriesExtractor_48dp_1F1F1F.png"),
	MenuBarAnonymizer("icon/anonymize_48dp_1F1F1F.png"),
	
	//2d viewer
	Viewer2DFrameWinIcon("icon/GRAPHY-128.png"),
	ResultWinIcon("icon/outline_square_foot_black_48dp.png"),
	RoiObjManagerWinIcon("icon/analysis_48.png"),
	PresenceCellSharinIcon("icon/Eye_of_Sharin.png"),
	PresenceCellHorusIcon("icon/Eye_of_Horus.png"),
	PresenceCellStandardIcon("icon/Eye_of_Standard.png"),
	CineStartIcon("icon/baseline_play_circle_outline_black_18dp.png"),
	CineStopIcon("icon/baseline_pause_circle_outline_black_18dp.png"),
	OverlayIcon("icon/anno_icon.png"),
	TileLayoutIcon("icon/baseline_border_all_black_18dp.png"),
	ResetPraparatIcon("icon/baseline_autorenew_black_48dp.png"),
	InvertIcon("icon/baseline_star_half_black_48dp.png"),
	FlipLRIcon("icon/baseline_flip_black_48dp.png"),
	FlipHFIcon("icon/baseline_flip_HF_black_48dp_.png"),
	ScreenOutIcon("icon/outline_visibility_off_black_48dp.png"),
	WindowContrastIcon("icon/baseline_perm_data_setting_black_48dp.png"),
	CropIcon("icon/crop_48.png"),
	CutIcon("icon/outline_content_cut_black_48dp.png"),
	RadiomicsJIcon("icon/RadiomicsJ_icon.png"),
	SlicerIcon("icon/slicer.png"),
	
	
	//pref
	PrefsIcon("icon/ic_build_black_36dp.png"),
	PrefsPACSIcon("icon/ic_import_export_black_36dp.png"),
	PrefsROIIcon("icon/shaperoi_48.png"),
	
	//roi tools
	RectangleRoiIcon("icon/roi_rectangle.png"),
	OvalRoiIcon("icon/roi_oval_circle.png"),
	FreeRoiIcon("icon/roi_freehand_closed.png"),
	LineRoiIcon("icon/roi_polyline.png"),
	FreeLineRoiIcon("icon/roi_freehand_draw.png"),
	PolyLineRoiIcon("icon/roi_polyline2.png"),
	PolygonRoiIcon("icon/roi_polygon.png"),
	ArrowRoiIcon("icon/roi_arrow.png"),
	PointRoiIcon("icon/roi_point_scan_64dp.png"),
	MultiPointRoiIcon("icon/roi_multipoint2.png"),
	TextRoiIcon("icon/roi_text_64.png"),
	AngleRoiIcon("icon/roi_angle.png"),
	RoiBrushIcon("icon/roi_brush_48dp.png"),
	RoiWandIcon("icon/wand.jpeg"),
	
	//viewer3d
	MenuBarViewer3DIcon("icon/ic_3d_rotation_black_48dp.png"),
	//mpr window
	MenuBarMPRWindowIcon("icon/outline_grid_view_black_48dp.png"),
	//other
	MissingIcon(null),
	
	SQL_LISTENER("sql/LISTENER.sql"), // deprecated, use AE instead.
	SQL_LOCALE("sql/LOCALE.sql"),
	SQL_MISCELLANEOUS("sql/MISCELLANEOUS.sql"),// delete ?
	SQL_MODALITY("sql/MODALITY.sql"),
	SQL_PATIENT("sql/PATIENT.sql"),
	SQL_STUDY("sql/STUDY.sql"),
	SQL_SERIES("sql/SERIES.sql"),
	SQL_IMAGE("sql/IMAGE.sql"),
	SQL_PRESET("sql/PRESETS.sql"),
	SQL_ROI("sql/ROI.sql"),
	SQL_SERVERS("sql/SERVERS.sql"),//previous name is SERVERS.sql
	SQL_TEXTANNOTATION("sql/TEXTANNOTATION.sql"),
	SQL_THEME("sql/THEME.sql"),
	
	DicomDict("dicom_dict/dicom_dict.properties"),
	//default db settings, see also ConfigInfo.
	RecordFactory("dcmqrscp/RecordFactory.xml"),
	AE_Properties("dcmqrscp/ae.properties"),
	STORE_TCS_Properties("dcmqrscp/store-tcs.properties"),
	PS3_15_TableE1_1("dicom_dict/Table_E1_1_Application_Level_Confidentiality.csv"),
	PS3_15_TableE3_4_1("dicom_dict/Table_E3_4-1_ApplicationLevelConfidentialityProfileCleanStructuredContentOptionContentItemConceptNameCodes.csv"),
	PS3_15_TableE3_10_1("dicom_dict/Table_E3_10-1_SafePrivateAttributes.csv"),
	
	//LUT
	LUT_GRAY("luts/gray.lut"),
	LUT_FIRE("luts/Fire-1.lut"),
	LUT_S_PET("luts/S_PET.lut"),
	LUT_PHASE("luts/Phase.lut"),
	LUT_RAINBOW("luts/Rainbow.lut"),
	; 
	
	private String pathInResource;
	private Resources(String path) {
		this.pathInResource = path;
	}
	
	/**
	 * path in resource
	 * E.g., /conf/graphy.properties
	 * @return file path in resources
	 */
	public String path() {
		return pathInResource;
	}
	
	public File tempFile() {
		InputStream is = Resources.class.getClassLoader().getResourceAsStream(pathInResource);
		if (is == null) {
			Log.logger.warning("This resource file is not exists.:" + pathInResource);
			return null;
		}
		File tempFile = null;
		try {
			if(new File(ConfigInfo.TemporalDirName.toString()).exists()) {
				tempFile = File.createTempFile("GRAPHY_temp", null/*.tmp*/, new File(ConfigInfo.TemporalDirName.toString()));
			}else{
				tempFile = File.createTempFile("GRAPHY_temp", null/*.tmp*/);
			}
			tempFile.deleteOnExit();
			// out temp file
			java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			is.close();
			outputStream.close();
			return tempFile;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Resize image icon to 48 * 48
	 * @return
	 */
	public ImageIcon loadIconFromResource(){
		if(pathInResource.endsWith("png") || pathInResource.endsWith("jpg") || pathInResource.endsWith("jpeg")) {
			InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathInResource);
			if(stream == null) {
				stream = Resources.class.getResourceAsStream("/"+pathInResource);
			}
			ImageIcon ico = null;
			try {
				java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(stream);
				if(img.getWidth() > 48) {
					img = (java.awt.image.BufferedImage)ImageUtils.resize(img, 48, 48);
				}
				ico = new ImageIcon(img);
			} catch (IOException e) {
				Log.logger.severe("Cannot load Resources files...");
				e.printStackTrace();
				return null;
			}
			return ico;
		}
		return null;
	}
	
	public BufferedImage loadImageFromResource(){
		if(pathInResource.endsWith("png") || pathInResource.endsWith("jpg") || pathInResource.endsWith("jpeg")) {
			InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathInResource);
			if(stream == null) {
				stream = Resources.class.getResourceAsStream("/"+pathInResource);
			}
			try {
				return javax.imageio.ImageIO.read(stream);
			} catch (IOException e) {
				Log.logger.severe("Cannot load Resources files...");
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}
	
	public ij.process.LUT loadLUT(){
		if(pathInResource.endsWith("lut")) {
			File appDir = Platform.getAppDirectory();
			if(new File(appDir.getAbsolutePath()+File.separator+pathInResource).exists()) {
				return LutLoader.openLut(new File(appDir.getAbsolutePath()+File.separator+pathInResource).getAbsolutePath());
			}else {
				//debug
				return LutLoader.openLut(new File(pathInResource).getAbsolutePath());
			}
		}
		return null;
	}
	
	public static ij.process.LUT loadLUT(String name) {
		File appDir = Platform.getAppDirectory();
		File lut = new File(appDir.getAbsolutePath() + File.separator + "luts"+ File.separator + name +".lut");
		if (lut.exists()) {
			return LutLoader.openLut(lut.getAbsolutePath());
		} else {
			// debug
			return LutLoader.openLut(new File("./luts/"+name+".lut").getAbsolutePath());
		}
	}
	
	public static HashMap<String,ij.process.LUT> loadAllLUT() {
		HashMap<String,ij.process.LUT> luts = new HashMap<String,ij.process.LUT>();
		File appDir = Platform.getAppDirectory();
		File parent = new File(appDir.getAbsolutePath()+File.separator+"luts");//no pathSeparator ";"
		if(Utils.isDebug) {
			parent = new File("luts");
		}
		Log.logger.fine("luts parent:"+parent.getAbsolutePath());
		File[] lutFileList = parent.listFiles();
		Log.logger.fine(lutFileList.length + " lut files are loaded.");
		for(File l:lutFileList) {
			String name = l.getName();
			if(name.endsWith("lut")) {
				int end = name.indexOf(".lut");
				String lutType = name.substring(0, end);
				luts.put(lutType,LutLoader.openLut(l.getAbsolutePath()));
			}
		}
		return luts;
	}
	
	/**
     * アプリケーションと同階層にある lut (または luts) フォルダを走査し、
     * 格納されているすべてのLUTファイル名（拡張子なし）の配列を取得します。
     * * @return LUT名の配列。フォルダが存在しない場合はデフォルトの "Grayscale" を返します。
     */
    public static String[] getLutNames() {
        // 1. フォルダの特定（"lut" フォルダを優先し、無ければ "luts" を探す）
        java.io.File lutDir = new java.io.File("lut");
        if (!lutDir.exists() || !lutDir.isDirectory()) {
            lutDir = new java.io.File("luts");
        }

        // フォルダが見つからない場合の安全策
        if (!lutDir.exists() || !lutDir.isDirectory()) {
            com.vis.core.log.Log.logger.warning("LUT directory not found. Returning default list.");
            return new String[] { "Grayscale" };
        }

        // 2. フォルダ内のファイルを走査（隠しファイルなどは除外）
        java.io.File[] files = lutDir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(java.io.File pathname) {
                return pathname.isFile() && !pathname.isHidden();
            }
        });

        if (files == null || files.length == 0) {
            return new String[] { "Grayscale" };
        }

        // 3. ファイル名から拡張子を取り除き、リストに追加
        java.util.List<String> nameList = new java.util.ArrayList<>();
        nameList.add("Grayscale"); // デフォルトLUTとして常に先頭に配置しておくのがオススメです

        for (java.io.File f : files) {
            String fileName = f.getName();
            
            // 拡張子を取り除く処理 (例: "S_Pet.lut" -> "S_Pet")
            int dotIndex = fileName.lastIndexOf('.');
            String lutName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;

            // 重複を防ぐ（大文字小文字の違い等も考慮する場合は適宜調整）
            if (!nameList.contains(lutName) && !"Grayscale".equalsIgnoreCase(lutName)) {
                nameList.add(lutName);
            }
        }

        // 4. UIで見やすいように、"Grayscale" 以降をアルファベット順にソート
        if (nameList.size() > 1) {
            java.util.Collections.sort(nameList.subList(1, nameList.size()));
        }

        return nameList.toArray(new String[0]);
    }
	
	public static String i18n(String key) {
		return ResourceBundle.getBundle("i18n.i18n").getString(key);
	}
}
