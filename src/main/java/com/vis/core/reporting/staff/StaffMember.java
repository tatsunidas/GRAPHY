package com.vis.core.reporting.staff;

import com.vis.core.reporting.StaffRole;

/**
 * One entry in the lightweight staff directory (STAFF table): a named person with
 * a {@link StaffRole} (職種), used to populate the participant pickers in the report
 * editor so role assignment stays consistent. GRAPHY has no login, so this is a
 * convenience directory, not an authentication store.
 *
 * @author tatsunidas
 */
public class StaffMember {

	private String id;
	private String name;
	private StaffRole role;
	private String organization;
	private String department;

	public StaffMember() {
	}

	public StaffMember(String id, String name, StaffRole role, String organization, String department) {
		this.id = id;
		this.name = name;
		this.role = role;
		this.organization = organization;
		this.department = department;
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

	public StaffRole getRole() {
		return role;
	}

	public void setRole(StaffRole role) {
		this.role = role;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getDepartment() {
		return department;
	}

	public void setDepartment(String department) {
		this.department = department;
	}

	@Override
	public String toString() {
		return name == null ? "" : name;
	}
}
