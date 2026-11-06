-- Lock table of ShedLock: a scheduled job (refresh-token purge, issue #30) runs on one backend
-- instance at a time. Layout required by JdbcTemplateLockProvider; times come from the database
-- clock (usingDbTime), so instances with drifting clocks agree.
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);
