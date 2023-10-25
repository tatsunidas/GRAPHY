package com.vis.core.view.D2.ui.glasses;

import java.awt.image.ColorModel;

/**
 *https://sourceforge.net/p/dcm4che/svn/3935/tree/dcm4che14/trunk/src/java/org/dcm4che/image/ColorModelParam.java
 * @author  gunter.zeilinger@tiani.com
 * @version 1.0.0
 */
public interface ColorModelParam {
    
    public ColorModel newColorModel();
    
    public ColorModelParam update(float center, float width, boolean inverse);
    
    public float getRescaleSlope();

    public float getRescaleIntercept();

    public float getWindowCenter(int index);

    public float getWindowWidth(int index);

    public int getNumberOfWindows();
    
    public float toMeasureValue(int pxValue);
    
    public int toPixelValue(float measureValue);
    
    public int toSampleValue(int pxValue);
    public int toPixelValueRaw(int sampleValue);

    public boolean isInverse();
    
    public boolean isCacheable();

	public boolean isMonochrome();
}
