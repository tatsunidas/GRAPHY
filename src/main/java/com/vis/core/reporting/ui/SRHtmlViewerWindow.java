package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.reporting.KeyImageRef;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.db.DatabaseHandler;

/**
 * Read-only HTML viewer for SR-family documents (free-text SR, RDSR, KO, ...).
 * SR objects are routed here instead of the image viewer (Praparat).
 * <ul>
 *   <li>{@code graphy://image/...} links perform object retrieval — they load the
 *       referenced instance into the 2D viewer.</li>
 *   <li>external {@code http(s)} links open in the default browser.</li>
 *   <li>the whole report can also be opened in the default browser.</li>
 * </ul>
 *
 * @author tatsunidas
 */
public class SRHtmlViewerWindow extends JFrame {

	private static final long serialVersionUID = 1L;

	private final JEditorPane editor;
	private String currentHtml = "";
	private final String patID;

	private SRHtmlViewerWindow(String title, String patID) {
		super(title == null ? Resources.i18n("Reporting.sr.viewer.title") : title);
		this.patID = patID;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(new Dimension(720, 800));
		setLocationRelativeTo(null);

		editor = new JEditorPane();
		editor.setEditable(false);
		editor.setContentType("text/html");
		editor.setEditorKit(new HTMLEditorKit());
		editor.addHyperlinkListener(this::onHyperlink);

		JToolBar bar = new JToolBar();
		bar.setFloatable(false);
		JButton browser = new JButton(Resources.i18n("Reporting.action.openInBrowser"));
		browser.addActionListener(e -> openInBrowser());
		bar.add(browser);

		JPanel content = new JPanel(new BorderLayout());
		content.add(bar, BorderLayout.NORTH);
		content.add(new JScrollPane(editor), BorderLayout.CENTER);
		setContentPane(content);
	}

	/** Build (on the EDT) and show a viewer for the given rendered HTML. */
	public static void showSr(String title, String html, String patID) {
		SwingUtilities.invokeLater(() -> {
			SRHtmlViewerWindow w = new SRHtmlViewerWindow(title, patID);
			w.setHtml(html);
			w.setVisible(true);
		});
	}

	public void setHtml(String html) {
		this.currentHtml = html == null ? "" : html;
		editor.setText(currentHtml);
		editor.setCaretPosition(0);
	}

	private void onHyperlink(HyperlinkEvent e) {
		if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
			return;
		}
		// custom schemes are not resolved by java.net.URL, so use the raw description.
		String href = e.getDescription();
		KeyImageRef ref = KeyImageRef.fromHref(href);
		if (ref != null) {
			retrieve(ref);
			return;
		}
		if (href != null && (href.startsWith("http://") || href.startsWith("https://"))) {
			browse(href);
		}
	}

	/** Object retrieval: load the referenced instance into the 2D image viewer. */
	private void retrieve(KeyImageRef ref) {
		if (ref.getSopUID() == null || ref.getStudyUID() == null || ref.getStudyUID().isEmpty()
				|| ref.getSeriesUID() == null || ref.getSeriesUID().isEmpty()) {
			Log.logger.warning("SRHtmlViewerWindow - key image link missing study/series; cannot retrieve.");
			return;
		}
		Viewer2DScreen viewer = Viewer2DScreen.getInstance();
		if (viewer == null) {
			Log.logger.info("SRHtmlViewerWindow - 2D viewer not available for retrieval.");
			return;
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		String refUID = db.getValueFromImage("FrameOfReferenceUID", patID, ref.getStudyUID(), ref.getSeriesUID(),
				ref.getSopUID());
		if (refUID == null) {
			refUID = "";
		}
		viewer.loadImagesOnStage(patID, ref.getStudyUID(), ref.getSeriesUID(), new String[] { ref.getSopUID() },
				refUID);
	}

	private void openInBrowser() {
		try {
			File tmp = Files.createTempFile("graphy-sr-", ".html").toFile();
			try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
				w.write(currentHtml);
			}
			browse(tmp.toURI().toString());
		} catch (Exception ex) {
			Log.logger.warning("SRHtmlViewerWindow - openInBrowser failed: " + ex.getMessage());
		}
	}

	private void browse(String uri) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(java.net.URI.create(uri));
			}
		} catch (Exception ex) {
			Log.logger.warning("SRHtmlViewerWindow - browse failed: " + ex.getMessage());
		}
	}
}
