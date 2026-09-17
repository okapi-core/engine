function ch_client(){
	clickhouse-client --host localhost --port 9000 --password okapi_testing_password
}

function pg_client(){
	PGPASSWORD=okapi_oscar_password psql --host localhost --port 5432 --username okapi_oscar_user -d okapi_oscar
}

function pg_web_client(){
	PGPASSWORD=okapi_web_password psql --host localhost --port 5432 --username okapi_web_user -d okapi_oscar
}
