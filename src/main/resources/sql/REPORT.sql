create table report (
	ReportID varchar(255) NOT NULL CONSTRAINT ReportID_pk PRIMARY KEY,
	Title varchar(512),
	Status varchar(32),
	ReportType varchar(32),
	Author varchar(255),
	BodyHtml clob,
	KeyImageRefs clob,
	SrSopInstanceUID varchar(255),
	StudyDate date,
	CreatedDateTime timestamp,
	ModifiedDateTime timestamp,
	PatientID varchar(255),
	foreign key(PatientID) references Patient(PatientID),
	StudyInstanceUID varchar(255),
	foreign key(StudyInstanceUID) references Study(StudyInstanceUID),
	SeriesInstanceUID varchar(255)
	)
