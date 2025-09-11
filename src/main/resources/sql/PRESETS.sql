create table presets(
	pk integer primary key GENERATED ALWAYS AS IDENTITY,
	presetname varchar(255),
	windowwidth numeric,
	windowlevel numeric,
	lut varchar(255),
	modality varchar(255)
	)
