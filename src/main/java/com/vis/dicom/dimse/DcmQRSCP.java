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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
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

package com.vis.dicom.dimse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.media.DicomDirReader;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationStateException;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.BasicCGetSCP;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.BasicRetrieveTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.net.service.InstanceLocator;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.dcm4che3.net.service.QueryTask;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.common.FilesetInfo;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;

import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;
import com.vis.db.DicomServer;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomUtilities;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Tatsuaki Kobayashi <t_kobayashi@vis-ionary.com>
 *
 */
public class DcmQRSCP implements DicomServer{

	static Logger LOG = Log.logger;
	private static final String[] PATIENT_ROOT_LEVELS = { "PATIENT", "STUDY", "SERIES", "IMAGE" };
	private static final String[] STUDY_ROOT_LEVELS = { "STUDY", "SERIES", "IMAGE" };
	private static final String[] PATIENT_STUDY_ONLY_LEVELS = { "PATIENT", "STUDY" };
	private static ResourceBundle rb = ResourceBundle.getBundle("dcmqrscp.dcmqrscp-help");

	protected final Device device = new Device("dcmqrscp");
	protected final ApplicationEntity ae = new ApplicationEntity("*");
	protected final Connection conn = new Connection();

	private File storageDir;
	private File dicomDir;
	private AttributesFormat filePathFormat;
	private RecordFactory recFact;
	private String availability;
	private boolean stgCmtOnSameAssoc;
	private boolean sendPendingCGet;
	private int sendPendingCMoveInterval;
	private int delayCFind;
	private int delayCStore;
	private boolean ignoreCaseOfPN;
	private boolean matchNoValue;
	protected final FilesetInfo fsInfo = new FilesetInfo();
	private DicomDirReader ddReader;
	private DicomDirWriter ddWriter;
	private HashMap<String, Connection> remoteConnections = new HashMap<String, Connection>();

	public boolean useDicomDir = false;

	private final class CStoreSCPImpl extends BasicCStoreSCP {

		CStoreSCPImpl() {
			super("*");
		}

		@Override
		protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
				throws IOException {
			String cuid = rq.getString(Tag.AffectedSOPClassUID);
			String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
			String tsuid = pc.getTransferSyntax();
			File file = new File(storageDir, iuid);
			try {
				Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
				storeTo(as, fmi, data, file);
				Attributes attrs = parse(file);
				File dest = getDestinationFile(attrs);
				renameTo(as, file, dest);
				file = dest;
				if (!useDicomDir) {
					//writeDB
					/* "DICOM/{00100020,hash}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}" */
					writeGraphyDB(attrs, dest);
					LOG.info("DB-WROTE\n"+as);
				} else {
					if (addDicomDirRecords(as, attrs, fmi, file)) {
						LOG.info("DICOMDIR-UPDATE\n"+as);
						//and also, write to db
						writeGraphyDB(attrs, dest);
					} else {
						LOG.info("{}: ignore received object\n"+as);
						deleteFile(as, file);
					}
				}
			} catch (Exception e) {
				deleteFile(as, file);
				throw new DicomServiceException(Status.ProcessingFailure, e);
			}
		}
	};

	/*
	 * Storage Commitment
	 * This is a DICOM service which allows a creator of images or other Composite Instances to check 
	 * that they have been stored safely by a server (the SCP) before deleting from its own cache.
	 */
	private final class StgCmtSCPImpl extends AbstractDicomService {

		public StgCmtSCPImpl() {
			super(UID.StorageCommitmentPushModel);
		}

		@Override
		public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes rq, Attributes actionInfo)
				throws IOException {
			if (dimse != Dimse.N_ACTION_RQ)
				throw new DicomServiceException(Status.UnrecognizedOperation);

			int actionTypeID = rq.getInt(Tag.ActionTypeID, 0);
			if (actionTypeID != 1)
				throw new DicomServiceException(Status.NoSuchActionType).setActionTypeID(actionTypeID);

			Attributes rsp = Commands.mkNActionRSP(rq, Status.Success);
			String callingAET = as.getCallingAET();
			String calledAET = as.getCalledAET();
			Connection remoteConnection = getRemoteConnection(callingAET);
			if (remoteConnection == null)
				throw new DicomServiceException(Status.ProcessingFailure, "Unknown Calling AET: " + callingAET);
			Attributes eventInfo = null;
			if(useDicomDir) {
				eventInfo = calculateStorageCommitmentResultFromDcmDir(calledAET, actionInfo);
			}else {
				//not tested
				eventInfo = calculateStorageCommitmentResultFromDB(calledAET, actionInfo);
			}
			try {
				as.writeDimseRSP(pc, rsp, null);
				device.execute(new SendStgCmtResult(as, eventInfo, stgCmtOnSameAssoc, remoteConnection));
			} catch (AssociationStateException e) {
				LOG.warning(" N-ACTION-RSP failed:"+as+"\n"+e.getMessage());
			}
		}
	}

	private final class CFindSCPImpl extends BasicCFindSCP {

		private final String[] qrLevels;
		private final QueryRetrieveLevel rootLevel;

		public CFindSCPImpl(String sopClass, String... qrLevels) {
			super(sopClass);
			this.qrLevels = qrLevels;
			this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
		}

		/*
		 * (non-Javadoc)
		 * @see org.dcm4che3.net.service.BasicCFindSCP#calculateMatches(org.dcm4che3.net.Association, org.dcm4che3.net.pdu.PresentationContext, org.dcm4che3.data.Attributes, org.dcm4che3.data.Attributes)
		 * DicomDirを使うパターンは、戻り値のAttributesをDirectoryRecordに合わせている。
		 * http://dicom.nema.org/dicom/2013/output/chtml/part03/sect_F.5.html
		 * DBを使うパターンは、これに関わらず、必要な値のみを返す。
		 * QueryTaskクラスは継承で変数が繋がっているので注意。
		 */
		@Override
		protected QueryTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
				throws DicomServiceException {
			QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(keys, qrLevels);
			level.validateQueryKeys(keys, rootLevel, rootLevel == QueryRetrieveLevel.IMAGE || relational(as, rq));
			switch (level) {
			case PATIENT:
				if(useDicomDir) {
					return new PatientQueryTaskUsingDcmDir(as, pc, rq, keys, DcmQRSCP.this);
				}else {
					return new PatientQueryTaskUsingDB(as, pc, rq, keys, DcmQRSCP.this);
				}
			case STUDY:
				if(useDicomDir) {
					return new StudyQueryTaskUsingDcmDir(as, pc, rq, keys, DcmQRSCP.this);
				}else {
					return new StudyQueryTaskUsingDB(as, pc, rq, keys, DcmQRSCP.this);
				}
			case SERIES:
				if(useDicomDir) {
					return new SeriesQueryTaskUsingDcmDir(as, pc, rq, keys, DcmQRSCP.this);
				}else {
					return new SeriesQueryTaskUsingDB(as, pc, rq, keys, DcmQRSCP.this);
				}
			case IMAGE:
				if(useDicomDir) {
					return new InstanceQueryTaskUsingDcmDir(as, pc, rq, keys, DcmQRSCP.this);
				}else {
					return new InstanceQueryTaskUsingDB(as, pc, rq, keys, DcmQRSCP.this);
				}
			default:
				assert true;
			}
			throw new AssertionError();
		}

		private boolean relational(Association as, Attributes rq) {
			String cuid = rq.getString(Tag.AffectedSOPClassUID);
			ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
			return QueryOption.toOptions(extNeg).contains(QueryOption.RELATIONAL);
		}
	}

	private final class CGetSCPImpl extends BasicCGetSCP {

		private final String[] qrLevels;
		private final boolean withoutBulkData;
		private final QueryRetrieveLevel rootLevel;

		public CGetSCPImpl(String sopClass, String... qrLevels) {
			super(sopClass);
//			System.out.println(sopClass);//1.2.840.10008.5.1.4.1.2.1.3	Patient Root Query/Retrieve Information Model – GET
//			System.out.println(qrLevels.length+" "+qrLevels[0]+" "+qrLevels[1]+" "+qrLevels[2]+" "+qrLevels[3]);//4 PATIENT STUDY SERIES IMAGE
			this.qrLevels = qrLevels;
			this.withoutBulkData = qrLevels.length == 0;
			this.rootLevel = withoutBulkData ? QueryRetrieveLevel.IMAGE : QueryRetrieveLevel.valueOf(qrLevels[0]);//qrLevels[0]=PATIENT
		}

		/*
		 * (non-Javadoc)
		 * @see org.dcm4che3.net.service.BasicCGetSCP#calculateMatches(org.dcm4che3.net.Association, org.dcm4che3.net.pdu.PresentationContext, org.dcm4che3.data.Attributes, org.dcm4che3.data.Attributes)
		 * RetrieveTaskは要求に答えてこちらからデータを送ってあげるサービス。
		 */
		@Override
		protected RetrieveTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
				throws DicomServiceException {
			QueryRetrieveLevel level = withoutBulkData ? QueryRetrieveLevel.IMAGE
					: QueryRetrieveLevel.valueOf(keys, qrLevels);
			level.validateRetrieveKeys(keys, rootLevel, relational(as, rq));//StudyIUID check
			List<InstanceLocator> matches = null;
						
			if(useDicomDir) {
				matches = DcmQRSCP.this.calculateMatches(keys);
			}else {
				matches = DcmQRSCP.this.calculateMatchesFromDB(keys);
			}
			if (matches.isEmpty())
				return null;

			RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(Dimse.C_GET_RQ, as, pc, rq, matches, as,
					withoutBulkData, delayCStore);
			retrieveTask.setSendPendingRSP(isSendPendingCGet());
			return retrieveTask;
		}

		private boolean relational(Association as, Attributes rq) {
			String cuid = rq.getString(Tag.AffectedSOPClassUID);
			ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
			return QueryOption.toOptions(extNeg).contains(QueryOption.RELATIONAL);
		}

	}

	private final class CMoveSCPImpl extends BasicCMoveSCP {

		private final String[] qrLevels;
		private final QueryRetrieveLevel rootLevel;

		public CMoveSCPImpl(String sopClass, String... qrLevels) {
			super(sopClass);
			this.qrLevels = qrLevels;
			this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
		}

		@Override
		protected RetrieveTask calculateMatches(Association as, PresentationContext pc, final Attributes rq,
				Attributes keys) throws DicomServiceException {
			QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(keys, qrLevels);
			level.validateRetrieveKeys(keys, rootLevel, relational(as, rq));
			String moveDest = rq.getString(Tag.MoveDestination);
			final Connection remote = getRemoteConnection(moveDest);
			if (remote == null)
				throw new DicomServiceException(Status.MoveDestinationUnknown,
						"Move Destination: " + moveDest + " unknown");
			List<InstanceLocator> matches = null;
			if(useDicomDir) {
				matches = DcmQRSCP.this.calculateMatches(keys);
			}else {
				matches = DcmQRSCP.this.calculateMatchesFromDB(keys);
			}
			if (matches.isEmpty())
				return null;

			AAssociateRQ aarq = makeAAssociateRQ(as.getLocalAET(), moveDest, matches);
			Association storeas = openStoreAssociation(as, remote, aarq);
			BasicRetrieveTask<?> retrieveTask = new RetrieveTaskImpl(Dimse.C_MOVE_RQ, as, pc, rq, matches, storeas,
					false, delayCStore);
			retrieveTask.setSendPendingRSPInterval(getSendPendingCMoveInterval());
			return retrieveTask;
		}

		private Association openStoreAssociation(Association as, Connection remote, AAssociateRQ aarq)
				throws DicomServiceException {
			try {
				return as.getApplicationEntity().connect(as.getConnection(), remote, aarq);
			} catch (Exception e) {
				throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
			}
		}

		private AAssociateRQ makeAAssociateRQ(String callingAET, String calledAET, List<InstanceLocator> matches) {
			AAssociateRQ aarq = new AAssociateRQ();
			aarq.setCalledAET(calledAET);
			aarq.setCallingAET(callingAET);
			for (InstanceLocator match : matches) {
				if (aarq.addPresentationContextFor(match.cuid, match.tsuid)) {
					if (!UID.ExplicitVRLittleEndian.equals(match.tsuid))
						aarq.addPresentationContextFor(match.cuid, UID.ExplicitVRLittleEndian);
					if (!UID.ImplicitVRLittleEndian.equals(match.tsuid))
						aarq.addPresentationContextFor(match.cuid, UID.ImplicitVRLittleEndian);
				}
			}
			return aarq;
		}

		private boolean relational(Association as, Attributes rq) {
			String cuid = rq.getString(Tag.AffectedSOPClassUID);
			ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
			return QueryOption.toOptions(extNeg).contains(QueryOption.RELATIONAL);
		}
	}

	public DcmQRSCP() throws IOException {
		device.addConnection(conn);
		device.addApplicationEntity(ae);
		ae.setAssociationAcceptor(true);
		ae.addConnection(conn);
		device.setDimseRQHandler(createServiceRegistry());
	}
	
	public void setLogger(java.util.logging.Logger log) {
		LOG =  log;
	}

	private void storeTo(Association as, Attributes fmi, PDVInputStream data, File file) throws IOException {
		LOG.info("M-WRITE:"+as+","+file);
		file.getParentFile().mkdirs();
		DicomOutputStream out = new DicomOutputStream(file);
		try {
			out.writeFileMetaInformation(fmi);
			data.copyTo(out);
		} finally {
			SafeClose.close(out);
		}
	}

	private File getDestinationFile(Attributes attrs) {
		File file = new File(storageDir, filePathFormat.format(attrs));
		while (file.exists())
			file = new File(file.getParentFile(), TagUtils.toHexString(new Random().nextInt()));
		return file;
	}

	private static void renameTo(Association as, File from, File dest) throws IOException {
		dest.getParentFile().mkdirs();
		
		Path p1 = Paths.get(from.getAbsolutePath());
		Path p2 = Paths.get(dest.getAbsolutePath());
		try{
			java.nio.file.Files.move(p1, p2, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			LOG.info("RENAME:"+as+", "+from+", "+dest);
		}catch(IOException e){
		  throw new IOException("Failed to rename " + from + " to " + dest);
		}
	}

	private static Attributes parse(File file) throws IOException {
		DicomInputStream in = new DicomInputStream(file);
		try {
			in.setIncludeBulkData(IncludeBulkData.NO);
			return in.readDatasetUntilPixelData();//in.readDataset(-1, Tag.PixelData);
		} finally {
			SafeClose.close(in);
		}
	}

	/*
	 * About deletion:See, databaseHandler
	 */
	private static void deleteFile(Association as, File file) {
		if (file.delete())
			LOG.info("M-DELETE:"+","+as+","+file);
		else
			LOG.warning("M-DELETE: failed!"+","+as+","+file);
	}

	public static void deleteFile(File file) {
		if (file.delete())
			LOG.info("M-DELETE:"+file);
		else
			LOG.warning("M-DELETE failed!"+","+file);
	}

	private DicomServiceRegistry createServiceRegistry() {
		DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
		serviceRegistry.addDicomService(new BasicCEchoSCP());
		serviceRegistry.addDicomService(new CStoreSCPImpl());
		serviceRegistry.addDicomService(new StgCmtSCPImpl());
		serviceRegistry.addDicomService(
				new CFindSCPImpl(UID.PatientRootQueryRetrieveInformationModelFind, PATIENT_ROOT_LEVELS));
		serviceRegistry
				.addDicomService(new CFindSCPImpl(UID.StudyRootQueryRetrieveInformationModelFind, STUDY_ROOT_LEVELS));
		serviceRegistry.addDicomService(new CFindSCPImpl(UID.PatientStudyOnlyQueryRetrieveInformationModelFind,
				PATIENT_STUDY_ONLY_LEVELS));
		serviceRegistry
				.addDicomService(new CGetSCPImpl(UID.PatientRootQueryRetrieveInformationModelGet, PATIENT_ROOT_LEVELS));
		serviceRegistry
				.addDicomService(new CGetSCPImpl(UID.StudyRootQueryRetrieveInformationModelGet, STUDY_ROOT_LEVELS));
		serviceRegistry.addDicomService(new CGetSCPImpl(UID.PatientStudyOnlyQueryRetrieveInformationModelGet,
				PATIENT_STUDY_ONLY_LEVELS));
		serviceRegistry.addDicomService(new CGetSCPImpl(UID.CompositeInstanceRetrieveWithoutBulkDataGet));
		serviceRegistry.addDicomService(
				new CMoveSCPImpl(UID.PatientRootQueryRetrieveInformationModelMove, PATIENT_ROOT_LEVELS));
		serviceRegistry
				.addDicomService(new CMoveSCPImpl(UID.StudyRootQueryRetrieveInformationModelMove, STUDY_ROOT_LEVELS));
		serviceRegistry.addDicomService(new CMoveSCPImpl(UID.PatientStudyOnlyQueryRetrieveInformationModelMove,
				PATIENT_STUDY_ONLY_LEVELS));
		return serviceRegistry;
	}

	public final Device getDevice() {
		return device;
	}

	public final void setDicomDirectory(File dicomDir) {
		if (this.dicomDir != null) {
			this.dicomDir = null;
		}
		File storageDir = dicomDir.getParentFile();
		if (storageDir.mkdirs())
			System.out.println("M-WRITE " + storageDir);
		this.storageDir = storageDir;
		this.dicomDir = dicomDir;
	}
	
	//tatsu
	public void setStorageDirectory(String storage) {
		File storageDir = new File(storage);
		if (storageDir.mkdirs()) {
			LOG.info("CREATE STORAGEDIR " + storageDir);
		}
		this.storageDir = storageDir;
	}

	public void reloadDicomDir(File dicomDir) {
		setDicomDirectory(dicomDir);
		try {
			openDicomDir();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public final File getStorageDirectory() {
		return storageDir;
	}

	public final AttributesFormat getFilePathFormat() {
		return filePathFormat;
	}

	public void setFilePathFormat(String pattern) {
		this.filePathFormat = new AttributesFormat(pattern);
	}

	public final File getDicomDirectory() {
		return dicomDir;
	}

	public boolean isWriteable() {
		return storageDir.canWrite();
	}

	public final void setInstanceAvailability(String availability) {
		this.availability = availability;
	}

	public final String getInstanceAvailability() {
		return availability;
	}

	public boolean isIgnoreCaseOfPN() {
		return ignoreCaseOfPN;
	}

	public void setIgnoreCaseOfPN(boolean ignoreCaseOfPN) {
		this.ignoreCaseOfPN = ignoreCaseOfPN;
	}

	public boolean isMatchNoValue() {
		return matchNoValue;
	}

	public void setMatchNoValue(boolean matchNoValue) {
		this.matchNoValue = matchNoValue;
	}

	public boolean isStgCmtOnSameAssoc() {
		return stgCmtOnSameAssoc;
	}

	public void setStgCmtOnSameAssoc(boolean stgCmtOnSameAssoc) {
		this.stgCmtOnSameAssoc = stgCmtOnSameAssoc;
	}

	public final void setSendPendingCGet(boolean sendPendingCGet) {
		this.sendPendingCGet = sendPendingCGet;
	}

	public final boolean isSendPendingCGet() {
		return sendPendingCGet;
	}

	public final void setSendPendingCMoveInterval(int sendPendingCMoveInterval) {
		this.sendPendingCMoveInterval = sendPendingCMoveInterval;
	}

	public final int getSendPendingCMoveInterval() {
		return sendPendingCMoveInterval;
	}

	public int getDelayCFind() {
		return delayCFind;
	}

	public void setDelayCFind(int delayCFind) {
		this.delayCFind = delayCFind;
	}

	public int getDelayCStore() {
		return delayCStore;
	}

	public void setDelayCStore(int delayCStore) {
		this.delayCStore = delayCStore;
	}

	public final void setRecordFactory(RecordFactory recFact) {
		this.recFact = recFact;
	}

	public final RecordFactory getRecordFactory() {
		return recFact;
	}

	public CommandLine parseComandLine(String[] args) throws ParseException {
		Options opts = new Options();
		CLIUtils.addFilesetInfoOptions(opts);
		CLIUtils.addBindServerOption(opts);
		CLIUtils.addConnectTimeoutOption(opts);
		CLIUtils.addAcceptTimeoutOption(opts);
		CLIUtils.addAEOptions(opts);
		CLIUtils.addCommonOptions(opts);
		CLIUtils.addResponseTimeoutOption(opts);
		addStorageDirOption(opts);
		addDicomDirOption(opts);
		addTransferCapabilityOptions(opts);
		addInstanceAvailabilityOption(opts);
		addMatchingOptions(opts);
		addStgCmtOptions(opts);
		addSendingPendingOptions(opts);
		addDelayCFindOptions(opts);
		addDelayCStoreOptions(opts);
		addRemoteConnectionsOption(opts);
		return CLIUtils.parseComandLine(args, opts, rb, DcmQRSCP.class);
	}

	private void addInstanceAvailabilityOption(Options opts) {
		opts.addOption(Option.builder().hasArg().argName("code").desc(rb.getString("availability"))
				.longOpt("availability").build());
	}

	private void addMatchingOptions(Options opts) {
		opts.addOption(null, "match-pn-icase", false, rb.getString("match-pn-icase"));
		opts.addOption(null, "match-no-value", false, rb.getString("match-no-value"));
	}

	private void addStgCmtOptions(Options opts) {
		opts.addOption(null, "stgcmt-same-assoc", false, rb.getString("stgcmt-same-assoc"));
	}

	private void addSendingPendingOptions(Options opts) {
		opts.addOption(null, "pending-cget", false, rb.getString("pending-cget"));
		opts.addOption(Option.builder().hasArg().argName("s").desc(rb.getString("pending-cmove"))
				.longOpt("pending-cmove").build());
	}

	private void addDelayCFindOptions(Options opts) {
		opts.addOption(Option.builder().hasArg().argName("ms").desc(rb.getString("delay-cfind")).longOpt("delay-cfind")
				.build());
	}

	private void addDelayCStoreOptions(Options opts) {
		opts.addOption(Option.builder().hasArg().argName("ms").desc(rb.getString("delay-cstore"))
				.longOpt("delay-cstore").build());
	}
	
	private void addStorageDirOption(Options opts) {
		opts.addOption(
				Option.builder().hasArg().argName("db folder").desc("Storage Folder").longOpt("graphy-storage-dir").build());
	}

	private void addDicomDirOption(Options opts) {
		opts.addOption(
				Option.builder().hasArg().argName("file").desc(rb.getString("dicomdir")).longOpt("dicomdir").build());
		opts.addOption(Option.builder().hasArg().argName("pattern").desc(rb.getString("filepath")).longOpt("filepath")
				.build());
		opts.addOption(Option.builder().longOpt("record-config").hasArg().argName("file|url")
				.desc(rb.getString("record-config")).build());
	}

	private void addTransferCapabilityOptions(Options opts) {
		opts.addOption(null, "all-storage", false, rb.getString("all-storage"));
		opts.addOption(null, "no-storage", false, rb.getString("no-storage"));
		opts.addOption(null, "no-query", false, rb.getString("no-query"));
		opts.addOption(null, "no-retrieve", false, rb.getString("no-retrieve"));
		opts.addOption(null, "relational", false, rb.getString("relational"));
		opts.addOption(Option.builder().hasArg().argName("file|url").desc(rb.getString("storage-sop-classes"))
				.longOpt("storage-sop-classes").build());
		opts.addOption(Option.builder().hasArg().argName("file|url").desc(rb.getString("query-sop-classes"))
				.longOpt("query-sop-classes").build());
		opts.addOption(Option.builder().hasArg().argName("file|url").desc(rb.getString("retrieve-sop-classes"))
				.longOpt("retrieve-sop-classes").build());
	}

	private void addRemoteConnectionsOption(Options opts) {
		opts.addOption(Option.builder().hasArg().argName("file|url").desc(rb.getString("ae-config"))
				.longOpt("ae-config").build());
	}
	
	private void configureStorageDir(DcmQRSCP main, CommandLine cl) {
		String storage_dir = DatabaseHandler.getInstance().getDatabaseFolderPath(false);
		if (cl.hasOption("graphy-storage-dir")) {
			String p = cl.getOptionValue("graphy-storage-dir");
			if(p == null || p.isBlank()) {
				p = storage_dir;
			}
			File f = new File(p);
			if(!f.exists()) {
				f.mkdirs();
			}
			setStorageDirectory(p);
		}else {
			useDicomDir = true;
		}
		main.setFilePathFormat(cl.getOptionValue("filepath", "DICOM/{00100020,hash}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}"));
	}

	protected void configureDicomFileSet(DcmQRSCP main, CommandLine cl) throws Exception {
		if (useDicomDir) {
			main.setDicomDirectory(new File(cl.getOptionValue("dicomdir")));
			RecordFactory recFact = new RecordFactory();
			if (cl.hasOption("record-config"))// for dicomdir creation
				recFact.loadConfiguration(cl.getOptionValue("record-config"));
			main.setRecordFactory(recFact);
		}
	}

	protected static void configureInstanceAvailability(DcmQRSCP main, CommandLine cl) {
		main.setInstanceAvailability(cl.getOptionValue("availability"));
	}

	protected static void configureMatching(DcmQRSCP main, CommandLine cl) {
		main.setIgnoreCaseOfPN(cl.hasOption("match-pn-icase"));
		main.setMatchNoValue(cl.hasOption("match-no-value"));
	}

	protected static void configureStgCmt(DcmQRSCP main, CommandLine cl) {
		main.setStgCmtOnSameAssoc(cl.hasOption("stgcmt-same-assoc"));
	}

	protected static void configureSendPending(DcmQRSCP main, CommandLine cl) {
		main.setSendPendingCGet(cl.hasOption("pending-cget"));
		if (cl.hasOption("pending-cmove"))
			main.setSendPendingCMoveInterval(Integer.parseInt(cl.getOptionValue("pending-cmove")));
	}

	protected static void configureDelayCFind(DcmQRSCP main, CommandLine cl) {
		if (cl.hasOption("delay-cfind"))
			main.setDelayCFind(Integer.parseInt(cl.getOptionValue("delay-cfind")));
	}

	protected static void configureDelayCStore(DcmQRSCP main, CommandLine cl) {
		if (cl.hasOption("delay-cstore"))
			main.setDelayCStore(Integer.parseInt(cl.getOptionValue("delay-cstore")));
	}

	protected void configureTransferCapability(DcmQRSCP main, CommandLine cl) throws IOException {
		ApplicationEntity ae = main.ae;
		EnumSet<QueryOption> queryOptions = cl.hasOption("relational") ? EnumSet.of(QueryOption.RELATIONAL)
				: EnumSet.noneOf(QueryOption.class);
		boolean storage = !cl.hasOption("no-storage") && main.isWriteable();
		/* オールストレージオプションは、すべて保存はしてくれるが、GETSCPやMOVESCPが対応できなくなるので注意 */
		if (storage && cl.hasOption("all-storage")) {
			TransferCapability tc = new TransferCapability(null, "*", TransferCapability.Role.SCP, "*");
			tc.setQueryOptions(queryOptions);
			ae.addTransferCapability(tc);
		} else {
			ae.addTransferCapability(new TransferCapability(null, UID.Verification, TransferCapability.Role.SCP,
					UID.ImplicitVRLittleEndian));
			
//			Properties storageSOPClasses = CLIUtils.loadProperties(
//					cl.getOptionValue("storage-sop-classes", "resource:storage-sop-classes.properties"), null);
			Properties storageSOPClasses = new Properties();
			storageSOPClasses.load(getClass().getResourceAsStream("/dcmqrscp/storage-sop-classes.properties"));
			// sample to load from file
//			java.net.URL resource = new java.io.File("conf/storage-sop-classes.properties").toURI().toURL();
//			java.io.InputStreamReader isr = new java.io.InputStreamReader(resource.openStream(),"UTF8");
//			storageSOPClasses.load(isr);
//			isr.close();
			
			if (storage)
				addTransferCapabilities(ae, storageSOPClasses, TransferCapability.Role.SCP, null);
			if (!cl.hasOption("no-retrieve")) {
				addTransferCapabilities(ae, storageSOPClasses, TransferCapability.Role.SCU, null);
//				Properties p = CLIUtils.loadProperties(
//						cl.getOptionValue("retrieve-sop-classes", "resource:retrieve-sop-classes.properties"), null);
				Properties retrieveSopClasses = new Properties();
				retrieveSopClasses.load(getClass().getResourceAsStream("/dcmqrscp/retrieve-sop-classes.properties"));
				addTransferCapabilities(ae, retrieveSopClasses, TransferCapability.Role.SCP, queryOptions);
			}
			if (!cl.hasOption("no-query")) {
//				Properties p = CLIUtils.loadProperties(
//						cl.getOptionValue("query-sop-classes", "resource:query-sop-classes.properties"), null);
				Properties querySopClasses = new Properties();
				querySopClasses.load(getClass().getResourceAsStream("/dcmqrscp/query-sop-classes.properties"));
				
				addTransferCapabilities(ae, querySopClasses, TransferCapability.Role.SCP, queryOptions);
			}
		}
		if (storage)
			main.openDicomDir();
		else
			main.openDicomDirForReadOnly();
	}

	private static void addTransferCapabilities(ApplicationEntity ae, Properties p, TransferCapability.Role role,
			EnumSet<QueryOption> queryOptions) {
		for (String cuid : p.stringPropertyNames()) {
			String ts = p.getProperty(cuid);
			TransferCapability tc = new TransferCapability(null, CLIUtils.toUID(cuid), role, CLIUtils.toUIDs(ts));
			tc.setQueryOptions(queryOptions);
			ae.addTransferCapability(tc);
		}
	}

	protected void configureRemoteConnections(DcmQRSCP main, CommandLine cl) throws Exception {
		/* As default, if specified option, set it, if not set resource:ae.properties*/
//		String file = cl.getOptionValue("ae-config", "resource:ae.properties");
//		Properties aeConfig = CLIUtils.loadProperties(file, null);
		
		if(!useDicomDir) {
			ArrayList<HashMap<String,Object>> servers = DatabaseHandler.getInstance().getCommunicationServerList();
			for (HashMap<String,Object> entry : servers) {
				String aet = (String) entry.get("aetitle");
				try {
					Connection remote = new Connection();
					remote.setHostname((String)entry.get("hostname"));
					remote.setPort((int)entry.get("port"));
					if(entry.get("ciphers") != null || !entry.get("ciphers").equals("")) {
						remote.setTlsCipherSuites(((String)entry.get("ciphers")).split(":"));
					}
					main.addRemoteConnection(aet, remote);
				} catch (Exception e) {
					throw new IllegalArgumentException("Invalid entry in " + "graphy registered servers" + ": " + entry.get("logicalname"));
				}
			}
		}else {
			Properties aeConfig = new Properties();
			aeConfig.load(getClass().getResourceAsStream("/dcmqrscp/ae.properties"));
			for (Map.Entry<Object, Object> entry : aeConfig.entrySet()) {
				String aet = (String) entry.getKey();
				String value = (String) entry.getValue();
				try {
					String[] hostPortCiphers = StringUtils.split(value, ':');
					String[] ciphers = new String[hostPortCiphers.length - 2];
					System.arraycopy(hostPortCiphers, 2, ciphers, 0, ciphers.length);
					Connection remote = new Connection();
					remote.setHostname(hostPortCiphers[0]);
					remote.setPort(Integer.parseInt(hostPortCiphers[1]));
					remote.setTlsCipherSuites(ciphers);
					main.addRemoteConnection(aet, remote);
				} catch (Exception e) {
//					throw new IllegalArgumentException("Invalid entry in " + file + ": " + aet + "=" + value);
					throw new IllegalArgumentException("Invalid entry in " + "/localserver/ae.properties" + ": " + aet + "=" + value);
				}
			}
		}
	}

	final DicomDirReader getDicomDirReader() {
		return ddReader;
	}

	final DicomDirWriter getDicomDirWriter() {
		return ddWriter;
	}

	private void openDicomDir() throws IOException {
		if(useDicomDir) {
			if (!dicomDir.exists())
				DicomDirWriter.createEmptyDirectory(dicomDir, UIDUtils.createUIDIfNull(fsInfo.getFilesetUID()),
						fsInfo.getFilesetID(), fsInfo.getDescriptorFile(), fsInfo.getDescriptorFileCharset());
			ddReader = ddWriter = DicomDirWriter.open(dicomDir);
		}
	}

	private void openDicomDirForReadOnly() throws IOException {
		ddReader = new DicomDirReader(dicomDir);
	}

	public void addRemoteConnection(String aet, Connection remote) {
		remoteConnections.put(aet, remote);
	}

	Connection getRemoteConnection(String dest) {
		return remoteConnections.get(dest);
	}

	public List<InstanceLocator> calculateMatches(Attributes keys) throws DicomServiceException {
		try {
			
			List<InstanceLocator> list = new ArrayList<InstanceLocator>();
			String[] patIDs = keys.getStrings(Tag.PatientID);
			String[] studyIUIDs = keys.getStrings(Tag.StudyInstanceUID);
			String[] seriesIUIDs = keys.getStrings(Tag.SeriesInstanceUID);
			String[] sopIUIDs = keys.getStrings(Tag.SOPInstanceUID);
			DicomDirReader ddr = ddReader;
			Attributes patRec = ddr.findPatientRecord(patIDs);//patIDsがnullでも、オブジェクトとしてnullではない。
			
			while (patRec != null) {
				Attributes studyRec = ddr.findStudyRecord(patRec, studyIUIDs);
				while (studyRec != null) {
					Attributes seriesRec = ddr.findSeriesRecord(studyRec, seriesIUIDs);
					while (seriesRec != null) {
						Attributes instRec = ddr.findLowerInstanceRecord(seriesRec, true, sopIUIDs);
						while (instRec != null) {
							String cuid = instRec.getString(Tag.ReferencedSOPClassUIDInFile);
							String iuid = instRec.getString(Tag.ReferencedSOPInstanceUIDInFile);
							String tsuid = instRec.getString(Tag.ReferencedTransferSyntaxUIDInFile);
							String[] fileIDs = instRec.getStrings(Tag.ReferencedFileID);
							String uri = ddr.toFile(fileIDs).toURI().toString();//file:+absPath
							list.add(new InstanceLocator(cuid, iuid, tsuid, uri));
							if (sopIUIDs != null && sopIUIDs.length == 1)
								break;

							instRec = ddr.findNextInstanceRecord(instRec, true, sopIUIDs);
						}
						if (seriesIUIDs != null && seriesIUIDs.length == 1)
							break;

						seriesRec = ddr.findNextSeriesRecord(seriesRec, seriesIUIDs);
					}
					if (studyIUIDs != null && studyIUIDs.length == 1)
						break;

					studyRec = ddr.findNextStudyRecord(studyRec, studyIUIDs);
				}
				if (patIDs != null && patIDs.length == 1)
					break;

				patRec = ddr.findNextPatientRecord(patRec, patIDs);
			}
			return list;
		} catch (IOException e) {
			throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
		}
	}
	
	public List<InstanceLocator> calculateMatchesFromDB(Attributes keys) throws DicomServiceException {
		try {
			DatabaseHandler db = DatabaseHandler.getInstance();
			List<String> patIDs = new ArrayList<>();
			List<String> studyIUIDs = new ArrayList<>();
			List<String> seriesIUIDs = new ArrayList<>();
			List<String> sopIUIDs = new ArrayList<>();
			
			List<InstanceLocator> result = new ArrayList<InstanceLocator>();
			
			if(keys.getStrings(Tag.PatientID) != null && keys.getStrings(Tag.PatientID).length!=0) {
				patIDs = new ArrayList<>(Arrays.asList(keys.getStrings(Tag.PatientID)));
			}
			if(keys.getStrings(Tag.StudyInstanceUID) != null && keys.getStrings(Tag.StudyInstanceUID).length!=0) {
				studyIUIDs = new ArrayList<>(Arrays.asList(keys.getStrings(Tag.StudyInstanceUID)));
			}
			if(keys.getStrings(Tag.SeriesInstanceUID) != null && keys.getStrings(Tag.SeriesInstanceUID).length !=0) {
				seriesIUIDs = new ArrayList<>(Arrays.asList(keys.getStrings(Tag.SeriesInstanceUID)));
			}
			if(keys.getStrings(Tag.SOPInstanceUID) != null && keys.getStrings(Tag.SOPInstanceUID).length !=0) {
				sopIUIDs = new ArrayList<>(Arrays.asList(keys.getStrings(Tag.SOPInstanceUID)));
			}
			
			String patID = null;
			String studyIUID = null;
			String seriesIUID = null;
			String sopIUID = null;
			
			do {// patient loop
				if (patIDs.size() != 0) {
					patID = patIDs.iterator().next();
				}
				do {// study loop
					if (studyIUIDs.size() != 0) {
						studyIUID = studyIUIDs.iterator().next();
					}
					do {// series loop
						if (seriesIUIDs.size() != 0) {
							seriesIUID = seriesIUIDs.iterator().next();
						}
						do {// instance loop
							if (sopIUIDs.size() != 0) {
								sopIUID = sopIUIDs.iterator().next();
							}
							ArrayList<HashMap<String, String>> recordInfo = db.getImageInstanceInfo(patID, studyIUID,
									seriesIUID, sopIUID);
							if(recordInfo != null) {
								for (HashMap<String, String> info : recordInfo) {
									result.add(new InstanceLocator(info.get("SOPClassUID"), info.get("SOPInstanceUID"),
											info.get("TransferSyntaxUID"), info.get("URI")));
								}
							}
							if (sopIUIDs.size() != 0) {
								sopIUIDs.remove(0);
							}
						} while (sopIUIDs.iterator().hasNext());
						if (seriesIUIDs.size() != 0) {
							seriesIUIDs.remove(0);
						}
					} while (seriesIUIDs.iterator().hasNext());
					if (studyIUIDs.size() != 0) {
						studyIUIDs.remove(0);
					}
				} while (studyIUIDs.iterator().hasNext());
				if (patIDs.size() != 0) {
					patIDs.remove(0);
				}
			} while (patIDs.iterator().hasNext());
			return result;
		} catch (Exception e) {
			throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
		}
	}

	public Attributes calculateStorageCommitmentResultFromDcmDir(String calledAET, Attributes actionInfo)
			throws DicomServiceException {
		Sequence requestSeq = actionInfo.getSequence(Tag.ReferencedSOPSequence);
		int size = requestSeq.size();
		String[] sopIUIDs = new String[size];
		Attributes eventInfo = new Attributes(6);
		eventInfo.setString(Tag.RetrieveAETitle, VR.AE, calledAET);
		eventInfo.setString(Tag.StorageMediaFileSetID, VR.SH, ddReader.getFileSetID());
		eventInfo.setString(Tag.StorageMediaFileSetUID, VR.SH, ddReader.getFileSetUID());
		eventInfo.setString(Tag.TransactionUID, VR.UI, actionInfo.getString(Tag.TransactionUID));
		Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
		Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(size * 4 / 3);
		for (int i = 0; i < sopIUIDs.length; i++) {
			Attributes item = requestSeq.get(i);
			map.put(sopIUIDs[i] = item.getString(Tag.ReferencedSOPInstanceUID),
					item.getString(Tag.ReferencedSOPClassUID));
		}
		DicomDirReader ddr = ddReader;
		try {
			Attributes patRec = ddr.findPatientRecord();
			while (patRec != null) {
				Attributes studyRec = ddr.findStudyRecord(patRec);
				while (studyRec != null) {
					Attributes seriesRec = ddr.findSeriesRecord(studyRec);
					while (seriesRec != null) {
						Attributes instRec = ddr.findLowerInstanceRecord(seriesRec, true, sopIUIDs);
						while (instRec != null) {
							String iuid = instRec.getString(Tag.ReferencedSOPInstanceUIDInFile);
							String cuid = map.remove(iuid);
							if (cuid.equals(instRec.getString(Tag.ReferencedSOPClassUIDInFile)))
								successSeq.add(refSOP(iuid, cuid, Status.Success));
							else
								failedSeq.add(refSOP(iuid, cuid, Status.ClassInstanceConflict));
							instRec = ddr.findNextInstanceRecord(instRec, true, sopIUIDs);
						}
						seriesRec = ddr.findNextSeriesRecord(seriesRec);
					}
					studyRec = ddr.findNextStudyRecord(studyRec);
				}
				patRec = ddr.findNextPatientRecord(patRec);
			}
		} catch (IOException e) {
			LOG.info("Failed to M-READ " + dicomDir +"\n"+e);
			throw new DicomServiceException(Status.ProcessingFailure, e);
		}
		for (Map.Entry<String, String> entry : map.entrySet()) {
			failedSeq.add(refSOP(entry.getKey(), entry.getValue(), Status.NoSuchObjectInstance));
		}
		if (failedSeq.isEmpty())
			eventInfo.remove(Tag.FailedSOPSequence);
		return eventInfo;
	}
	
	/*
	 * search in db, return Attributes（eventInfo）
	 */
	public Attributes calculateStorageCommitmentResultFromDB(String calledAET, Attributes actionInfo)
			throws DicomServiceException {
		/* Read all list from requested on STUDY level */
		Sequence requestSeq = actionInfo.getSequence(Tag.ReferencedSOPSequence);
		/* total list size */
		int size = requestSeq.size();
		/* prepare SOPInstanceUIDs holder for each list */
		String[] sopIUIDs = new String[size];
		/* results will handle by 4 attrs */
		Attributes eventInfo = new Attributes(4);
		/* Item:1 RetrieveAETitle*/
		eventInfo.setString(Tag.RetrieveAETitle, VR.AE, calledAET);
		/* Item:2 TransactionUID*/
		eventInfo.setString(Tag.TransactionUID, VR.UI, actionInfo.getString(Tag.TransactionUID));
		/* Item:3 ReferencedInstanceSequence:ReferencedSOPClassUID and ReferencedSOPInstanceUID */
		//see, DICOM p.42
		Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
		/* Item:4 ReferencedInstanceSequence no found list in db, If all list success, size become to zero */
		Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
		/* create search applicant list with no duplicate */
		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();//(size * 4 / 3);
		for (int i = 0; i < sopIUIDs.length; i++) {
			Attributes item = requestSeq.get(i);
			map.put(sopIUIDs[i] = item.getString(Tag.ReferencedSOPInstanceUID),
					item.getString(Tag.ReferencedSOPClassUID));
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		//search by SOPInstanceUID
		for(String iuid:sopIUIDs) {
			/* if found, delete from list */
			String path2Inst = db.getInstancePathUsingSOPInstanceUID(iuid);
			if (path2Inst != null) {
				/* get ReferencedSOPInstanceUID as key */
				String cuid = map.remove(iuid);// keyで除外するとともに除外したReferencedSOPClassUIDを取得
				/* if ReferencedSOPClassUID is OK, add to successed sequence */
				if (cuid.equals(DicomUtilities.getSOPClassUID(path2Inst))) {
					successSeq.add(refSOP(iuid, cuid, Status.Success));
				}else {
					/* else add to failed sequence */
					failedSeq.add(refSOP(iuid, cuid, Status.ClassInstanceConflict));
				}
			}
		}
		/* validate */
		for (Map.Entry<String, String> entry : map.entrySet()) {
			failedSeq.add(refSOP(entry.getKey(), entry.getValue(), Status.NoSuchObjectInstance));
		}
		/* if all clear, remove FailedSOPSequence */
		if (failedSeq.isEmpty())
			eventInfo.remove(Tag.FailedSOPSequence);
		return eventInfo;
	}

	boolean addDicomDirRecords(Association as, Attributes ds, Attributes fmi, File f) throws IOException {
		DicomDirWriter ddWriter = getDicomDirWriter();
		RecordFactory recFact = getRecordFactory();
		String pid = ds.getString(Tag.PatientID, null);
		String styuid = ds.getString(Tag.StudyInstanceUID, null);
		String seruid = ds.getString(Tag.SeriesInstanceUID, null);
		String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);
		if (pid == null)
			ds.setString(Tag.PatientID, VR.LO, pid = styuid);

		Attributes patRec = ddWriter.findPatientRecord(pid);
		if (patRec == null) {
			patRec = recFact.createRecord(RecordType.PATIENT, null, ds, null, null);
			ddWriter.addRootDirectoryRecord(patRec);
		}
		Attributes studyRec = ddWriter.findStudyRecord(patRec, styuid);
		if (studyRec == null) {
			studyRec = recFact.createRecord(RecordType.STUDY, null, ds, null, null);
			ddWriter.addLowerDirectoryRecord(patRec, studyRec);
		}
		Attributes seriesRec = ddWriter.findSeriesRecord(studyRec, seruid);
		if (seriesRec == null) {
			seriesRec = recFact.createRecord(RecordType.SERIES, null, ds, null, null);
			ddWriter.addLowerDirectoryRecord(studyRec, seriesRec);
		}
		Attributes instRec = ddWriter.findLowerInstanceRecord(seriesRec, false, iuid);
		if (instRec != null)
			return false;

		instRec = recFact.createRecord(ds, fmi, ddWriter.toFileIDs(f));
		ddWriter.addLowerDirectoryRecord(seriesRec, instRec);
		ddWriter.commit();
		return true;
	}

	private static Attributes refSOP(String iuid, String cuid, int failureReason) {
		Attributes attrs = new Attributes(3);
		attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
		attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
		if (failureReason != Status.Success)
			attrs.setInt(Tag.FailureReason, VR.US, failureReason);
		return attrs;
	}

	public void writeGraphyDB(Attributes data, File dest) {
		DatabaseHandler.getInstance().writeDatasetInfo(data, dest.getAbsolutePath());
	}

	private boolean isListening() {
		return conn.isListening();
	}

	public void close() {
		device.unbindConnections();
	}
	
	public boolean start(String[] args) {
		try {
			CommandLine cl = parseComandLine(args);
			CLIUtils.configure(fsInfo, cl);
			CLIUtils.configureBindServer(conn, ae, cl);
			CLIUtils.configure(conn, cl);
			configureStorageDir(this, cl);
			configureDicomFileSet(this, cl);
			configureTransferCapability(this, cl);
			configureInstanceAvailability(this, cl);
			configureMatching(this, cl);
			configureStgCmt(this, cl);
			configureSendPending(this, cl);
			configureDelayCFind(this, cl);
			configureDelayCStore(this, cl);
			configureRemoteConnections(this, cl);
			ExecutorService executorService = Executors.newCachedThreadPool();
			ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			device.setScheduledExecutor(scheduledExecutorService);
			device.setExecutor(executorService);
			device.bindConnections();
		} catch (ParseException e) {
			System.err.println("graphy-dcmqrscp: " + e.getMessage());
			System.err.println(rb.getString("try"));
			System.exit(2);
		} catch (Exception e) {
			System.err.println("graphy-dcmqrscp: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
		return isListening();
	}

	@Override
	public void stop() {
		close();
	}

	@Override
	public DICOMBackend backendCheck(DICOMBackend backend) {
		return DICOMBackend.DCM4CHE;
	}
}
