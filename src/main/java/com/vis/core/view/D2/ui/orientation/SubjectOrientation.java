/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 *
 * The Initial Developer of the Original Code is
 * Raster Images
 * Portions created by the Initial Developer are Copyright (C) 2014
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Babu Hussain A
 * Devishree V
 * Meer Asgar Hussain B
 * Prakash J
 * Suresh V
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package com.vis.core.view.D2.ui.orientation;

import org.joml.Vector3d;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;

import java.awt.Color;

/**
 * 
 * https://github.com/nroduit/Weasis/tree/master/weasis-dicom/weasis-dicom-codec/src/main/java/org/weasis/dicom/codec/geometry
 *
 * @author tatsunidas
 * @version 0.1
 *
 */
public class SubjectOrientation {
	
	//how to information to use this class
	public static void main(String[] args) {
	}
	
	public static final Color blue = new Color(44783);
	public static final Color red = new Color(15539236);
	public static final Color green = new Color(897355);

    public SubjectOrientation() {
    }

    public static String getOrientation(double x, double y, double z) {
        String orientation = "";
        String orientationX = (x < 0) ? "right" : "left";
        String orientationY = (y < 0) ? "anterior" : "posterior";
        String orientationZ = (z < 0) ? "foot" : "head";
      
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);
        for (int i = 0; i < 3; ++i) {
            if ((absX > 0.0001) && (absX > absY) && (absX > absZ)) {
                orientation += orientationX;
                absX = 0;
            } else if ((absY > 0.0001) && (absY > absX) && (absY > absZ)) {
                orientation += orientationY;
                absY = 0;
            } else if (absZ > 0.0001 && absZ > absX && absZ > absY) {
                orientation += orientationZ;
                absZ = 0;
            } else {
                break;
            }
        }
        return orientation;
    }

    public static void getOrientation(String imageOrientation) {
        String imageOrientationArray[], columnRowArray[];
        imageOrientationArray = imageOrientation.split("\\\\");
        float _imgRowCosx = Float.parseFloat(imageOrientationArray[0]);
        float _imgRowCosy = Float.parseFloat(imageOrientationArray[1]);
        float _imgRowCosz = Float.parseFloat(imageOrientationArray[2]);
        float _imgColCosx = Float.parseFloat(imageOrientationArray[3]);
        float _imgColCosy = Float.parseFloat(imageOrientationArray[4]);
        float _imgColCosz = Float.parseFloat(imageOrientationArray[5]);
        columnRowArray = new String[2];
        columnRowArray[0] = SubjectOrientation.getOrientation(_imgRowCosx, _imgRowCosy, _imgRowCosz);
        columnRowArray[1] = SubjectOrientation.getOrientation(_imgColCosx, _imgColCosy, _imgColCosz);
    }

    public static String getOppositeOrientation(String orientation) {
        String oppositePrcl = "";
        char[] temp = orientation.toCharArray();
        for (char c : temp) {
            oppositePrcl += getOpposite(c);
        }
        return oppositePrcl;
    }

    public static char getOpposite(char c) {
        char opposite = ' ';
        switch (c) {
            case 'L':
                return 'R';
            case 'R':
                return 'L';
            case 'P':
                return 'A';
            case 'A':
                return 'P';
            case 'H':
                return 'F';
            case 'F':
                return 'H';
        }
        return opposite;
    }
    
    public enum Biped implements Orientation {
        R("Right", blue),
        L("Left", blue),
        A("Anterior", red),
        P("Posterior", red),
        F("Foot", green),
        H("Head", green);

        private final String fullName;
        private final Color color;

        Biped(String fullName, Color color) {
          this.fullName = fullName;
          this.color = color;
        }

        @Override
        public String getFullName() {
          return fullName;
        }

        @Override
        public String toString() {
          return fullName;
        }

        @Override
        public Color getColor() {
          return color;
        }
      }

      public enum Quadruped implements Orientation {
        RT("Right", blue),
        LE("Left", blue),
        V("Ventral", red),
        D("Dorsal", red),
        CD("Caudal", green),
        CR("Cranial", green);

        private final String fullName;
        private final Color color;

        Quadruped(String fullName, Color color) {
          this.fullName = fullName;
          this.color = color;
        }

        public String getFullName() {
          return fullName;
        }

        @Override
        public String toString() {
          return fullName;
        }

        public Color getColor() {
          return color;
        }
      }

      public static Biped getBipedXOrientation(Vector3d v) {
        return v.x < 0 ? Biped.R : Biped.L;
      }

      public static Biped getBipedYOrientation(Vector3d v) {
        return v.y < 0 ? Biped.A : Biped.P;
      }

      public static Biped getBipedZOrientation(Vector3d v) {
        return v.z < 0 ? Biped.F : Biped.H;
      }

      public static Quadruped getQuadrupedXOrientation(Vector3d v) {
        return v.x < 0 ? Quadruped.RT : Quadruped.LE;
      }

      public static Quadruped getQuadrupedYOrientation(Vector3d v) {
        return v.y < 0 ? Quadruped.V : Quadruped.D;
      }

      public static Quadruped getQuadrupedZOrientation(Vector3d v) {
        return v.z < 0 ? Quadruped.CD : Quadruped.CR;
      }

      public static Biped getOppositeOrientation(Biped val) {
    	  if(val.name().equals(Biped.R.name())) {
    		  return Biped.L;
    	  }else if(val.name().equals(Biped.L.name())) {
    		  return Biped.R;
    	  }else if(val.name().equals(Biped.A.name())) {
    		  return Biped.P;
    	  }else if(val.name().equals(Biped.P.name())) {
    		  return Biped.A;
    	  }else if(val.name().equals(Biped.F.name())) {
    		  return Biped.H;
    	  }else if(val.name().equals(Biped.H.name())) {
    		  return Biped.F;
    	  }else {
    		  return null;
    	  }
      }

      public static Quadruped getOppositeOrientation(Quadruped val) {
    	  if(val.name().equals(Quadruped.RT.name())) {
    		  return Quadruped.LE;
    	  }else if(val.name().equals(Quadruped.LE.name())) {
    		  return Quadruped.RT;
    	  }else if(val.name().equals(Quadruped.V.name())) {
    		  return Quadruped.D;
    	  }else if(val.name().equals(Quadruped.D.name())) {
    		  return Quadruped.V;
    	  }else if(val.name().equals(Quadruped.CD.name())) {
    		  return Quadruped.CR;
    	  }else if(val.name().equals(Quadruped.CR.name())) {
    		  return Quadruped.CD;
    	  }else {
    		  return null;
    	  }
      }

      /**
       * get direction cosines from Tag.ImagePositionPatient
       * @param dcm : dicom object
       * @return 3d-vector idirection cosines of ImagePositionPatient
       */
      public static Vector3d getSubjectPosition(DicomObject dcm) {
        double[] patientPosition = dcm.getDoubles(Tag.Image​Position​Patient);
        if (patientPosition != null && patientPosition.length == 3) {
          return new Vector3d(patientPosition);
        }
        return null;
      }
      
	  public static Vector3d getSubjectPosition(double[] patientPosition) {
			if (patientPosition != null && patientPosition.length == 3) {
				return new Vector3d(patientPosition);
			}
			return null;
		}
      
      public static boolean isBiped(DicomObject dcm) {
    	  //Anatomical Orientation Type (0010,2210) is absent or has a value of BIPED
    	  String val = dcm.getString(Tag.Anatomical​Orientation​Type);
    	  if(val == null || val.equals("BIPED")) {
    		  return true;
    	  }else {
    		  return false;
    	  }
      }
}