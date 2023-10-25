package com.vis.core.view.D2.ui.orientation;

/**
 *
 * @author BabuHussain
 * @version 0.6
 *
 */
public class ScoutLineInfoModel {

    private String imagePosition;
    private String imageOrientation;
    private String imagePixelSpacing;
    private int imageRow;
    private int imageColumn;
    private String imageFrameofReferenceUID;
    private String imageReferenceSOPInstanceUID;
    private String imageType;
    private String sliceLocation;

    public ScoutLineInfoModel() {
    }

    public ScoutLineInfoModel(String imagePos, String imgOrientation, String pixelSpacing, int row, int column, String frameOfReferenceUid, String referencedSopUid, String imageType, String sliceLocation) {
        this.imagePosition = imagePos;
        this.imageOrientation = imgOrientation;
        this.imagePixelSpacing = pixelSpacing;
        this.imageRow = row;
        this.imageColumn = column;
        this.imageFrameofReferenceUID = frameOfReferenceUid;
        this.imageReferenceSOPInstanceUID = referencedSopUid;
        this.imageType = imageType;
        this.sliceLocation = sliceLocation;
    }

    public String getImageFrameofReferenceUID() {
        return imageFrameofReferenceUID;
    }

    public void setImageFrameofReferenceUID(String imageFrameofReferenceUID) {
        this.imageFrameofReferenceUID = imageFrameofReferenceUID;
    }

    public String getImageOrientation() {
        return imageOrientation;
    }

    public void setImageOrientation(String imageOrientation) {
        this.imageOrientation = imageOrientation;
    }

    public String getImagePixelSpacing() {
        return imagePixelSpacing;
    }

    public void setImagePixelSpacing(String imagePixelSpacing) {
        this.imagePixelSpacing = imagePixelSpacing;
    }

    public String getImagePosition() {
        return imagePosition;
    }

    public void setImagePosition(String imagePosition) {
        this.imagePosition = imagePosition;
    }

    public String getImageReferenceSOPInstanceUID() {
        return imageReferenceSOPInstanceUID;
    }

    public void setImageReferenceSOPInstanceUID(String imageReferenceSOPInstanceUID) {
        this.imageReferenceSOPInstanceUID = imageReferenceSOPInstanceUID;
    }

    public int getImageColumn() {
        return imageColumn;
    }

    public void setImageColumn(int imageColumn) {
        this.imageColumn = imageColumn;
    }

    public int getImageRow() {
        return imageRow;
    }

    public void setImageRow(int imageRow) {
        this.imageRow = imageRow;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getSliceLocation() {
        return sliceLocation;
    }

    public void setSliceLocation(String sliceLocation) {
        this.sliceLocation = sliceLocation;
    }
}