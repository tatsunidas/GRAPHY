package com.vis.core.ui.main.dcmtreetable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import com.vis.core.log.Log;

/**
 * Hierarchy
 * -Root
 * -Patient
 * -STUDY
 * -SERIES
 * -IMAGE
 * @author tatsunidas
 *
 */
public class DICOMNode extends DefaultMutableTreeNode{
			
	private static final long serialVersionUID = 3509301784538157522L;
	//levels
	int level = -1;
	public final static int ROOT = 0;
	public final static int PATIENT = 1;
	public final static int STUDY = 2;
	public final static int SERIES = 3;
	public final static int IMAGE = 4;
	/*
	 * if use for QR,
	 * exchange String to Boolean:Boolean.valueOf("true");
	 */
	private HashMap<String,String> map;
	private List<DICOMNode> children;
	boolean isRoot = false;	
	
	boolean isMultiframe = false;
	boolean isVideo = false;
	String instanceUIDIfMultiframe;
	
	/*
	 * Keys
	 */
	//following keys Should be match in DB SQL
	public static final String PatientName = "PatientName";
	public static final String PatientID = "PatientID";
	public static final String StudyDate= "StudyDate";
	public static final String SeriesDate= "SeriesDate";
	public static final String StudyTime= "StudyTime";
	public static final String AcquisitionTime= "AquisitionDateTime";
	public static final String StudyDescription= "StudyDescription";
	public static final String SeriesDescription= "SeriesDescription";
	public static final String Modality= "Modality";
	public static final String Sex= "PatientSex";
	public static final String BirthDate= "PatientBirthDate";
	public static final String Age= "PatientAge";
	public static final String Institution= "Institution";
	public static final String ModelName= "ModelName";
	public static final String SeriesNumber= "SeriesNumber";
	public static final String AcquisitionNumber= "AcquisitionNumber";
	public static final String InstanceNumber= "InstanceNumber";
	public static final String AccessionNumber= "AccessionNumber";
	public static final String NumOfSeries= "NumOfSeries";
	public static final String NumOfInstances= "NumOfInstances";
	public static final String StudyInstanceUID= "StudyInstanceUID";
	public static final String SeriesInstanceUID= "SeriesInstanceUID";
	public static final String SOPInstanceUID= "SOPInstanceUID";
	
	/**
	 * check DICOMTableModel.java columnName
	 * 
	 * node level
	 * 
	 * pname			PATIENT
	 * pid				PATIENT
	 * studyDate		STUDY
	 * seriesDate		SERIES
	 * studyTime		STUDY
	 * acquisitiontime		IMAGE
	 * studyDesc		STUDY
	 * seriesDesc		SERIES
	 * modality		SERIES
	 * sex			PATIENT
	 * bod			PATIENT
	 * age			PATIENT
	 * institution	SERIES
	 * modelname		SERIES
	 * seriesNumber		SERIES
	 * acquisitionNumber	IMAGE
	 * instanceNumber		IMAGE
	 * numOfSeries		STUDY
	 * numOfInstances		STUDY/SERIES
	 * "StudyInstanceUID"	STUDY//no visible
	 * "SeriesInstanceUID"	SERIES//no visible
	 * "SOPInstanceUID"		IMAGE//no visible
	 * "FrameOfReferenceUID"  SERIES//no visible
	 * @param children
	 */
	public DICOMNode(boolean root,List<DICOMNode> children) {
		if(root) {
			this.level = DICOMNode.ROOT;
			isRoot = true;
			setData("", "", "", "", "", "", "", "",
					"", "", "", "", "", "", "", "", "", "",
					"", "", "", "", "");
			this.children = children;
			if (this.children == null) {
	            this.children = new ArrayList<>();
	        }
		}else {
			Log.logger.warning("DICOMNode:this is not root!! return.");
			return;
		}
	}
	
	/*
	 * level
	 * 0 = root
	 * 1 = patient
	 * 2 = study
	 * 3 = series
	 * 4 = image
	 */
	public DICOMNode(int level,String pname, String pid, String studyDate,
			String seriesDate, String studyTime, String acquisitiontime, String studyDesc, String seriesDesc,
			String modality, String sex, String bod, String age, String institution, String modelname,
			String seriesNumber, String acquisitionNumber, String instanceNumber,String accessionNumber,
			String numOfSeries, String numOfInstances, String studyUID, String seriesUID, String sopInstaceUID,
			List<DICOMNode> children) {
		this.level = level;
		setData(pname, pid, studyDate, seriesDate, studyTime, acquisitiontime, studyDesc, seriesDesc,
				modality, sex, bod, age, institution, modelname, seriesNumber, acquisitionNumber, instanceNumber,accessionNumber,
				numOfSeries, numOfInstances, studyUID, seriesUID, sopInstaceUID);
		this.children = children;
		if (this.children == null) {
            this.children = new ArrayList<>();
        }
	}
	
	private void setData(String pname, String pid, String studyDate, String seriesDate,
			String studyTime, String acquisitiontime, String studyDesc, String seriesDesc, String modality, String sex,
			String bod, String age, String institution, String modelName, String seriesNumber, String acquisitionNumber,
			String instanceNumber, String accessionNumber,String numOfSeries, String numOfInstances, String studyUID, String seriesUID,
			String sopInstaceUID) {
		map = new HashMap<>();
		map.put(PatientName, pname);
		map.put(PatientID, pid);
		map.put(StudyDate, studyDate);
		map.put(SeriesDate, seriesDate);
		map.put(StudyTime, studyTime);
		map.put(AcquisitionTime, acquisitiontime);
		map.put(StudyDescription, studyDesc);
		map.put(SeriesDescription, seriesDesc);
		map.put(Modality, modality);
		map.put(Sex, sex);
		map.put(BirthDate, bod);
		map.put(Age, age);
		map.put(Institution, institution);
		map.put(ModelName, modelName);
		map.put(SeriesNumber, seriesNumber);
		map.put(AcquisitionNumber, acquisitionNumber);
		map.put(InstanceNumber, instanceNumber);
		map.put(AccessionNumber, accessionNumber);
		map.put(NumOfSeries, numOfSeries);
		map.put(NumOfInstances, numOfInstances);
		map.put(StudyInstanceUID, studyUID);
		map.put(SeriesInstanceUID, seriesUID);
		map.put(SOPInstanceUID, sopInstaceUID);
	}
	
	public String getData(String key) {
		if(key.equals(BirthDate)) {
			String bod = map.get(key);
			if(bod == null) {
				return null;
			}else {
				return bod.replace("-", "/");
			}
		}
		return map.get(key);
	}
	
	public void setData(String key, String value) {
		if(!map.containsKey(key)) {
			Log.logger.warning("DICOMNode::Map does not contain this key...");
			return;
		}
		map.put(key,value);//replace value
	}
	
	public List<DICOMNode> getChildren() {
        return children;
    }
	
	public void setChildren(List<DICOMNode> children) {
        this.children = children;
    }
	
	public void replaceParticularChildrenNode(int candidateNodeLevel, String uidOfWillSetNode, DICOMNode childNode) {
		if (candidateNodeLevel == PATIENT || candidateNodeLevel == ROOT) {
			Log.logger.warning("DICOMNode:setParticularNodeChildren::you should set study/series/image level...");
			return;
		}
		if (!isLowerLevel(candidateNodeLevel)) {
			Log.logger.warning("DICOMNode:setParticularNodeChildren::you should set candidate node child level...");
			return;
		}
		
		List<DICOMNode> childs = getChildren();
		int targetLocation = -1;
		for (int i = 0; i < childs.size(); i++) {
			if (getLevel() == STUDY) {
				if (childs.get(i).getData(DICOMNode.StudyInstanceUID).equals(uidOfWillSetNode)) {
					targetLocation = i;
					break;
				}
			}else if(getLevel() == SERIES) {
				if (childs.get(i).getData(DICOMNode.SeriesInstanceUID).equals(uidOfWillSetNode)) {
					targetLocation = i;
					break;
				}
			}
		}
		childs.set(targetLocation, childNode);
	}
	
	public boolean isLowerLevel(int childLevel){
		if(getLevel()<childLevel) {
			return true;
		}else {
			return false;
		}
	}
	
    @Override
	public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    public Object getChild(int index) {
        return this.children.get(index);
    }

    @Override
	public DICOMNode getFirstChild() {
        return children.get(0);
    }

    public void addChild(DICOMNode child) {
        this.children.add(child);
    }

	public void setChildren(List<DICOMNode> children, boolean isRoot) {
		this.children = null;
		this.children = children;
		this.isRoot = isRoot;
	}
	
	@Override
	public boolean isRoot() {
        return isRoot;
    }
	
	@Override
	public int getLevel() {
		return level;
	}
	
	public String getLevelString() {
		if(this.level==PATIENT) {
			return "PATIENT";
		}else if(this.level==STUDY) {
			return "STUDY";
		}else if(this.level==SERIES) {
			return "SERIES";
		}else {
			return "IMAGE";
		}
	}
	
    public boolean isMultiframe() {
        return isMultiframe;
    }

    public void setMultiframe(boolean isMultiframe) {
        this.isMultiframe = isMultiframe;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideoStatus(boolean isVideo) {
        this.isVideo = isVideo;
    }
    
    public String getInstanceUIDIfMultiframe() {
        return instanceUIDIfMultiframe;
    }

    public void setInstanceUIDIfMultiframe(String instanceUIDIfMultiframe) {
        this.instanceUIDIfMultiframe = instanceUIDIfMultiframe;
    }
    
    public void remove(int index) {
    	/*
    	 * or
    	 * children.replace(index,null);
    	 * children....remove null use Collections....
    	 */
        this.children.remove(index);
    }
}
