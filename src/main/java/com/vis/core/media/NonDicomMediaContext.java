package com.vis.core.media;

import com.vis.dicom.UIDUtils;

public class NonDicomMediaContext {
	
	public String pname;
	public String pid;
	public String sex;
	public String dob;//yyyy/MM/dd
	
	public String studyDate;//studyDate, from DateUtils.toDicomDateString(Date)
	public String studyTime;//studyTime, from DateUtils.toDicomTimeString(Date)
	public String contentDate;//contentDate,
	public String contentTime;//contentDate,
	
	public String studyDesc;
	public String seriesDesc;
	
	public String studyUID;
	public String seriesUID;
	
	public NonDicomMediaContext() {
		studyUID = UIDUtils.createUID();
		seriesUID = UIDUtils.createUID();
	}

}
