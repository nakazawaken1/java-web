CREATE TABLE t_account(
login_id VARCHAR(64) NOT NULL,
password VARCHAR(64) NOT NULL,
name VARCHAR(64) NOT NULL,
roles VARCHAR(256) NOT NULL,
PRIMARY KEY(login_id));