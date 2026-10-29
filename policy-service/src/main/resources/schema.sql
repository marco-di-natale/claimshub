CREATE SEQUENCE policy_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE policy (
    id            NUMBER(19) PRIMARY KEY,
    policy_number VARCHAR2(20) NOT NULL UNIQUE,
    holder_name   VARCHAR2(100) NOT NULL,
    policy_type   VARCHAR2(10) NOT NULL,
    status        VARCHAR2(10) NOT NULL,
    premium       NUMBER(10,2) NOT NULL,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL
);