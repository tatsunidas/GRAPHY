create table staff (
	StaffID varchar(255) NOT NULL CONSTRAINT StaffID_pk PRIMARY KEY,
	Name varchar(255),
	Role varchar(64),
	Organization varchar(255),
	Department varchar(255),
	CreatedDateTime timestamp,
	ModifiedDateTime timestamp
	)
