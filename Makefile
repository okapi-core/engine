# Build and container settings
ch_dir = ${HOME}/.okapi-data
FE_SETUP ?= fe-setup.json
REPO = ghcr.io/okapi-core
TAG ?= latest
DOCKER_COMPOSE ?= docker compose

# Test infrastructure endpoints
OKAPI_TEST_NET = okapi-test-infra-network
TEST_INFRA_COMPOSE ?= compose.test-infra.yaml
TEST_LOCALSTACK_ENDPOINT ?= http://127.0.0.1:4566
TEST_CLICKHOUSE_HOST ?= 127.0.0.1
TEST_CLICKHOUSE_PORT ?= 8123
TEST_POSTGRES_HOST ?= 127.0.0.1
TEST_POSTGRES_PORT ?= 5432
TEST_WEB_POSTGRES_SCHEMA ?= okapi_web
TEST_WEB_POSTGRES_USER ?= okapi_web_user
TEST_WEB_POSTGRES_PASSWORD ?= okapi_web_password
TEST_VAULT_ADDR ?= http://127.0.0.1:8200
POSTGRES_DB ?= okapi_oscar
POSTGRES_USER ?= okapi_oscar_user_admin
POSTGRES_PASSWORD ?= okapi_oscar_password
VAULT_ROOT_TOKEN ?= 0d94159a1b7e9c8f563e4e9e383185dc402ef70e
TEST_INFRA_ENV = \
	OKAPI_CH_DIR="$(ch_dir)" \
	POSTGRES_DB="$(POSTGRES_DB)" \
	POSTGRES_USER="$(POSTGRES_USER)" \
	POSTGRES_PASSWORD="$(POSTGRES_PASSWORD)" \
	OKAPI_WEB_DB_USER="$(TEST_WEB_POSTGRES_USER)" \
	OKAPI_WEB_DB_PASSWORD="$(TEST_WEB_POSTGRES_PASSWORD)" \
	VAULT_ROOT_TOKEN="$(VAULT_ROOT_TOKEN)"

# Local application endpoints
OKAPI_WEB_HOST ?= 127.0.0.1
OKAPI_WEB_PORT ?= 9001
OKAPI_INGESTER_HOST ?= 127.0.0.1
OKAPI_INGESTER_PORT ?= 9009

# Helm deployment settings
HELM ?= helm
HELM_NS ?= okapi
HELM_FLAGS ?=
MINIKUBE ?= minikube
LOCALSTACK_REPO ?= https://localstack.github.io/helm-charts
CLICKHOUSE_REPO ?= https://charts.clickhouse.com/
LOCALSTACK_CHART ?= localstack/localstack
CLICKHOUSE_CHART ?= clickhouse/clickhouse
LOCALSTACK_RELEASE ?= localstack
CLICKHOUSE_RELEASE ?= clickhouse
OKAPI_WEB_RELEASE ?= okapi-web
OKAPI_INGESTER_RELEASE ?= okapi-ingester
CERT_MANAGER_REPO ?= https://charts.jetstack.io
CERT_MANAGER_CHART ?= jetstack/cert-manager
CERT_MANAGER_RELEASE ?= cert-manager
CERT_MANAGER_NS ?= cert-manager
CLICKHOUSE_HOST ?= clickhouse.$(HELM_NS).svc.cluster.local
CLICKHOUSE_PORT ?= 8123
CLICKHOUSE_USER ?= default
CLICKHOUSE_PASSWORD ?=
OKAPI_AWS_ENDPOINT ?= http://localstack.$(HELM_NS).svc.cluster.local:4566
OKAPI_CLUSTER_ENDPOINT ?= http://okapi-ingester.$(HELM_NS).svc.cluster.local:9009
HELM_CHART_REPO ?= oci://ghcr.io/okapi-core
HELM_CHART_DIST ?= helm/dist

.PHONY: test-infra test-infra-up test-infra-down init-test-postgres

fe-dist:
	@python3 build-scripts/fe_dist_copy.py

package: copy-ch-sql
	mvn package -T 4 -DskipTests=true

package-ops: copy-ch-sql
	mvn -pl okapi-ops package -DskipTests=true

docker-okapi-ingester: package
	docker build -t $(REPO)/okapi-ingester:$(TAG) -f okapi-ingester/Dockerfile okapi-ingester

docker-okapi-web: fe-dist package
	docker build -t $(REPO)/okapi-web:$(TAG) -f okapi-web/Dockerfile okapi-web

docker-okapi-ops: package
	docker build -t $(REPO)/okapi-ops:$(TAG) -f okapi-ops/Dockerfile okapi-ops

docker-okapi-oscar: package
	docker build -t $(REPO)/okapi-oscar:$(TAG) -f okapi-oscar/Dockerfile okapi-oscar

docker-all: docker-okapi-ingester docker-okapi-web docker-okapi-ops docker-okapi-oscar

DOCKER_CMD := docker run -d --name
DOCKER_RM := sh stop_and_remove_container.sh
DOCKER_STOP := docker stop

localstack:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) up -d --wait localstack

tables:
	java -cp okapi-data-ddb/target/okapi-data-ddb-0.0.1-SNAPSHOT.jar  org.okapi.data.CreateDynamoDBTables $(ENV:ENV=test)
	java -cp okapi-data-ddb/target/okapi-data-ddb-0.0.1-SNAPSHOT.jar  org.okapi.data.CreateS3Bucket  $(ENV:ENV=test)

test-infra-down:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) down --remove-orphans

localstack-k8s:
	kubectl apply -n okapi -f okapi-ingester/local-stack-yamls/localstack.yml

helm-okapi-web:
	$(MINIKUBE) image load $(REPO)/okapi-web:$(TAG)
	$(HELM) upgrade --install $(OKAPI_WEB_RELEASE) helm/okapi-web --namespace $(HELM_NS) --create-namespace \
	--set springOverrides.clusterEndpoint=$(OKAPI_CLUSTER_ENDPOINT) \
	--set springOverrides.okapi.aws.endpoint=$(OKAPI_AWS_ENDPOINT) \
	$(HELM_FLAGS)

helm-okapi-ingester:
	$(MINIKUBE) image load $(REPO)/okapi-ingester:$(TAG)
	$(HELM) upgrade --install $(OKAPI_INGESTER_RELEASE) helm/okapi-ingester --namespace $(HELM_NS) --create-namespace \
	--set springOverrides.okapi.clickhouse.host=$(CLICKHOUSE_HOST) \
	--set springOverrides.okapi.clickhouse.port=$(CLICKHOUSE_PORT) \
	--set springOverrides.okapi.clickhouse.username=$(CLICKHOUSE_USER) \
	--set springOverrides.okapi.clickhouse.password=$(CLICKHOUSE_PASSWORD) \
	--set springOverrides.okapi.clickhouse.secure=false \
	--set springOverrides.okapi.aws.endpoint=$(OKAPI_AWS_ENDPOINT) \
	$(HELM_FLAGS)

helm-localstack:
	$(HELM) repo add localstack $(LOCALSTACK_REPO) --force-update
	$(HELM) repo update
	$(HELM) upgrade --install $(LOCALSTACK_RELEASE) $(LOCALSTACK_CHART) --namespace $(HELM_NS) --create-namespace $(HELM_FLAGS)

helm-clickhouse:
	$(HELM) repo add jetstack $(CERT_MANAGER_REPO) --force-update
	$(HELM) repo update
	$(HELM) upgrade --install $(CERT_MANAGER_RELEASE) $(CERT_MANAGER_CHART) --namespace $(CERT_MANAGER_NS) --create-namespace --set installCRDs=true $(HELM_FLAGS)
	$(HELM) upgrade --install okapi-clickhouse oci://ghcr.io/clickhouse/clickhouse-operator-helm \
	--create-namespace \
	-n $(HELM_NS)

helm-package:
	rm -f $(HELM_CHART_DIST)/okapi-ingester-*.tgz $(HELM_CHART_DIST)/okapi-web-*.tgz
	mkdir -p $(HELM_CHART_DIST)
	$(HELM) package helm/okapi-ingester --destination $(HELM_CHART_DIST)
	$(HELM) package helm/okapi-web --destination $(HELM_CHART_DIST)
	$(HELM) push $(HELM_CHART_DIST)/okapi-ingester-*.tgz $(HELM_CHART_REPO)
	$(HELM) push $(HELM_CHART_DIST)/okapi-web-*.tgz $(HELM_CHART_REPO)

testnetwork:
	sh test-network.sh $(OKAPI_TEST_NET)

run-zk:
	$(DOCKER_RM) zookeeper
	$(DOCKER_CMD) zookeeper --network $(OKAPI_TEST_NET) -p 2181:2181 zookeeper:latest

ch:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) up -d --wait clickhouse

postgres:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) up -d --wait postgres
	$(MAKE) init-test-postgres

init-test-postgres:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) exec -T postgres \
		psql -v ON_ERROR_STOP=1 -U $(POSTGRES_USER) -d $(POSTGRES_DB) \
		-f /docker-entrypoint-initdb.d/init.sql

oscar-vault-dev:
	@if [ -z "$(OPENAI_API_KEY)" ]; then \
		echo "OPENAI_API_KEY is not set"; \
		exit 1; \
	fi
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) up -d --wait vault
	$(TEST_INFRA_ENV) OPENAI_API_KEY="$(OPENAI_API_KEY)" \
		$(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) --profile init run --rm vault-init

test-secret:
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar create-secrets \
	--endpoint $(TEST_LOCALSTACK_ENDPOINT) \
	--region us-west-2


migrate: package-ops
	$(MAKE) migrate-test-datastores

migrate-test-datastores:
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar ddb-migrate --region us-west-2 --endpoint $(TEST_LOCALSTACK_ENDPOINT)
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar ch-migrate --host $(TEST_CLICKHOUSE_HOST) --port $(TEST_CLICKHOUSE_PORT) --user default --password okapi_testing_password

test-users:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar users-gen --host http://$(OKAPI_WEB_HOST) --port $(OKAPI_WEB_PORT)

test-spans:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-spans-gen --host http://$(OKAPI_INGESTER_HOST) --port $(OKAPI_INGESTER_PORT)

test-metrics:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-metrics-gen --host http://$(OKAPI_INGESTER_HOST) --port $(OKAPI_INGESTER_PORT) --file data-gen/astro-metrics-config.json

test-data: test-users test-spans test-metrics

promql-testdata:
	scripts/promql/update-promqltestdata.sh

test-infra-up:
	@if [ -z "$(OPENAI_API_KEY)" ]; then \
		echo "OPENAI_API_KEY is not set"; \
		exit 1; \
	fi
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) \
		up -d --wait clickhouse localstack postgres vault
	$(MAKE) init-test-postgres
	$(TEST_INFRA_ENV) OPENAI_API_KEY="$(OPENAI_API_KEY)" \
		$(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) --profile init run --rm vault-init

test-infra:
	$(MAKE) test-infra-up
	$(MAKE) migrate-test-datastores
	$(MAKE) test-secret

run-ingester:
	java -jar okapi-ingester/target/okapi-ingester-0.0.1-SNAPSHOT.jar &

build: run-ingester
	mvn package -T 4

all: package test-infra build docker-all

test-run-ingester:
	$(DOCKER_RM) okapi-ingester
	docker run -p 9009:9009 -d \
	--network $(OKAPI_TEST_NET) \
	--name okapi-ingester \
	$(REPO)/okapi-ingester:$(TAG) \
	--okapi.clickhouse.host=okapi-clickhouse \
	--okapi.chMetricsWal=/wal/metrics \
	--okapi.chLogsWal=/wal/metrics \
	--okapi.chTracesWal=/wal/metrics


test-run-web:
	$(DOCKER_RM) okapi-web
	docker run -p 9001:9001 -d \
	--network $(OKAPI_TEST_NET) \
	-e AWS_ACCESS_KEY_ID=$(AWS_ACCESS_KEY_ID) \
	-e AWS_SECRET_ACCESS_KEY=$(AWS_SECRET_ACCESS_KEY) \
	-e AWS_REGION=$(AWS_REGION) \
	--name okapi-web \
	$(REPO)/okapi-web:$(TAG) \
	--clusterEndpoint=http://okapi-ingester:9009 \
	--okapi.aws.endpoint=http://localstack-main:4566

test-run: test-run-ingester test-run-web

test: package test-infra start-okapi-ingester-jar start-okapi-web-jar
	OKAPI_TEST_LOCALSTACK_ENDPOINT="$(TEST_LOCALSTACK_ENDPOINT)" \
	OKAPI_TEST_CLICKHOUSE_HOST="$(TEST_CLICKHOUSE_HOST)" \
	OKAPI_TEST_CLICKHOUSE_PORT="$(TEST_CLICKHOUSE_PORT)" \
	OKAPI_WEB_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)?currentSchema=$(TEST_WEB_POSTGRES_SCHEMA)" \
	OKAPI_WEB_DB_USER="$(TEST_WEB_POSTGRES_USER)" \
	OKAPI_WEB_DB_PASSWORD="$(TEST_WEB_POSTGRES_PASSWORD)" \
	OSCAR_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/okapi_oscar?currentSchema=okapi_oscar" \
	VAULT_ADDR="$(TEST_VAULT_ADDR)" \
		mvn test

publish-docker:
	docker push $(REPO)/okapi-web:$(TAG)
	docker push $(REPO)/okapi-ingester:$(TAG)
	docker push $(REPO)/okapi-ops:$(TAG)

publish-okapi-cp:
	cd okapi-cp && poetry build
	cd okapi-cp && poetry publish

publish: publish-docker publish-okapi-cp

publish-okapi-cp-test:
	cd okapi-cp && poetry build
	cd okapi-cp && poetry publish -r testpypi

copy-ch-sql:
	cp -r ./okapi-ingester/src/main/resources/ch/*.sql ./okapi-ops/src/main/resources/ch/

start-oscar-jar:
	OSCAR_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/okapi_oscar?currentSchema=okapi_oscar" \
	OSCAR_DB_USER="okapi_oscar_user" \
	OSCAR_DB_PASSWORD="okapi_oscar_password" \
	OKAPI_CLUSTER_ENDPOINT="http://$(OKAPI_INGESTER_HOST):$(OKAPI_INGESTER_PORT)" \
		java -jar ./okapi-oscar/target/okapi-oscar-0.0.1-SNAPSHOT.jar \
		--okapi.oscar.vault.address='' \
		--okapi.oscar.openai.api-key-path=env://OPENAI_API_KEY &

start-oscar-dummy: package
	java -jar ./okapi-oscar/target/okapi-oscar-0.0.1-SNAPSHOT.jar \
		--spring.profiles.active=dummy
		--okapi.oscar.cluster-endpoint=http://${OKAPI_INGESTER_HOST}:${OKAPI_INGESTER_PORT} \
		--okapi.oscar.vault.address='' \
		--okapi.oscar.openai.api-key-path=env://OPENAI_API_KEY &

test-all: package run-ingester
	mvn test -Dmaven.test.failure.ignore=true

start-okapi-web-jar: package
	OKAPI_AWS_ENDPOINT="$(TEST_LOCALSTACK_ENDPOINT)" \
		java -jar ./okapi-web/target/okapi-web-0.0.1-SNAPSHOT.jar &

start-okapi-ingester-jar: package
	OKAPI_CLICKHOUSE_HOST="$(TEST_CLICKHOUSE_HOST)" \
	OKAPI_CLICKHOUSE_PORT="$(TEST_CLICKHOUSE_PORT)" \
		java -jar ./okapi-ingester/target/okapi-ingester-0.0.1-SNAPSHOT.jar &

test-env: package test-infra start-okapi-ingester-jar start-oscar-jar start-okapi-web-jar test-data
