package com.vis.core.ui.dialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.regex.Pattern;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import java.awt.GridLayout;
import javax.swing.JTabbedPane;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import java.awt.Insets;
import javax.swing.SwingConstants;

import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.DeIdentificationMethod;
import org.dcm4che3.io.DicomEncodingOptions;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;

import com.vis.dicom.DicomUtilities;
import com.vis.dicom.TagDict;
import javax.swing.JScrollPane;

@SuppressWarnings("serial")
public class DcmAnonymizer2 extends JFrame{
	/*
	 * https://github.com/dcm4che/dcm4che/blob/5d64574d2a496dc10145c10a849d1805c94ba108/dcm4che-deident/src/main/java/org/dcm4che3/deident/DeIdentifier.java#L54
	 */
	boolean proceeded = false;
	private JTextField textFieldPname;
	private JTextField textFieldPID;
	private ArrayList<String> advanceItem;
	private ArrayList<JCheckBox> anonymizeCheckList;
	private ArrayList<JTextField> anonymizeTextList; // DO NOT value replace.
	
	String regex_num = "^[0-9]+$";//pattern matcher 0-9
    Pattern p1 = Pattern.compile(regex_num); // 正規表現パターンの読み込み
	
	private static final String UNMODIFIED = "UNMODIFIED";
    private static final String REMOVED = "REMOVED";
    private static final String YES = "YES";
    
    private final String RETAIN_ORIGINAL = "RETAIN_ORIGINAL";

    private EnumSet<DcmAnonymizer2.Option> options = null;
    private Attributes dummyValues = new Attributes();
    private int[] x = X;
    private int[] u = U;
    private int[] o;
	private JCheckBox chckbxRetainDeviceIdentityOption;
	private JCheckBox chckbxRetainInstitutionIdentityOption;
	private JCheckBox chckbxRetainLongitudinalTemporalInformationFullDatesOption;
	private JCheckBox chckbxRetainUIDsOption;
	private JCheckBox chckbxRetainPrivate;
	private JRadioButton rdbtnSimple;
	private JRadioButton rdbtnAdvance;
    
    public enum Option {
        BasicApplicationConfidentialityProfile(DeIdentificationMethod.BasicApplicationConfidentialityProfile),
//        CleanPixelDataOption(DeIdentificationMethod.CleanPixelDataOption),
//        CleanRecognizableVisualFeaturesOption(DeIdentificationMethod.CleanRecognizableVisualFeaturesOption),
//        CleanGraphicsOption(DeIdentificationMethod.CleanGraphicsOption),
//        CleanStructuredContentOption(DeIdentificationMethod.CleanStructuredContentOption),
//        CleanDescriptorsOption(DeIdentificationMethod.CleanDescriptorsOption),
        RetainLongitudinalTemporalInformationFullDatesOption(
                DeIdentificationMethod.RetainLongitudinalTemporalInformationFullDatesOption),
//        RetainLongitudinalTemporalInformationModifiedDatesOption(
//                DeIdentificationMethod.RetainLongitudinalTemporalInformationModifiedDatesOption),
//        RetainPatientCharacteristicsOption(DeIdentificationMethod.RetainPatientCharacteristicsOption),
        RetainDeviceIdentityOption(DeIdentificationMethod.RetainDeviceIdentityOption),
        RetainInstitutionIdentityOption(DeIdentificationMethod.RetainInstitutionIdentityOption),
        RetainUIDsOption(DeIdentificationMethod.RetainUIDsOption);
//        RetainSafePrivateOption(DeIdentificationMethod.RetainSafePrivateOption);

        private final Code code;

        Option(Code code) {
            this.code = code;
        }
    }

	//debug and how-to
	public static void main(String[] args) {
		DcmAnonymizer2 anon = new DcmAnonymizer2();
		anon.addWindowListener(new WindowListener() {
			@Override
			public void windowOpened(WindowEvent e) {}
			
			@Override
			public void windowIconified(WindowEvent e) {}
			
			@Override
			public void windowDeiconified(WindowEvent e) {}
			
			@Override
			public void windowDeactivated(WindowEvent e) {
				File src = new File("C:\\Users\\tatsu\\OneDrive\\デスクトップ\\DICOM-CD-TEST\\DICOM\\LGG-104");
				File dest = new File("C:\\Users\\tatsu\\OneDrive\\デスクトップ\\LGG-104_deident");
				anon.mtranscode(src, dest);
				System.out.println("Finish de-identified.");
			}
			
			@Override
			public void windowClosing(WindowEvent e) {}
			
			@Override
			public void windowClosed(WindowEvent e) {}
			
			@Override
			public void windowActivated(WindowEvent e) {}
		});
		
	}
	
	public DcmAnonymizer2() {
		super("Dicom Anonymizer");
		
		this.options = null;
		
		JPanel panelNorth = new JPanel();
		getContentPane().add(panelNorth, BorderLayout.NORTH);
		
		ButtonGroup bgroup = new ButtonGroup();
		
		rdbtnSimple = new JRadioButton("Simple");
		rdbtnSimple.setSelected(true);
		panelNorth.add(rdbtnSimple);
		
		rdbtnAdvance = new JRadioButton("Advance");
		panelNorth.add(rdbtnAdvance);
		
		bgroup.add(rdbtnSimple);
		bgroup.add(rdbtnAdvance);

		JPanel panelSouth = new JPanel();
		getContentPane().add(panelSouth, BorderLayout.SOUTH);
		
		JButton btnProceed = new JButton("Proceed");
		btnProceed.setToolTipText("set de-identify mode");
		panelSouth.add(btnProceed);
		btnProceed.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setProceed(true);
				readyToDeIdentify(checkOptions());
				setVisible(false);
			}
		});
		
		JButton btnCancel = new JButton("Cancel");
		btnCancel.setToolTipText("cancel de-identify");
		panelSouth.add(btnCancel);
		btnCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setProceed(false);
				setVisible(false);
			}
		});
		
		JPanel panelCenter = new JPanel();
		getContentPane().add(panelCenter, BorderLayout.CENTER);
		panelCenter.setLayout(new BorderLayout(0, 0));
		
		JPanel panelOption = new JPanel();
		panelCenter.add(panelOption, BorderLayout.SOUTH);
		panelOption.setLayout(new GridLayout(5, 1, 0, 0));
		
		chckbxRetainDeviceIdentityOption = new JCheckBox("Retain DeviceIdentity Option");
		chckbxRetainDeviceIdentityOption.setSelected(true);
		panelOption.add(chckbxRetainDeviceIdentityOption);
		
		chckbxRetainInstitutionIdentityOption = new JCheckBox("Retain Institution Identity Option");
		chckbxRetainInstitutionIdentityOption.setSelected(true);
		panelOption.add(chckbxRetainInstitutionIdentityOption);
		
		chckbxRetainLongitudinalTemporalInformationFullDatesOption = new JCheckBox("Retain Longitudinal Temporal Information Full Dates Option");
		chckbxRetainLongitudinalTemporalInformationFullDatesOption.setSelected(true);
		panelOption.add(chckbxRetainLongitudinalTemporalInformationFullDatesOption);
		
		chckbxRetainUIDsOption = new JCheckBox("Retain UIDs Option");
		chckbxRetainUIDsOption.setSelected(true);
		panelOption.add(chckbxRetainUIDsOption);
		
		chckbxRetainPrivate = new JCheckBox("Retain private tags");
		chckbxRetainPrivate.setSelected(true);
		panelOption.add(chckbxRetainPrivate);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panelCenter.add(tabbedPane, BorderLayout.CENTER);
		
		JPanel panelGeneral = new JPanel();
		tabbedPane.addTab("General", null, panelGeneral, null);
		GridBagLayout gbl_panelGeneral = new GridBagLayout();
		gbl_panelGeneral.columnWidths = new int[]{0, 0, 0};
		gbl_panelGeneral.rowHeights = new int[]{0, 0, 0};
		gbl_panelGeneral.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gbl_panelGeneral.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		panelGeneral.setLayout(gbl_panelGeneral);
		
		JLabel lblPname = new JLabel(" Patient Name");
		lblPname.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblPname = new GridBagConstraints();
		gbc_lblPname.insets = new Insets(0, 0, 5, 5);
		gbc_lblPname.anchor = GridBagConstraints.WEST;
		gbc_lblPname.gridx = 0;
		gbc_lblPname.gridy = 0;
		panelGeneral.add(lblPname, gbc_lblPname);
		
		textFieldPname = new JTextField();
		textFieldPname.setText("de-identified");
		GridBagConstraints gbc_textFieldPname = new GridBagConstraints();
		gbc_textFieldPname.insets = new Insets(0, 0, 5, 0);
		gbc_textFieldPname.fill = GridBagConstraints.HORIZONTAL;
		gbc_textFieldPname.gridx = 1;
		gbc_textFieldPname.gridy = 0;
		panelGeneral.add(textFieldPname, gbc_textFieldPname);
		textFieldPname.setColumns(10);
		
		JLabel lblPID = new JLabel(" Patient ID");
		lblPID.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblPID = new GridBagConstraints();
		gbc_lblPID.anchor = GridBagConstraints.WEST;
		gbc_lblPID.insets = new Insets(0, 0, 0, 5);
		gbc_lblPID.gridx = 0;
		gbc_lblPID.gridy = 1;
		panelGeneral.add(lblPID, gbc_lblPID);
		
		textFieldPID = new JTextField();
		textFieldPID.setText("de-identified");
		GridBagConstraints gbc_textFieldPID = new GridBagConstraints();
		gbc_textFieldPID.fill = GridBagConstraints.HORIZONTAL;
		gbc_textFieldPID.gridx = 1;
		gbc_textFieldPID.gridy = 1;
		panelGeneral.add(textFieldPID, gbc_textFieldPID);
		textFieldPID.setColumns(10);
		
		JScrollPane scrollPaneAdvance = new JScrollPane();
		tabbedPane.addTab("Advance", null, scrollPaneAdvance, null);
		JPanel panelAdvance = new JPanel();
		scrollPaneAdvance.setViewportView(panelAdvance);
		GridBagLayout gbl_panelAdvance = new GridBagLayout();
		gbl_panelAdvance.columnWidths = new int[] { 0, 0, 0 };
		gbl_panelAdvance.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		gbl_panelAdvance.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		gbl_panelAdvance.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panelAdvance.setLayout(gbl_panelAdvance);
		
		setUpAdvancePanel(panelAdvance);
		
//		pack();// do not use
		setSize(400, 400);
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	
	public void readyToDeIdentify(Option... options) {
        this.options = EnumSet.of(Option.BasicApplicationConfidentialityProfile, options);
        int[] z = Z;
        int[] d = D;
        if (!this.options.contains(Option.RetainDeviceIdentityOption)) {
            x = cat(x, X_DEVICE);
            d = cat(d, D_DEVICE);
            u = cat(u, U_DEVICE);
        }
        if (!this.options.contains(Option.RetainInstitutionIdentityOption)) {
            x = cat(x, X_INSTITUTION);
            z = cat(z, Z_INSTITUTION);
            d = cat(d, D_INSTITUTION);
        }
        if (!this.options.contains(Option.RetainLongitudinalTemporalInformationFullDatesOption)) {
            x = cat(x, X_DATES);
            z = cat(z, Z_DATES);
            d = cat(d, D_DATES);
        }
        if (!this.options.contains(Option.RetainUIDsOption)) {
            z = cat(z, Z_UID);
        }
        o = cat(z, d);
        Arrays.sort(x);
        Arrays.sort(u);
        Arrays.sort(o);
        initDummyValues(d);//IMPORTANT
        /*
         * then, set perticular dummy values for each editable tags 
         */
        setReplacableDummyValues();
    }
	
	private Option[] checkOptions() {
		this.options = null;
		this.options = EnumSet.noneOf(DcmAnonymizer2.Option.class);
		if(chckbxRetainDeviceIdentityOption.isSelected()) {
			options.add(DcmAnonymizer2.Option.RetainDeviceIdentityOption);
		}
		if(chckbxRetainInstitutionIdentityOption.isSelected()) {
			options.add(DcmAnonymizer2.Option.RetainInstitutionIdentityOption);
		}
		if(chckbxRetainLongitudinalTemporalInformationFullDatesOption.isSelected()) {
			options.add(DcmAnonymizer2.Option.RetainLongitudinalTemporalInformationFullDatesOption);
		}
		if(chckbxRetainUIDsOption.isSelected()) {
			options.add(DcmAnonymizer2.Option.RetainUIDsOption);
		}
		return options.toArray(new DcmAnonymizer2.Option[0]);
	}
	
	private void setReplacableDummyValues() {
		String pname = textFieldPname.getText().trim();
		String pid = textFieldPID.getText().trim();
		if(pname != null && !pname.equals("")) {
			VR vr = ElementDictionary.getStandardElementDictionary().vrOf(Tag.PatientName);
            setDummyValue(Tag.PatientName, vr, pname);
		}
		if(pid != null && !pid.equals("")) {
			VR vr = ElementDictionary.getStandardElementDictionary().vrOf(Tag.PatientID);
            setDummyValue(Tag.PatientID, vr, pid);
		}
		if(anonymizeCheckList == null || anonymizeTextList == null) {
			return;
		}else {
			for(JCheckBox chck : anonymizeCheckList) {
				if(chck.isSelected()) {
					String tagName = chck.getName();
					System.out.println("Retain tag: "+tagName);
					String val = getTextFieldNamed(tagName).getText().trim();
					int tag = TagDict.tagOf(tagName);
					VR vr = ElementDictionary.getStandardElementDictionary().vrOf(tag);
					if(val != null && !val.equals("")) {
						String replace = checkDummyValueFor(vr, val);
						if(replace != null) {
							//replace
							setDummyValue(tag, vr, replace);
						}else {
							//retain original
							setDummyValue(tag, vr, RETAIN_ORIGINAL);
						}
					}else {
						//retain original
						setDummyValue(tag, vr, RETAIN_ORIGINAL);
					}
				}
			}
		}
    }
	
	public void setDummyValue(int tag, VR vr, String s) {
        dummyValues.setString(tag, vr, s);
    }

    public void deidentify(Attributes attrs) {
    	if(rdbtnAdvance.isSelected()) {
    		deidentifyItem(attrs);
            correct(attrs);
            attrs.setString(Tag.PatientIdentityRemoved, VR.CS, YES);
            attrs.setString(Tag.LongitudinalTemporalInformationModified, VR.CS,
                    options.contains(Option.RetainLongitudinalTemporalInformationFullDatesOption) ? UNMODIFIED : REMOVED);
            Sequence sq = attrs.ensureSequence(Tag.DeidentificationMethodCodeSequence, options.size());
            for (Option option : options) {
                sq.add(option.code.toItem());
            }
    	}else {
    		deidentifyItemSimple(attrs);
    	}
    }

    public String remapUID(String uid) {
        return options.contains(Option.RetainUIDsOption) ? uid : UIDUtils.remapUID(uid);
    }

    public boolean equalOptions(Option... options) {
        return EnumSet.of(Option.BasicApplicationConfidentialityProfile, options).equals(options);
    }

    private static int[] cat(int[] a, int[] b) {
        int[] dest = new int[a.length + b.length];
        System.arraycopy(a, 0, dest, 0, a.length);
        System.arraycopy(b, 0, dest, a.length, b.length);
        return dest;
    }

    private void initDummyValues(int[] d) {
    	dummyValues = new Attributes();
        ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
        for (int tag : d) {
        	initDummyValue(dict.vrOf(tag), tag);
        }
        initDummyValue(VR.DA, Tag.SeriesDate);
        initDummyValue(VR.TM, Tag.SeriesTime);
    }

    private Object initDummyValue(VR vr, int tag) {
        return dummyValues.setString(tag, vr, dummyValueFor(vr));
    }

    private static String dummyValueFor(VR vr) {
        switch (vr) {
            case DA:
                return "19991111";
            case DT:
                return "19991111111111";
            case TM:
                return "111111";
            case IS:
            case DS:
                return "0";
		default:
			break;
        }
        return "REMOVED";
    }
    
    private String checkDummyValueFor(VR vr, String replace) {
        switch (vr) {
            case DA:
            	if(replace.length() != "yyyymmdd".length()) {
            		return null;
            	}
            	if(p1.matcher(replace).matches()) {
            		return replace;
            	}
                return null;
            case DT:
            	if(replace.length() != "yyyyMMddHHmm".length()) {
            		return null;
            	}
            	if(p1.matcher(replace).matches()) {
            		return replace;
            	}
                return null;
            case TM:
            	if(replace.length() != "HHmmSS".length()) {
            		return null;
            	}
            	if(p1.matcher(replace).matches()) {
            		return replace;
            	}
                return null;
            case IS:
            case DS:
                return replace;
		default:
			break;
        }
        return replace;
    }

    private void correct(Attributes attrs) {
        if (!options.contains(Option.RetainLongitudinalTemporalInformationFullDatesOption)
                && UID.PositronEmissionTomographyImageStorage.equals(attrs.getString(Tag.SOPClassUID))) {
            attrs.setString(Tag.SeriesDate, VR.DA, dummyValues.getString(Tag.SeriesDate));
            attrs.setString(Tag.SeriesTime, VR.TM, dummyValues.getString(Tag.SeriesTime));
        }
    }

    private void deidentifyItem(Attributes attrs) {
    	int removeOrreplace[] = dummyValues.tags();
    	 ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    	for(int tag : removeOrreplace) {
    		String val = dummyValues.getString(tag);
    		if(val.equals(RETAIN_ORIGINAL)) {
    			dummyValues.setValue(tag, dict.vrOf(tag), attrs.getValue(tag));
    		}
    	}
    	
    	if(!chckbxRetainPrivate.isSelected()) {
    		attrs.removePrivateAttributes();
    	}
        attrs.removeCurveData();
        attrs.removeOverlayData();
        attrs.replaceSelected(dummyValues, x); //or  attrs.removeSelected(x);
        attrs.replaceSelected(dummyValues, o);
        if (!options.contains(Option.RetainUIDsOption)) {
        	attrs.replaceUIDSelected(u);
        }
        try {
            attrs.accept(new Attributes.Visitor() {
                @Override
                public boolean visit(Attributes attrs, int tag, VR vr, Object value) throws Exception {
                    if (value instanceof Sequence)
                        for (Attributes item : (Sequence) value)
                            deidentifyItem(item);
                    return true;
                }
            }, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private void deidentifyItemSimple(Attributes attrs) {
    	String pname = textFieldPname.getText().trim();
    	if(pname == null) {
    		pname = "";
    	}else {
    		pname = pname.replace(" ", "^").replace("　", "^");
    	}
    	VR vr = ElementDictionary.getStandardElementDictionary().vrOf(Tag.PatientName);
    	attrs.setString(Tag.PatientName, vr, pname);
    	//pid
    	String pid = textFieldPID.getText().trim();
    	if(pid == null) {
    		pid = "";
    	}else {
    		pid = pid.replace(" ", "^").replace("　", "^");
    	}
    	vr = ElementDictionary.getStandardElementDictionary().vrOf(Tag.PatientID);
    	attrs.setString(Tag.PatientID, vr, pid);
    }
	
	private void setProceed(boolean proceed) {
		this.proceeded = proceed;
	}
	
	public boolean isProceeded() {
		return this.proceeded;
	}
	
	private void setUpAdvancePanel(JPanel advancePanel) {
		advanceItem = new ArrayList<String>();
		for(int tag : X) {
			advanceItem.add(TagDict.keyword(tag));
		}
		for(int tag : Z) {
			String name = TagDict.keyword(tag);
			if(name.equals(TagDict.keyword(Tag.PatientName)) || name.equals(TagDict.keyword(Tag.PatientID))) {
				continue;
			}
			if(!advanceItem.contains(name)) {
				advanceItem.add(name);
			}
		}
		for(int tag : D) {
			String name = TagDict.keyword(tag);
			if(!advanceItem.contains(name)) {
				advanceItem.add(name);
			}
		}
		
		anonymizeCheckList = new ArrayList<JCheckBox>();
		anonymizeTextList = new ArrayList<JTextField>();
		for (int i = 0; i < advanceItem.size(); i++) {
			String itemName = advanceItem.get(i);
			JCheckBox chckbx = new JCheckBox("Retain "+itemName);
			chckbx.setName(itemName);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(0, 0, 5, 5);
			gbc.gridx = 0;
			gbc.gridy = i;
			advancePanel.add(chckbx, gbc);
			anonymizeCheckList.add(chckbx);

			JTextField textField = new JTextField();
			textField.setName(itemName);
			if (itemName.contains("Date") || itemName.contains("Time")) {
				textField.setToolTipText("use format; date:YYYYMMDD, time:HHMMSS, datetime:YYYYMMDDHHMMSS");
			}
			if (itemName.contains("Sequence")) {
				textField.setEnabled(false);
			}
			GridBagConstraints gbc_textField = new GridBagConstraints();
			gbc_textField.insets = new Insets(0, 0, 5, 0);
			gbc_textField.fill = GridBagConstraints.HORIZONTAL;
			gbc_textField.gridx = 1;
			gbc_textField.gridy = i;
			advancePanel.add(textField, gbc_textField);
			textField.setColumns(10);
			anonymizeTextList.add(textField);
		}

	}
	
	private JTextField getTextFieldNamed(String name) {
		if (anonymizeTextList == null || anonymizeTextList.size() < 1) {
			return null;
		}
		for (JTextField field : anonymizeTextList) {
			if (field.getName().equals(name)) {
				return field;
			}
		}
		return null;
	}

//	private ArrayList<String> getAnonymizeItems() {
//		if(anonymizeCheckList == null){
//			return null;
//		}
//		ArrayList<String> items = new ArrayList<String>();
//		for (JCheckBox ckbx : anonymizeCheckList) {
//			if (ckbx.isSelected()) {
//				items.add(ckbx.getName());
//			}
//		}
//		if (items.size() == 0) {
//			return null;
//		} else {
//			return items;
//		}
//	}
	
	/*
	 * you should check dicomdir end up anonymize.
	 */
	public void mtranscode(File src, File dest) {
		if(!isProceeded()) {
			return;
		}
		if(DicomUtilities.isDICOMDIR(src)) {
			return;
		}
        if (src.isDirectory()) {
        	if(!dest.exists()) {
        		dest.mkdir();
        	}
            for (File file : src.listFiles()) {
            	mtranscode(file, new File(dest, file.getName()));
            }
            return;
        }
        if (dest.isDirectory())
            dest = new File(dest, src.getName());
        try {
            transcode(src, dest);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

   private void transcode(File src, File dest) throws IOException {
	   if(!DicomUtilities.isDicomFile(src)) {
		   return;
	   }
       Attributes fmi;
       Attributes dataset;
       try (DicomInputStream dis = new DicomInputStream(src)) {
           dis.setIncludeBulkData(IncludeBulkData.URI);
           fmi = dis.readFileMetaInformation();
           dataset = dis.readDataset();
       }
       deidentify(dataset);
       if (fmi != null)
           fmi = dataset.createFileMetaInformation(fmi.getString(Tag.TransferSyntaxUID));
       try (DicomOutputStream dos = new DicomOutputStream(dest)) {
           dos.setEncodingOptions(DicomEncodingOptions.DEFAULT);
           dos.writeDataset(fmi, dataset);
       }
   }
	
	private static final int[] X = {
            Tag.AcquisitionComments,
            Tag.AcquisitionContextSequence,
            Tag.AcquisitionProtocolDescription,
            Tag.ActualHumanPerformersSequence,
            Tag.AdditionalPatientHistory,
            Tag.AddressTrial,
            Tag.AdmissionID,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.AdmittingDiagnosesDescription,
            Tag.Allergies,
            Tag.Arbitrary,
            Tag.AuthorObserverSequence,
            Tag.BranchOfService,
            Tag.CommentsOnThePerformedProcedureStep,
            Tag.ConfidentialityConstraintOnPatientDataDescription,
            Tag.ConsultingPhysicianIdentificationSequence,
            Tag.ContentCreatorIdentificationCodeSequence,
            Tag.ContentSequence,
            Tag.ContributionDescription,
            Tag.CountryOfResidence,
            Tag.CurrentObserverTrial,
            Tag.CurrentPatientLocation,
            Tag.CustodialOrganizationSequence,
            Tag.Date, // Content Item Attribute
            Tag.DateTime, // Content Item Attribute
            Tag.DataSetTrailingPadding,
            Tag.DerivationDescription,
            Tag.DigitalSignatureUID,
            Tag.DigitalSignaturesSequence,
            Tag.DischargeDiagnosisCodeSequence,
            Tag.DischargeDiagnosisDescription,
            Tag.DistributionAddress,
            Tag.DistributionName,
            Tag.EthnicGroup,
            Tag.FrameComments,
            Tag.GraphicAnnotationSequence,
            Tag.HumanPerformerCodeSequence, // missing in Part 15
            Tag.HumanPerformerName,
            Tag.HumanPerformerOrganization,
            Tag.IconImageSequence,
            Tag.IdentifyingComments,
            Tag.ImageComments,
            Tag.ImagePresentationComments,
            Tag.ImagingServiceRequestComments,
            Tag.Impressions,
            Tag.InsurancePlanIdentification,
            Tag.IntendedRecipientsOfResultsIdentificationSequence,
            Tag.InterpretationApproverSequence,
            Tag.InterpretationAuthor,
            Tag.InterpretationDiagnosisDescription,
            Tag.InterpretationIDIssuer,
            Tag.InterpretationRecorder,
            Tag.InterpretationText,
            Tag.InterpretationTranscriber,
            Tag.IssuerOfAccessionNumberSequence, // missing in Part 15
            Tag.IssuerOfAdmissionID,
            Tag.IssuerOfAdmissionIDSequence, // missing in Part 15
            Tag.IssuerOfPatientID,
            Tag.IssuerOfPatientIDQualifiersSequence, // missing in Part 15
            Tag.IssuerOfServiceEpisodeID,
            Tag.MAC,
            Tag.MedicalAlerts,
            Tag.MedicalRecordLocator,
            Tag.MilitaryRank,
            Tag.ModifiedAttributesSequence,
            Tag.ModifiedImageDescription,
            Tag.ModifyingDeviceID,
            Tag.NameOfPhysiciansReadingStudy,
            Tag.NamesOfIntendedRecipientsOfResults,
            Tag.Occupation,
            Tag.OperatorIdentificationSequence,
            Tag.OrderCallbackPhoneNumber,
            Tag.OrderCallbackTelecomInformation,
            Tag.OrderEnteredBy,
            Tag.OrderEntererLocation,
            Tag.OriginalAttributesSequence,
            Tag.OtherPatientIDs,
            Tag.OtherPatientIDsSequence,
            Tag.OtherPatientNames,
            Tag.ParticipantSequence,
            Tag.PatientAddress,
            Tag.PatientComments,
            Tag.PatientState,
            Tag.PatientTransportArrangements,
            Tag.PatientAge,
            Tag.PatientBirthName,
            Tag.PatientBirthTime,
            Tag.PatientInstitutionResidence,
            Tag.PatientInsurancePlanCodeSequence,
            Tag.PatientMotherBirthName,
            Tag.PatientPrimaryLanguageCodeSequence,
            Tag.PatientPrimaryLanguageModifierCodeSequence,
            Tag.PatientReligiousPreference,
            Tag.PatientSize,
            Tag.PatientSizeCodeSequence, // missing in Part 15
            Tag.PatientTelecomInformation,
            Tag.PatientTelephoneNumbers,
            Tag.PatientWeight,
            Tag.PerformedLocation,
            Tag.PerformedProcedureStepDescription,
            Tag.PerformedProcedureStepID,
            Tag.PerformingPhysicianIdentificationSequence,
            Tag.PerformingPhysicianName,
            Tag.PersonAddress,
            Tag.PersonIdentificationCodeSequence,
            Tag.PersonName, // Content Item Attribute
            Tag.PersonTelecomInformation,
            Tag.PersonTelephoneNumbers,
            Tag.PhysicianApprovingInterpretation,
            Tag.PhysiciansReadingStudyIdentificationSequence,
            Tag.PhysiciansOfRecord,
            Tag.PhysiciansOfRecordIdentificationSequence,
            Tag.PreMedication,
            Tag.PregnancyStatus,
            Tag.ReasonForOmissionDescription,
            Tag.ReasonForTheImagingServiceRequest,
            Tag.ReasonForStudy,
            Tag.ReferencedDigitalSignatureSequence,
            Tag.ReferencedPatientAliasSequence,
            Tag.ReferencedPatientPhotoSequence,
            Tag.ReferencedPatientSequence,
            Tag.ReferencedSOPInstanceMACSequence,
            Tag.ReferringPhysicianAddress,
            Tag.ReferringPhysicianIdentificationSequence,
            Tag.ReferringPhysicianTelephoneNumbers,
            Tag.RegionOfResidence,
            Tag.RequestAttributesSequence,
            Tag.RequestedContrastAgent,
            Tag.RequestedProcedureComments,
            Tag.RequestedProcedureID,
            Tag.RequestedProcedureLocation,
            Tag.RequestingPhysician,
            Tag.RequestingPhysicianIdentificationSequence, // missing in Part 15
            Tag.RequestingService,
            Tag.RequestingServiceCodeSequence, // missing in Part 15
            Tag.ResponsibleOrganization,
            Tag.ResponsiblePerson,
            Tag.ResultsComments,
            Tag.ResultsDistributionListSequence,
            Tag.ResultsIDIssuer,
            Tag.ScheduledHumanPerformersSequence,
            Tag.ScheduledPatientInstitutionResidence,
            Tag.ScheduledPerformingPhysicianIdentificationSequence,
            Tag.ScheduledPerformingPhysicianName,
            Tag.ScheduledProcedureStepDescription,
            Tag.SeriesDescription,
            Tag.SeriesDescriptionCodeSequence, // missing in Part 15
            Tag.ServiceEpisodeDescription,
            Tag.ServiceEpisodeID,
            Tag.SmokingStatus,
            Tag.SpecialNeeds,
            Tag.StudyComments,
            Tag.StudyDescription,
            Tag.StudyIDIssuer,
            Tag.TelephoneNumberTrial,
            Tag.TextComments,
            Tag.TextString,
            Tag.TextValue, // Content Item Attribute
            Tag.Time, // Content Item Attribute
            Tag.TopicAuthor,
            Tag.TopicKeywords,
            Tag.TopicSubject,
            Tag.TopicTitle,
            Tag.VerbalSourceTrial,
            Tag.VerbalSourceIdentifierCodeSequenceTrial,
            Tag.VisitComments
    };

    private static final int[] X_INSTITUTION = {
            Tag.InstitutionAddress,
            Tag.InstitutionalDepartmentName,
    };

    private static final int[] X_DEVICE = {
            Tag.CassetteID,
            Tag.GantryID,
            Tag.GeneratorID,
            Tag.PerformedStationAETitle,
            Tag.PerformedStationGeographicLocationCodeSequence,
            Tag.PerformedStationName,
            Tag.PerformedStationNameCodeSequence,
            Tag.PlateID,
            Tag.ScheduledProcedureStepLocation,
            Tag.ScheduledStationAETitle,
            Tag.ScheduledStationGeographicLocationCodeSequence,
            Tag.ScheduledStationName,
            Tag.ScheduledStationNameCodeSequence,
            Tag.ScheduledStudyLocation,
            Tag.ScheduledStudyLocationAETitle,
            Tag.SourceSerialNumber,
    };

    private static final int[] X_DATES = {
            Tag.CurveDate,
            Tag.CurveTime,
            Tag.ExpectedCompletionDateTime,
            Tag.InstanceCoercionDateTime,
            Tag.InstanceCreationDate, // missing in Part 15
            Tag.InstanceCreationTime, // missing in Part 15
            Tag.LastMenstrualDate,
            Tag.ObservationDateTime,
            Tag.ObservationDateTrial,
            Tag.ObservationTimeTrial,
            Tag.OverlayDate,
            Tag.OverlayTime,
            Tag.PerformedProcedureStepEndDate,
            Tag.PerformedProcedureStepEndDateTime,
            Tag.PerformedProcedureStepEndTime,
            Tag.PerformedProcedureStepStartDate,
            Tag.PerformedProcedureStepStartDateTime,
            Tag.PerformedProcedureStepStartTime,
            Tag.ProcedureStepCancellationDateTime,
            Tag.ScheduledProcedureStepEndDate,
            Tag.ScheduledProcedureStepEndTime,
            Tag.ScheduledProcedureStepModificationDateTime,
            Tag.ScheduledProcedureStepStartDate,
            Tag.ScheduledProcedureStepStartDateTime,
            Tag.ScheduledProcedureStepStartTime,
            Tag.TimezoneOffsetFromUTC,
    };

    private static final int[] Z = {
            Tag.AccessionNumber,
            Tag.ConsultingPhysicianName,
            Tag.ContentCreatorName,
            Tag.FillerOrderNumberImagingServiceRequest,
            Tag.PatientID,
            Tag.PatientSexNeutered,
            Tag.PatientBirthDate,
            Tag.PatientName,
            Tag.PatientSex,
            Tag.PlacerOrderNumberImagingServiceRequest,
            Tag.ReferringPhysicianName,
            Tag.RequestedProcedureDescription,
            Tag.ReviewerName,
            Tag.StudyID,
            Tag.VerifyingObserverIdentificationCodeSequence,
    };

    private static final int[] Z_INSTITUTION = {
            Tag.InstitutionCodeSequence,
    };

    private static final int[] Z_DATES = {
            Tag.AcquisitionDate,
            Tag.AcquisitionTime,
            Tag.AdmittingDate,
            Tag.AdmittingTime,
            Tag.SeriesDate,
            Tag.SeriesTime,
            Tag.StudyDate,
            Tag.StudyTime,
    };

    private static final int[] Z_UID = {
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.ReferencedStudySequence
    };

    private static final int[] D = {
            Tag.AcquisitionDeviceProcessingDescription,
            Tag.ContrastBolusAgent,
            Tag.DoseReferenceUID,
            Tag.OperatorsName,
            Tag.PersonName,
            Tag.ProtocolName,
            Tag.VerifyingObserverName,
            Tag.VerifyingOrganization
    };

    private static final int[] D_DEVICE = {
            Tag.DetectorID,
            Tag.DeviceSerialNumber,
            Tag.StationName,
    };

    private static final int[] D_INSTITUTION = {
            Tag.InstitutionName,
    };

    private static final int[] D_DATES = {
            Tag.AcquisitionDateTime,
            Tag.ContentDate,
            Tag.ContentTime,
            Tag.EndAcquisitionDateTime,
            Tag.StartAcquisitionDateTime,
            Tag.VerificationDateTime // missing in Part 15
    };

    private static final int[] U = {
            Tag.AffectedSOPInstanceUID,
            Tag.ConcatenationUID,
            Tag.DimensionOrganizationUID,
            Tag.FailedSOPInstanceUIDList,
            Tag.FiducialUID,
            Tag.FrameOfReferenceUID,
            Tag.InstanceCreatorUID,
            Tag.IrradiationEventUID,
            Tag.LargePaletteColorLookupTableUID,
            Tag.MediaStorageSOPInstanceUID,
            Tag.ObservationSubjectUIDTrial,
            Tag.ObservationUID,
            Tag.PaletteColorLookupTableUID,
            Tag.PresentationDisplayCollectionUID,
            Tag.PresentationSequenceCollectionUID,
            Tag.ReferencedFrameOfReferenceUID,
            Tag.ReferencedGeneralPurposeScheduledProcedureStepTransactionUID,
            Tag.ReferencedObservationUIDTrial,
            Tag.ReferencedSOPInstanceUID,
            Tag.ReferencedSOPInstanceUIDInFile,
            Tag.RelatedFrameOfReferenceUID,
            Tag.RequestedSOPInstanceUID,
            Tag.SeriesInstanceUID,
            Tag.SOPInstanceUID,
            Tag.StorageMediaFileSetUID,
            Tag.StudyInstanceUID,
            Tag.SynchronizationFrameOfReferenceUID,
            Tag.TargetUID,
            Tag.TemplateExtensionCreatorUID,
            Tag.TemplateExtensionOrganizationUID,
            Tag.TrackingUID,
            Tag.TransactionUID,
            Tag.UID,
    };

    private static final int[] U_DEVICE = {
            Tag.DeviceUID,
    };
}
