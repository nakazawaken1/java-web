CREATE TABLE t_session(
id VARCHAR(64) NOT NULL,
name VARCHAR(128) NOT NULL,
value BLOB,
last_access TIMESTAMP NOT NULL,
PRIMARY KEY(id, name));