package com.vis.core.reporting.template;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vis.core.log.Log;

/**
 * Loads boilerplate report templates (定型文). Phase 1 backs them with a bundled
 * JSON resource ({@code reporting/templates.json}); a DB-backed / user-editable
 * store is a future phase. Designed so {@code ReportEditorDialog} can list and
 * insert templates.
 *
 * @author tatsunidas
 */
public class ReportTemplateStore {

	private static final String RESOURCE = "reporting/templates.json";

	private List<ReportTemplate> templates;

	/** @return all templates (bundled). Cached after first load. */
	public synchronized List<ReportTemplate> getTemplates() {
		if (templates == null) {
			templates = loadBundled();
		}
		return templates;
	}

	private List<ReportTemplate> loadBundled() {
		List<ReportTemplate> list = new ArrayList<>();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		InputStream is = cl == null ? null : cl.getResourceAsStream(RESOURCE);
		if (is == null) {
			is = ReportTemplateStore.class.getResourceAsStream("/" + RESOURCE);
		}
		if (is == null) {
			Log.logger.info("ReportTemplateStore - no bundled templates found at " + RESOURCE);
			return list;
		}
		try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			Type type = new TypeToken<ArrayList<ReportTemplate>>() {
			}.getType();
			List<ReportTemplate> parsed = new Gson().fromJson(reader, type);
			if (parsed != null) {
				list.addAll(parsed);
			}
		} catch (Exception e) {
			Log.logger.warning("ReportTemplateStore - failed to read templates: " + e.getMessage());
		}
		return list;
	}
}
