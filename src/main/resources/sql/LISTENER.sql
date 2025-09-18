create table listener (
	pk integer primary key GENERATED ALWAYS AS IDENTITY,
	logicalname varchar(255),
	aetitle varchar(255),
	host varchar(255),
	port varchar(255),
	storagelocation varchar(1024)
	)