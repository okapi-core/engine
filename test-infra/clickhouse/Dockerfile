FROM clickhouse/clickhouse-server:25.12

COPY test-infra/clickhouse/users.xml /etc/clickhouse-server/users.d/okapi-test.xml
COPY test-infra/clickhouse/cluster.xml /etc/clickhouse-server/config.d/okapi-cluster.xml
