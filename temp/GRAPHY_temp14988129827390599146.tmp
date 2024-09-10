create table series (
	SeriesInstanceUID varchar(255) NOT NULL CONSTRAINT SeriesInstanceUID_pk PRIMARY KEY,
	SeriesNumber varchar(50),
	SeriesDate date,
	SeriesTime time,
	Modality varchar(10),
	ModelName varchar(50),
	SeriesDescription varchar(100),
	BodyPartExamined varchar(100),
	InstitutionName varchar(255),
	NoOfSeriesRelatedInstances integer,
	PatientID varchar(255),
	StudyInstanceUID varchar(255),
	foreign key(PatientID) references Patient(PatientID),
	foreign key(StudyInstanceUID) references Study(StudyInstanceUID)
	)