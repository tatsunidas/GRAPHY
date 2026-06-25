package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportService;
import com.vis.core.reporting.template.ReportTemplate;
import com.vis.core.reporting.template.ReportTemplateStore;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;

/**
 * Rich-text report editor (Swing {@code JEditorPane} + {@code HTMLEditorKit}).
 * Supports bold/italic/underline, lists, boilerplate template insertion, key-image
 * insertion (with object-retrieval anchors), draft save, and finalize-as-SR.
 * <p>
 * Hyperlinks are inert while editing (HTMLEditorKit fires no events when editable);
 * links navigate in the read-only {@link SRHtmlViewerWindow}.
 *
 * @author tatsunidas
 */
public class ReportEditorDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final ReportService service = new ReportService();
	private final ReportTemplateStore templateStore = new ReportTemplateStore();
	private final TextInputAssist inputAssist = TextInputAssist.NONE; // Phase-2 hook (voice / 辞書)

	private final ReportDocument doc;
	private final JTextField titleField = new JTextField(30);
	private final JTextField authorField = new JTextField(16);
	private final JLabel statusLabel = new JLabel();
	private final JEditorPane editor = new JEditorPane();
	private Runnable onSaved;

	private ReportEditorDialog(Window owner, ReportDocument doc) {
		super(owner, Resources.i18n("Reporting.editor.title"), ModalityType.MODELESS);
		this.doc = doc;
		setSize(new Dimension(760, 720));
		setLocationRelativeTo(owner);
		buildUI();
		loadFromDoc();
	}

	/** Open an editor for a fresh draft anchored to the given patient/study. */
	public static ReportEditorDialog showNew(Window owner, String patID, String studyUID, String studyDate,
			String author) {
		ReportDocument d = ReportDocument.newDraft(patID, studyUID, studyDate, author);
		ReportEditorDialog dlg = new ReportEditorDialog(owner, d);
		dlg.setVisible(true);
		return dlg;
	}

	/** Open an editor for an existing report. */
	public static ReportEditorDialog showExisting(Window owner, ReportDocument doc) {
		ReportEditorDialog dlg = new ReportEditorDialog(owner, doc);
		dlg.setVisible(true);
		return dlg;
	}

	public void setOnSaved(Runnable onSaved) {
		this.onSaved = onSaved;
	}

	private void buildUI() {
		editor.setEditable(true);
		editor.setContentType("text/html");
		editor.setEditorKit(new HTMLEditorKit());
		inputAssist.install(editor);

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
		header.add(new JLabel(Resources.i18n("Reporting.editor.field.title")));
		header.add(titleField);
		header.add(new JLabel(Resources.i18n("Reporting.editor.field.author")));
		header.add(authorField);
		header.add(statusLabel);

		JPanel top = new JPanel(new BorderLayout());
		top.add(header, BorderLayout.NORTH);
		top.add(buildToolbar(), BorderLayout.SOUTH);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton save = new JButton(Resources.i18n("Reporting.action.saveDraft"));
		save.addActionListener(e -> saveDraft());
		JButton finalize = new JButton(Resources.i18n("Reporting.action.finalizeSR"));
		finalize.addActionListener(e -> finalizeAsSR());
		JButton close = new JButton(Resources.i18n("Reporting.action.close"));
		close.addActionListener(e -> dispose());
		south.add(save);
		south.add(finalize);
		south.add(close);

		JPanel content = new JPanel(new BorderLayout());
		content.add(top, BorderLayout.NORTH);
		content.add(new JScrollPane(editor), BorderLayout.CENTER);
		content.add(south, BorderLayout.SOUTH);
		setContentPane(content);
	}

	private JToolBar buildToolbar() {
		JToolBar bar = new JToolBar();
		bar.setFloatable(false);

		JButton bold = new JButton(Resources.i18n("Reporting.editor.bold"));
		bold.addActionListener(new HTMLEditorKit.BoldAction());
		JButton italic = new JButton(Resources.i18n("Reporting.editor.italic"));
		italic.addActionListener(new HTMLEditorKit.ItalicAction());
		JButton underline = new JButton(Resources.i18n("Reporting.editor.underline"));
		underline.addActionListener(new HTMLEditorKit.UnderlineAction());

		JButton ul = new JButton(Resources.i18n("Reporting.editor.bulletList"));
		ul.addActionListener(e -> insertHtmlAtCaret("<ul><li>&nbsp;</li></ul>"));
		JButton ol = new JButton(Resources.i18n("Reporting.editor.numberList"));
		ol.addActionListener(e -> insertHtmlAtCaret("<ol><li>&nbsp;</li></ol>"));

		JButton keyImg = new JButton(Resources.i18n("Reporting.editor.insertKeyImage"));
		keyImg.addActionListener(e -> insertKeyImage());

		JButton link = new JButton(Resources.i18n("Reporting.editor.insertLink"));
		link.addActionListener(e -> insertLink());

		JComboBox<ReportTemplate> templates = new JComboBox<>();
		templates.addItem(null);
		for (ReportTemplate t : templateStore.getTemplates()) {
			templates.addItem(t);
		}
		templates.addActionListener(e -> {
			ReportTemplate t = (ReportTemplate) templates.getSelectedItem();
			if (t != null && t.getBody() != null) {
				insertHtmlAtCaret(t.getBody());
				templates.setSelectedItem(null);
			}
		});

		bar.add(bold);
		bar.add(italic);
		bar.add(underline);
		bar.addSeparator();
		bar.add(ul);
		bar.add(ol);
		bar.addSeparator();
		bar.add(keyImg);
		bar.add(link);
		bar.addSeparator();
		bar.add(new JLabel(Resources.i18n("Reporting.editor.template") + " "));
		bar.add(templates);
		return bar;
	}

	private void loadFromDoc() {
		titleField.setText(doc.getTitle() == null ? "" : doc.getTitle());
		authorField.setText(doc.getAuthor() == null ? "" : doc.getAuthor());
		if (doc.getBodyHtml() != null && !doc.getBodyHtml().isEmpty()) {
			editor.setText(doc.getBodyHtml());
		}
		editor.setCaretPosition(0);
		refreshStatus();
	}

	private void refreshStatus() {
		statusLabel.setText("   [" + doc.getStatus() + "]");
	}

	private void insertHtmlAtCaret(String html) {
		try {
			HTMLDocument hdoc = (HTMLDocument) editor.getDocument();
			HTMLEditorKit kit = (HTMLEditorKit) editor.getEditorKit();
			kit.insertHTML(hdoc, editor.getCaretPosition(), html, 0, 0, null);
		} catch (Exception ex) {
			Log.logger.warning("ReportEditorDialog - insert HTML failed: " + ex.getMessage());
		}
	}

	/** Insert a key-image reference to the currently active Praparat's current slice. */
	private void insertKeyImage() {
		Viewer2DScreen viewer = Viewer2DScreen.getInstance();
		if (viewer == null) {
			info(Resources.i18n("Reporting.editor.noViewer"));
			return;
		}
		ArrayList<Praparat> sel = viewer.getSelectedPraps();
		if (sel == null || sel.isEmpty()) {
			info(Resources.i18n("Reporting.editor.noSelection"));
			return;
		}
		Praparat pp = sel.get(0);
		Object[] uids = pp.getUIDs();
		String patID = (String) uids[0];
		String studyUID = (String) uids[1];
		String seriesUID = (String) uids[2];
		String[] sopUIDs = (String[]) uids[3];
		String sop = null;
		SlideGlass slide = pp.getCurrentSlide();
		if (slide != null && slide.getSOPInstanceUID() != null) {
			sop = slide.getSOPInstanceUID();
		} else if (sopUIDs != null && sopUIDs.length > 0) {
			sop = sopUIDs[0];
		}
		if (sop == null) {
			info(Resources.i18n("Reporting.editor.noSelection"));
			return;
		}
		String sopClass = DatabaseHandler.getInstance().getValueFromImage("SOPClassUID", patID, studyUID, seriesUID,
				sop);
		String label = Resources.i18n("Reporting.editor.keyImageLabel");
		KeyImageRef ref = new KeyImageRef(studyUID, seriesUID, sop, sopClass, label);
		doc.addKeyImage(ref);
		insertHtmlAtCaret("<a href=\"" + ref.toHref() + "\">" + escape(label) + "</a>&nbsp;");
	}

	private void insertLink() {
		String url = JOptionPane.showInputDialog(this, Resources.i18n("Reporting.editor.linkUrlPrompt"));
		if (url == null || url.trim().isEmpty()) {
			return;
		}
		String text = JOptionPane.showInputDialog(this, Resources.i18n("Reporting.editor.linkTextPrompt"), url);
		if (text == null || text.trim().isEmpty()) {
			text = url;
		}
		insertHtmlAtCaret("<a href=\"" + escape(url.trim()) + "\">" + escape(text.trim()) + "</a>&nbsp;");
	}

	private void saveDraft() {
		applyToDoc();
		service.saveDraft(doc);
		refreshStatus();
		if (onSaved != null) {
			onSaved.run();
		}
		info(Resources.i18n("Reporting.editor.saved"));
	}

	private void finalizeAsSR() {
		applyToDoc();
		service.saveDraft(doc);
		final ReportEditorDialog self = this;
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				return service.finalizeAsSR(doc);
			}

			@Override
			protected void done() {
				String sop = null;
				try {
					sop = get();
				} catch (Exception ignore) {
				}
				refreshStatus();
				if (onSaved != null) {
					onSaved.run();
				}
				if (sop != null) {
					info(Resources.i18n("Reporting.editor.finalized"));
				} else {
					JOptionPane.showMessageDialog(self, Resources.i18n("Reporting.editor.finalizeFailed"),
							Resources.i18n("dialog.title.information"), JOptionPane.WARNING_MESSAGE);
				}
			}
		}.execute();
	}

	/** Copy UI state into the document and reconcile key-image refs with the body. */
	private void applyToDoc() {
		doc.setTitle(titleField.getText());
		doc.setAuthor(authorField.getText());
		doc.setBodyHtml(editor.getText());
		reconcileKeyImages();
	}

	/** Drop key-image refs whose anchor was removed from the body text. */
	private void reconcileKeyImages() {
		String html = editor.getText();
		List<KeyImageRef> kept = new ArrayList<>();
		for (KeyImageRef ref : doc.getKeyImages()) {
			if (ref.toHref() != null && html.contains(ref.toHref())) {
				kept.add(ref);
			}
		}
		doc.setKeyImages(kept);
	}

	private void info(String msg) {
		JOptionPane.showMessageDialog(this, msg, Resources.i18n("dialog.title.information"),
				JOptionPane.INFORMATION_MESSAGE);
	}

	private static String escape(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"",
				"&quot;");
	}
}
