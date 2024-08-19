create table study (
	StudyInstanceUID varchar(255) NOT NULL CONSTRAINT StudyInstanceUID_pk PRIMARY KEY,
	StudyDate date,
	StudyTime time,
	AccessionNumber varchar(50),
	ReferingPhysicianName varchar(255),
	StudyDescription varchar(80),
	StudyID varchar(255),
	ModalitiesInStudy varchar(10),
	NoOfSeries integer,
	NoOfInstances integer,
	RecdImgCnt integer,
	SendImgCnt integer,
	RetrieveAET varchar(50),
	DownloadStatus boolean,
	PatientAge varchar(10),
	PatientID varchar(255), 
	foreign key(PatientID) references Patient(PatientID)
	)
