package com.vis.core.ui.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;

import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JTable;

/**
 * 
 * only can read tags info.
 *
 * @author tatsunidas
 * @version 1.0
 *
 */
public class DicomTagsViewer extends javax.swing.JFrame {

	//debug
	public static void main(String args[]) {

		String parent = "E:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-core\\src\\test\\resources\\JIRA_DICOM\\";
		String testCR = parent + "CR_JPG_IR87a.dcm";
//		String testNM = parent + "NM_LEE_IR87.dcm";		
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new DicomTagsViewer(new File(testCR));
			}
		});
	}

	private static final long serialVersionUID = 5578453739629959934L;
	private javax.swing.JTable dicomTagTable;
	private JTextField textField;
	private JScrollPane scrollPane;
	private String[] tagViewHeader = new String[] { "gggg,eeee", "Name", "VR", "Value" };
	private DefaultTableModel model;
//	private ArrayList<String[]> tagsArray = null;
	private ArrayList<DicomTagModel> tagsArray2 = null;
	private Object[][] tagsData = null;
	HighlightTableCellRenderer renderer;
	String encode = "";

	/**
	 * Creates new form DicomTagsViewer
	 */
	public DicomTagsViewer(File dcm) {
		setUp(dcm.getAbsolutePath());
		setVisible(true);
	}
	
	public DicomTagsViewer(DICOMNode node) {
		if(node.getLevel()!=DICOMNode.IMAGE) {
			JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "-DicomTagsViewer-\nPlease select image row.");
			return;
		}
		String patID = node.getData(DICOMNode.PatientID);
		String studyUID = node.getData(DICOMNode.StudyInstanceUID);
		String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
		String sopUID = node.getData(DICOMNode.SOPInstanceUID);
		DatabaseHandler db = DatabaseHandler.getInstance();
		String p2img = db.getFileLocation(patID, studyUID, seriesUID, sopUID);
		setUp(p2img);
		setVisible(true);
	}
	
	void setUp(String p2dcm){
		DicomTagsParser tagReader = new DicomTagsParser();
		tagsArray2 = tagReader.read(p2dcm);
		constructTableDataUsinfDicomTags();
		//UI
		scrollPane = new JScrollPane();
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		model = new DefaultTableModel(tagsData, tagViewHeader);
		dicomTagTable = new JTable(model);
		scrollPane.setViewportView(dicomTagTable);
		renderer = new HighlightTableCellRenderer();
		dicomTagTable.setDefaultRenderer(Object.class, renderer);
		JPanel searchPanel = new JPanel();
		getContentPane().add(searchPanel, BorderLayout.NORTH);
		JLabel lblSearch = new JLabel("Search : ");
		searchPanel.add(lblSearch);
		textField = new JTextField();
		textField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void update(DocumentEvent e) {
				if(renderer != null) {
					System.out.println(textField.getText());
					renderer.setPattern(textField.getText());
					repaint();
				}
			}
		});
		searchPanel.add(textField);
		textField.setColumns(25);
		pack();
	}

//	private void constructTableData() {
//		if (tagsArray.size() < 1) {
//			return;
//		}
//		tagsData = new Object[tagsArray.size()][];
//		int num = 0;
//		for (String[] item : tagsArray) {
//			tagsData[num] = (Object[]) item;
//			num++;
//		}
//	}
	
	private void constructTableDataUsinfDicomTags() {
		if (tagsArray2.size() < 1) {
			return;
		}
		tagsData = new Object[tagsArray2.size()][];
		int num = 0;
		for (DicomTagModel item : tagsArray2) {
			Object row[] = new Object[4];
			row[0] = (Object)item.getTag();
			row[1] = (Object)item.getTagName();
			row[2] = (Object)item.getVR();
			row[3] = (Object)item.getTagValue();
			tagsData[num] = row;
			num++;
		}
	}

//	private void readTagsInfo(Attributes in) {
//		int[] tags = in.tags();
//		for (int i = 0; i < tags.length; i++) {
//			VR vr = in.getVR(tags[i]);
//			if (vr.code() == VR.SQ.code()) {
//				Attributes seq = in.getNestedDataset(tags[i]);
//				if(seq == null) {
//					String[] info = new String[4];
//					info[0] = TagUtils.toString(tags[i]);// String.valueOf(tags[i]);//tag
//					info[1] = ElementDictionary.keywordOf(tags[i], null);// name
//					info[2] = ElementDictionary.vrOf(tags[i], null).name();// or getVR()
//					info[3] = "";
//					tagsArray.add(info);
//				}else {
//					/*
//					 * TODO この方法だと、 
//					 * 1414: >>(FFFE,E000) #56 Item #1 
//					 * 1422: >>(0008,0000) UL #4 [44] GroupLength
//					 * のタグが取得できない。
//					 * DcmDumpのように取得してくるしかないかも。
//					 * 
//					 */
//					String[] info = new String[4];
//					info[0] = TagUtils.toString(tags[i]);// String.valueOf(tags[i]);//tag
//					info[1] = ElementDictionary.keywordOf(tags[i], null);// name
//					info[2] = ElementDictionary.vrOf(tags[i], null).name();// or getVR()
//					info[3] = "";
//					tagsArray.add(info);
//					readTagsInfo(in.getNestedDataset(tags[i]));
//				}
//			} else {
//				String[] info = new String[4];
//				info[0] = TagUtils.toString(tags[i]);// String.valueOf(tags[i]);//tag
//				info[1] = ElementDictionary.keywordOf(tags[i], null);// name
//				info[2] = ElementDictionary.vrOf(tags[i], null).name();// or getVR()
//				Object val = in.getValue(tags[i]);
//				if (val instanceof byte[]) {
//					if (vr.code() == VR.US.code()) {
//						short s = -1;
//						if(Platform.getEndianness().equals("Big-endian")) {
//							s = (short) (ByteBuffer.wrap((byte[])val).order(ByteOrder.BIG_ENDIAN).getShort());
//						}else {
//							s = (short) (ByteBuffer.wrap((byte[])val).order(ByteOrder.LITTLE_ENDIAN).getShort());
//						}						
//						info[3] = String.valueOf(s);
//					} else {
//						try {
//							info[3] = new String((byte[]) val, encode);
//						} catch (UnsupportedEncodingException e) {
//							e.printStackTrace();
//							info[3] = "";
//						}
//					}
//				} else if (val instanceof Value) {
//					info[3] = ((Value) val).toString();
//				} else {
//					if (val == null) {
//						info[3] = "";
//					} else {
//						info[3] = (String) val;
//					}
//				}
//				tagsArray.add(info);
//			}
//		}
//	}

	@FunctionalInterface
	public interface SimpleDocumentListener extends DocumentListener {
		void update(DocumentEvent e);

		@Override
		default void insertUpdate(DocumentEvent e) {
			update(e);
		}

		@Override
		default void removeUpdate(DocumentEvent e) {
			update(e);
		}

		@Override
		default void changedUpdate(DocumentEvent e) {
			update(e);
		}
	}

	/*
	 * https://ateraimemo.com/Swing/TableHighlightRegexFilter.html
	 */
	class HighlightTableCellRenderer extends JTextField implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		private final Color BACKGROUND_SELECTION_COLOR = new Color(220, 240, 255);
		private final transient Highlighter.HighlightPainter highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(
				Color.YELLOW);
		private String pattern = "";
		private String prev;

		public boolean setPattern(String str) {
			if (str == null || str.equals(pattern)) {
				return false;
			} else {
				prev = pattern;
				pattern = str;
				return true;
			}
		}

		public HighlightTableCellRenderer() {
			super();
			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder());
			setForeground(Color.BLACK);
			setBackground(Color.WHITE);
			setEditable(false);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			String txt = Objects.toString(value, "");
			Highlighter highlighter = getHighlighter();
			highlighter.removeAllHighlights();
			setText(txt);
			setBackground(isSelected ? BACKGROUND_SELECTION_COLOR : Color.WHITE);
			if (pattern != null && !pattern.isEmpty() && !pattern.equals(prev)) {
				Matcher matcher = Pattern.compile(pattern).matcher(txt);
				int pos = 0;
				while (matcher.find(pos) && !matcher.group().isEmpty()) {
					int start = matcher.start();
					int end = matcher.end();
					try {
						highlighter.addHighlight(start, end, highlightPainter);
					} catch (BadLocationException | PatternSyntaxException e) {
						e.printStackTrace();
					}
					pos = end;
				}
			}
			return this;
		}
	}

}
