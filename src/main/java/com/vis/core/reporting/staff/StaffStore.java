package com.vis.core.reporting.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.vis.core.log.Log;
import com.vis.core.reporting.StaffRole;
import com.vis.db.DatabaseHandler;

/**
 * DB-backed directory of staff members (STAFF table), mirroring the
 * {@code ReportTemplateStore} pattern. When no DB is available (tests / early
 * startup) it degrades to an in-memory list so the editor still works.
 *
 * @author tatsunidas
 */
public class StaffStore {

	private List<StaffMember> cache;

	/** @return all staff members, cached after first load. */
	public synchronized List<StaffMember> getStaff() {
		if (cache == null) {
			cache = load();
		}
		return new ArrayList<>(cache);
	}

	/** Force reload from storage on the next call. */
	public synchronized void invalidate() {
		cache = null;
	}

	public synchronized void addStaff(StaffMember s) {
		ensureLoaded();
		if (s.getId() == null || s.getId().isEmpty()) {
			s.setId("staff-" + UUID.randomUUID());
		}
		dbUpsert(s);
		cache.add(s);
	}

	public synchronized void updateStaff(StaffMember s) {
		ensureLoaded();
		dbUpsert(s);
		for (int i = 0; i < cache.size(); i++) {
			if (s.getId() != null && s.getId().equals(cache.get(i).getId())) {
				cache.set(i, s);
				return;
			}
		}
		cache.add(s);
	}

	public synchronized void removeStaff(String id) {
		ensureLoaded();
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			db.deleteStaff(id);
		}
		cache.removeIf(s -> id != null && id.equals(s.getId()));
	}

	// ---- helpers -----------------------------------------------------------

	private void ensureLoaded() {
		if (cache == null) {
			cache = load();
		}
	}

	private List<StaffMember> load() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return new ArrayList<>();
		}
		List<StaffMember> out = new ArrayList<>();
		for (String[] row : db.loadAllStaff()) {
			out.add(new StaffMember(row[0], row[1], StaffRole.fromName(row[2]), row[3], row[4]));
		}
		return out;
	}

	private void dbUpsert(StaffMember s) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			db.upsertStaff(s.getId(), s.getName(),
					s.getRole() == null ? null : s.getRole().name(),
					s.getOrganization(), s.getDepartment());
		}
	}
}
