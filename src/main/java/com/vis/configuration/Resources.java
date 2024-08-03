package com.vis.configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.ResourceBundle;

import javax.swing.ImageIcon;

import com.vis.core.log.Log;

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
	
	//pref
	PrefsIcon("icon/ic_build_black_36dp.png"),
	PrefsPACSIcon("icon/ic_import_export_black_36dp.png"),
	PrefsROIIcon("icon/shaperoi_48.png"),
	
	//roi tools
	RectangleRoiIcon("icon/baseline_crop_square_black_48dp.png"),
	OvalRoiIcon("icon/baseline_panorama_fish_eye_black_48dp.png"),
	LineRoiIcon("icon/baseline_remove_black_48dp.png"),
	PolygonRoiIcon("icon/baseline_timeline_black_48dp.png"),
	ArrowRoiIcon("icon/baseline_transit_enterexit_black_48dp.png"),
	PointRoiIcon("icon/baseline_scatter_plot_black_48dp.png"),
	TextRoiIcon("icon/baseline_font_download_black_48dp.png"),
	AngleRoiIcon("icon/measure_angle_roi_icon.png"),
	RoiBrushIcon("icon/outline_brush_black_48dp.png"),
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
	SQL_AE("sql/AE.sql"),//previous name is SERVERS.sql
	SQL_TEXTANNOTATION("sql/TEXTANNOTATION.sql"),
	SQL_THEME("sql/THEME.sql"),
	
	DicomDict("dicom_dict/dicom_dict.properties"),
	//default db settings, see also ConfigInfo.
	RecordFactory("dcmqrscp/RecordFactory.xml"),
	
	//LUT
	LUT_FIRE("luts/Fire-1.lut"),
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
				tempFile = File.createTempFile("GRAPHY_temp", null, new File(ConfigInfo.TemporalDirName.toString()));
			}else{
				tempFile = File.createTempFile("GRAPHY_temp", null);
			}
			tempFile.deleteOnExit();
			// out temp file
			java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
			byte[] buffer = new byte[1024];
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
	
	public ImageIcon loadIconFromResource(){
		if(pathInResource.endsWith("png") || pathInResource.endsWith("jpg") || pathInResource.endsWith("jpeg")) {
//			InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(pathInResource);
			InputStream stream = Resources.class.getClassLoader().getResourceAsStream(pathInResource);//DO NOT USE
			ImageIcon ico = null;
			try {
				ico = new ImageIcon(javax.imageio.ImageIO.read(stream));
			} catch (IOException e) {
				Log.logger.severe("Cannot load Resources files...");
				e.printStackTrace();
				return null;
			}
			return ico;
		}
		return null;
	}
	
	public ij.process.LUT loadLUT(){
		if(pathInResource.endsWith("lut")) {
			if(new File("./"+pathInResource).exists()) {
				return LutLoader.openLut(new File("./"+pathInResource).getAbsolutePath());
			}else {
				if(!new File("./luts").exists()) {
					new File("./luts").mkdirs();
				}
				File tempFile = tempFile();
				try {
					Files.copy(tempFile.toPath(), new File("./"+pathInResource).toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
				return LutLoader.openLut(new File("./"+pathInResource).getAbsolutePath());
			}
		}
		return null;
	}
	
	public static HashMap<String,ij.process.LUT> loadAllLUT() {
		if(!new File("./luts").exists()) {
			return null;
		}
		HashMap<String,ij.process.LUT> luts = new HashMap<String,ij.process.LUT>();
		File parent = new File("./luts");
		File[] lutFileList = parent.listFiles();
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
	
	public static String i18n(String key) {
		return ResourceBundle.getBundle("i18n.i18n").getString(key);
	}
}
