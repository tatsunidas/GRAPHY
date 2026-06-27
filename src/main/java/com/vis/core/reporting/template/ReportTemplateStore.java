package com.vis.core.reporting.template;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * Loads and manages boilerplate report templates (定型文).
 * <p>
 * Storage layers (TD-2):
 * <ol>
 *   <li><b>Bundled</b> — read-only, from the classpath JAR
 *       ({@code reporting/templates.json}). Never stored in the DB.</li>
 *   <li><b>User-defined</b> — stored in the {@code REPORT_TEMPLATE} Derby table.
 *       On first launch, templates found in the legacy JSON file
 *       ({@code ~/.graphy/report-templates.json}) are automatically migrated to
 *       the DB and the JSON file is left in place as a backup.</li>
 * </ol>
 * The combined list (bundled first, then user) is what the editor combo-box shows.
 *
 * @author tatsunidas
 */
public class ReportTemplateStore {

    private static final String RESOURCE  = "reporting/templates.json";
    private static final String LEGACY_FILE =
            System.getProperty("user.home") + "/.graphy/report-templates.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<ReportTemplate>>() {}.getType();

    private List<ReportTemplate> bundled;
    private List<ReportTemplate> user;

    // ---- Public read API ---------------------------------------------------

    /** @return all templates (bundled + user), cached after first load. */
    public synchronized List<ReportTemplate> getTemplates() {
        if (bundled == null) bundled = loadBundled();
        if (user    == null) user    = loadUser();
        List<ReportTemplate> all = new ArrayList<>(bundled);
        all.addAll(user);
        return all;
    }

    /** Force reload from storage on the next call to {@link #getTemplates()}. */
    public synchronized void invalidate() {
        bundled = null;
        user    = null;
    }

    // ---- User template CRUD -----------------------------------------------

    public synchronized void addUserTemplate(ReportTemplate t) {
        ensureLoaded();
        if (t.getId() == null || t.getId().isEmpty()) {
            t.setId("user-" + UUID.randomUUID());
        }
        dbUpsert(t);
        user.add(t);
    }

    public synchronized void updateUserTemplate(ReportTemplate t) {
        ensureLoaded();
        dbUpsert(t);
        for (int i = 0; i < user.size(); i++) {
            if (t.getId().equals(user.get(i).getId())) {
                user.set(i, t);
                return;
            }
        }
        user.add(t); // fallback: wasn't in cache yet
    }

    public synchronized void removeUserTemplate(String id) {
        ensureLoaded();
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db != null) db.deleteTemplate(id);
        user.removeIf(t -> id.equals(t.getId()));
    }

    public boolean isUserTemplate(ReportTemplate t) {
        ensureLoaded();
        return user.stream().anyMatch(u -> u.getId() != null && u.getId().equals(t.getId()));
    }

    /** @return a mutable copy of user-only templates. */
    public synchronized List<ReportTemplate> getUserTemplates() {
        ensureLoaded();
        return new ArrayList<>(user);
    }

    // ---- Private helpers --------------------------------------------------

    private void ensureLoaded() {
        if (bundled == null) bundled = loadBundled();
        if (user    == null) user    = loadUser();
    }

    private List<ReportTemplate> loadBundled() {
        List<ReportTemplate> list = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream is = cl == null ? null : cl.getResourceAsStream(RESOURCE);
        if (is == null) is = ReportTemplateStore.class.getResourceAsStream("/" + RESOURCE);
        if (is == null) {
            Log.logger.info("ReportTemplateStore - no bundled templates at " + RESOURCE);
            return list;
        }
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            List<ReportTemplate> parsed = GSON.fromJson(reader, LIST_TYPE);
            if (parsed != null) list.addAll(parsed);
        } catch (Exception e) {
            Log.logger.warning("ReportTemplateStore - failed to read bundled templates: " + e.getMessage());
        }
        return list;
    }

    private List<ReportTemplate> loadUser() {
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db == null) {
            return loadLegacyJson(); // no DB yet (test / early startup)
        }

        // Migrate legacy JSON → DB on first launch
        if (!db.hasTemplates()) {
            List<ReportTemplate> legacy = loadLegacyJson();
            if (!legacy.isEmpty()) {
                Log.logger.info("ReportTemplateStore - migrating " + legacy.size()
                        + " user templates from JSON to DB");
                for (ReportTemplate t : legacy) {
                    if (t.getId() == null) t.setId("user-" + UUID.randomUUID());
                    db.upsertTemplate(t.getId(), t.getName(), t.getCategory(), t.getBody());
                }
            }
        }

        // Load from DB
        List<ReportTemplate> out = new ArrayList<>();
        for (String[] row : db.loadAllTemplates()) {
            ReportTemplate t = new ReportTemplate(row[0], row[1], row[2], row[3]);
            out.add(t);
        }
        return out;
    }

    private List<ReportTemplate> loadLegacyJson() {
        File f = new File(LEGACY_FILE);
        if (!f.exists()) return new ArrayList<>();
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            List<ReportTemplate> parsed = GSON.fromJson(reader, LIST_TYPE);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            Log.logger.warning("ReportTemplateStore - failed to read legacy JSON: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void dbUpsert(ReportTemplate t) {
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db != null) {
            db.upsertTemplate(t.getId(), t.getName(), t.getCategory(), t.getBody());
        }
    }
}
