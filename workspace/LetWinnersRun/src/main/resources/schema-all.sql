DROP TABLE trades IF EXISTS;

CREATE TABLE trades (
	txn_date date,
	ticker varchar(10),
	txn_type varchar(5),
	txn_price double
);