create table report_template (
	TemplateID varchar(255) NOT NULL CONSTRAINT TemplateID_pk PRIMARY KEY,
	Name varchar(512),
	Category varchar(255),
	Body clob,
	CreatedDateTime timestamp,
	ModifiedDateTime timestamp
	)
