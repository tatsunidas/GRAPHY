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
 * Visionary Imaging Srvices, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015-2021
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * 
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
package com.vis.dicom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.dcm4che3.data.UID;


/**
 * @author tatsunidas
 * @version 0.1
 */
public class DicomCommunicationNode {

    int pk;
    String nickname = "";
    String aeTitle = "";
    String hostName = "";
    int port;
    ArrayList<String> ciphers;
    String retrieveType = "";
    boolean previewEnabled;//how to use it ? tatsu
    private int wadoPort;
    private String wadoContextPath = "";
    private String wadoProtocol = "";
    private String retrieveTransferSyntax = "";
    
    public DicomCommunicationNode(HashMap<String,Object> nodeMaterials) {
		this.nickname = (String)nodeMaterials.get("logicalname");
		this.aeTitle = (String)nodeMaterials.get("aetitle");
		this.hostName = (String)nodeMaterials.get("hostname");
		if(this.hostName.equals("")) {
			this.hostName = "localhost";
		}
		this.port = (Integer)nodeMaterials.get("port");
		cipherStringToList((String)nodeMaterials.get("ciphers"));// set this.ciphers
		this.retrieveType = (String)nodeMaterials.get("retrievetype");
		this.wadoContextPath = (String)nodeMaterials.get("wadocontext");
		this.wadoPort = (Integer)nodeMaterials.get("wadoport");
		this.wadoPort = this.wadoPort != 0 ? this.wadoPort : 8080;
		this.wadoProtocol = (String)nodeMaterials.get("wadoprotocol");
		if ((String)nodeMaterials.get("retrievets") == null || ((String)nodeMaterials.get("retrievets")).equals("")) {
			this.retrieveTransferSyntax = UID.ExplicitVRLittleEndian;
		}else {
			if (((String)nodeMaterials.get("retrievets")).equals("Explicit VR Little Endian")) {
				this.retrieveTransferSyntax = UID.ExplicitVRLittleEndian;
			} else if (((String)nodeMaterials.get("retrievets")).equals("Implicit VR Little Endian")) {
				this.retrieveTransferSyntax = UID.ImplicitVRLittleEndian;
			} 
		}
    }

	public DicomCommunicationNode(String nickname, String aet, String host, int port, ArrayList<String> ciphers) {
		this.nickname = nickname;
		this.aeTitle = aet;
		this.hostName = host != null ? host : "localhost";
		this.port = port;
		this.ciphers = ciphers;
	}
    
	public DicomCommunicationNode(String nickname, String aet, String host, int port, String ciphersSequence) {
		this.nickname = nickname;
		this.aeTitle = aet;
		this.hostName = host != null ? host : "localhost";
		this.port = port;
		cipherStringToList(ciphersSequence);// set this.cipher variables.
	}

	public DicomCommunicationNode(String serverName, String aeTitle, String host, Integer port, String cipherString,
			String retrieveType, String wadoContext, Integer wadoPort, String wadoProtocol, String retrieveTS) {
		this.nickname = serverName;
		this.aeTitle = aeTitle;
		this.hostName = host != null ? host : "localhost";
		this.port = port;
		cipherStringToList(cipherString);// set this.ciphers
		this.retrieveType = retrieveType;
		this.wadoContextPath = wadoContext;
		this.wadoPort = wadoPort != 0 ? wadoPort : 8080;
		this.wadoProtocol = wadoProtocol;
		if (retrieveTS == null) {
			this.retrieveTransferSyntax = UID.ExplicitVRLittleEndian;
		}else {
			if (retrieveTS.equals("Explicit VR Little Endian")) {
				this.retrieveTransferSyntax = UID.ExplicitVRLittleEndian;
			} else if (retrieveTS.equals("Implicit VR Little Endian")) {
				this.retrieveTransferSyntax = UID.ImplicitVRLittleEndian;
			} 
		}
		
	}
	
	public HashMap<String, Object> getNodeMaterials(){
		HashMap<String,Object> nodeMaterials = new HashMap<String, Object>();
		nodeMaterials.put("logicalname", nickname);
		nodeMaterials.put("aetitle", aeTitle);
		nodeMaterials.put("hostname", hostName);
		nodeMaterials.put("port", port);
		nodeMaterials.put("ciphers", ciphers);
		nodeMaterials.put("retrievetype", retrieveType);
		nodeMaterials.put("wadocontext", wadoContextPath);
		nodeMaterials.put("wadoport", wadoPort);
		nodeMaterials.put("wadoprotocol", wadoProtocol);
		nodeMaterials.put("retrievets", retrieveTransferSyntax);
		return nodeMaterials;
	}
    
    /*
     * 0:AET
     * 1:host
     * 2:port
     * >=3:ciphers
     */
    public ArrayList<String> readNodeInfoFromAEPropAt(Properties prop, String AET) {
    	ArrayList<String> nodeInfo = new ArrayList<>();
    	String nodeInfoSequence = prop.getProperty(AET, null);
    	String lastChar = "willNUll";
    	/* <ae-title>=<hostname>:<port>[:cipher1[:...]] */
    	if(nodeInfoSequence == null) {
    		return null;
    	}else {
    		nodeInfo.add(AET);
    	}
    	while(lastChar != null) {
    		if(nodeInfoSequence.length()<1) {
    			lastChar = null;
    		}
    		if(nodeInfoSequence.contains(":")) {
				nodeInfo.add(nodeInfoSequence.substring(0, nodeInfoSequence.indexOf(":")));
				nodeInfoSequence = nodeInfoSequence.substring(nodeInfoSequence.indexOf(":") + 1,
						nodeInfoSequence.length());
    		}else {
    			nodeInfo.add(nodeInfoSequence.trim());
    			lastChar = null;
    		}    		
    	}
    	return nodeInfo;
    }
    
    public void setNodeInformation(ArrayList<String> nodeInfo){
    	this.aeTitle = nodeInfo.get(0);
    	this.hostName = nodeInfo.get(1);
    	this.port = Integer.parseInt(nodeInfo.get(2));
    	if(nodeInfo.size()>3) {
    		this.ciphers = new ArrayList<String>();
    		for(int i=3; i<nodeInfo.size();i++) {
    			this.ciphers.add(nodeInfo.get(i));
    		}
    	}
    }
    
    public String getNodeInfoAsStringSequence() {
    	String seq = hostName+":"+String.valueOf(port);//+":"+cipherListToString4Prop();
    	if(!cipherListToString4Prop().equals("")) {
    		seq = seq+":"+cipherListToString4Prop();
    	}
    	return seq;
    }

    public void setWadoInformation(String wadoProtocol, String wadocontext, String hostName, int wadoPort) {
        this.wadoProtocol = wadoProtocol;
        this.wadoContextPath = wadocontext;
        this.hostName = hostName;
        this.wadoPort = wadoPort;
    }

    public int getPk() {
        return pk;
    }

    public void setPk(int pk) {
        this.pk = pk;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRetrieveTransferSyntax() {
        return retrieveTransferSyntax;
    }

    public void setRetrieveTransferSyntax(String retrieveTransferSyntax) {
        this.retrieveTransferSyntax = retrieveTransferSyntax;
    }

    public String getRetrieveType() {
        return retrieveType;
    }

    public void setRetrieveType(String retrieveType) {
        this.retrieveType = retrieveType;
    }

    public String getWadoURL() {
        return wadoContextPath;
    }

    public void setWadoContextPath(String wadoContextPath) {
        this.wadoContextPath = wadoContextPath;
    }

    public int getWadoPort() {
        return wadoPort;
    }

    public void setWadoPort(int wadoPort) {
        this.wadoPort = wadoPort;
    }

    public String getWadoProtocol() {
        return wadoProtocol;
    }

    public void setWadoProtocol(String wadoProtocol) {
        this.wadoProtocol = wadoProtocol;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    public void setCipherFromStringSequence(String ciphers) {
    	cipherStringToList(ciphers);
    }
    
    public void setCipher(ArrayList<String> ciphers) {
        this.ciphers = ciphers;
    }
    
    public String getCipher() {
        return cipherListToString();
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }
    
    public String cipherListToString(){
		if(ciphers == null || ciphers.size()<1) {
			return null;
		}
		String ciphersSequence = "";
		for(int i=0;i<ciphers.size();i++) {
			if(i==0) {
				ciphersSequence = ciphersSequence + ciphers.get(i);
			}else {
				ciphersSequence = ciphersSequence + "," + ciphers.get(i);
			}
		}
		return ciphersSequence;
	}
    
    public String cipherListToString4Prop(){
		if(ciphers == null || ciphers.size()<1) {
			return "";
		}
		String ciphersSequence = "";
		for(int i=0;i<ciphers.size();i++) {
			if(i==0) {
				ciphersSequence = ciphersSequence + ciphers.get(i);
			}else {
				ciphersSequence = ciphersSequence + ":" + ciphers.get(i);
			}
		}
		return ciphersSequence;
	}
    
    public void cipherStringToList(String seq){
		if(seq == null || seq.equals("")) {
			return;
		}
		this.ciphers = new ArrayList<String>();
		String willNull = "";
		while(willNull != null) {
    		if(seq.length()<1) {
    			willNull = null;
    			break;
    		}
    		if(seq.contains(":")) {
				ciphers.add(seq.substring(0, seq.indexOf(":")).trim());
				seq = seq.substring(seq.indexOf(":") + 1,seq.length());
    		}else if(seq.contains(",")){
    			ciphers.add(seq.substring(0, seq.indexOf(",")).trim());
				seq = seq.substring(seq.indexOf(",") + 1,seq.length());
    		}else {
    			//last
    			ciphers.add(seq.trim());
    			willNull = null;
    		}    		
    	}
	}
}