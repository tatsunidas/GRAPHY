create table servers(
	pk integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY(Start with 1, Increment by 1),
	logicalname varchar(255) NOT NULL UNIQUE,
	aetitle varchar(255),
	hostname varchar(255),
	port integer,
	ciphers varchar(255),
	retrievetype varchar(100),
	wadocontext varchar(100),
	wadoport integer,
	wadoprotocol varchar(100),
	retrievets varchar(255),
	tls_enabled boolean default false
	)
