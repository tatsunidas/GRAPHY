package com.vis.core.ui.dialog;

/**
 * For Dicom tag viewer
 * @author tatsunidas
 * @version 1.0
 */
public class DicomTagModel {

	private String position;
    private String tag;
    private String tagName;
    private String VR;
    private String VM;
    private String tagLength;

    public String getTagLength() {
        return tagLength;
    }

    public void setTagLength(String tagLength) {
        this.tagLength = tagLength;
    }
    private String tagValue;

    public String getVM() {
        return VM;
    }

    public void setVM(String VM) {
        this.VM = VM;
    }

    public String getVR() {
        return VR;
    }

    public void setVR(String VR) {
        this.VR = VR;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTagValue() {
        return tagValue;
    }

    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
    }

	public String getPosition() {
		return position;
	}

	public void setPosition(String position) {
		this.position = position;
	}
}