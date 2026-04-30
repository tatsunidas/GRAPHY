package com.vis.dicom;

import java.util.Collection;
import java.util.List;


public interface Sequence extends List<DicomObject> {
	
	public boolean addTags(Collection<? extends DicomObject> c);
	
}
