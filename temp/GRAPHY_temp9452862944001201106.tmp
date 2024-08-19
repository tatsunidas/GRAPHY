create table presets(
	pk integer primary key GENERATED ALWAYS AS IDENTITY,
	presetname varchar(255),
	windowwidth numeric,
	windowlevel numeric,
	modality_fk integer,
	foreign key(modality_fk) references modality(pk)
	)
