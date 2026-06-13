DO
$$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'okapi_oscar_user') THEN
    CREATE USER okapi_oscar_user WITH PASSWORD 'okapi_oscar_password';
  ELSE
    ALTER USER okapi_oscar_user WITH PASSWORD 'okapi_oscar_password';
  END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS okapi_oscar;
ALTER SCHEMA okapi_oscar OWNER TO okapi_oscar_user_admin;
GRANT CREATE ON SCHEMA okapi_oscar TO okapi_oscar_user;
GRANT USAGE ON SCHEMA okapi_oscar TO okapi_oscar_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA okapi_oscar TO okapi_oscar_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA okapi_oscar
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO okapi_oscar_user;

DO
$$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'okapi_web_user') THEN
    CREATE USER okapi_web_user WITH PASSWORD 'okapi_web_password';
  ELSE
    ALTER USER okapi_web_user WITH PASSWORD 'okapi_web_password';
  END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS okapi_web AUTHORIZATION okapi_web_user;
ALTER SCHEMA okapi_web OWNER TO okapi_web_user;
GRANT CONNECT ON DATABASE okapi_oscar TO okapi_web_user;
GRANT USAGE, CREATE ON SCHEMA okapi_web TO okapi_web_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA okapi_web TO okapi_web_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA okapi_web
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO okapi_web_user;
