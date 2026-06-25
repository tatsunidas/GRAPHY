package com.vis.core.reporting.template;

/**
 * A reusable boilerplate snippet (定型文) that can be inserted into a report.
 * The {@link #body} is an HTML fragment compatible with the editor's
 * {@code HTMLEditorKit}.
 *
 * @author tatsunidas
 */
public class ReportTemplate {

	private String id;
	private String name; // shown in the template chooser
	private String category; // optional grouping, e.g. modality or organ
	private String body; // HTML fragment inserted at the caret

	public ReportTemplate() {
	}

	public ReportTemplate(String id, String name, String category, String body) {
		this.id = id;
		this.name = name;
		this.category = category;
		this.body = body;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return name == null ? id : name;
	}
}
