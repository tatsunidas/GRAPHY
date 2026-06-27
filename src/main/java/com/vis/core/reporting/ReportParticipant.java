package com.vis.core.reporting;

/**
 * One person involved in a report, pairing a {@link ParticipationType} (how they
 * are involved: author / verifier / enterer / reviewer) with a {@link StaffRole}
 * (their job: physician / technologist / ...). Persisted as part of
 * {@link ReportDocument} (JSON) and exported into the SR observer/participant
 * sequences (see {@code SrCommon.addObservers}).
 *
 * @author tatsunidas
 */
public class ReportParticipant {

	private String name;                 // PN, e.g. "Yamada^Taro" or a display name
	private StaffRole role;              // 職種; nullable (optional, e.g. legacy data)
	private ParticipationType participation = ParticipationType.AUTHOR;
	private String organization;        // institution name; nullable
	private String staffId;             // optional FK into the STAFF directory
	private long dateTimeMillis;        // optional participation/verification time; 0 = unset

	public ReportParticipant() {
	}

	public ReportParticipant(String name, StaffRole role, ParticipationType participation) {
		this.name = name;
		this.role = role;
		this.participation = participation == null ? ParticipationType.AUTHOR : participation;
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

	public ParticipationType getParticipation() {
		return participation;
	}

	public void setParticipation(ParticipationType participation) {
		this.participation = participation == null ? ParticipationType.AUTHOR : participation;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getStaffId() {
		return staffId;
	}

	public void setStaffId(String staffId) {
		this.staffId = staffId;
	}

	public long getDateTimeMillis() {
		return dateTimeMillis;
	}

	public void setDateTimeMillis(long dateTimeMillis) {
		this.dateTimeMillis = dateTimeMillis;
	}

	public boolean hasName() {
		return name != null && !name.trim().isEmpty();
	}
}
