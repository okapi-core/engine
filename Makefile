# Build and container settings
ch_dir = ${HOME}/.okapi-data
FE_SETUP ?= fe-setup.json
REPO ?= ghcr.io/okapi-core
TAG ?= latest
DOCKER_BUILD ?= docker build
DOCKER_PUSH ?= docker push
DOCKER_PLATFORMS ?= linux/amd64,linux/arm64
GHCR_REGISTRY ?= ghcr.io
CI_DOCKER_REPO ?= $(REPO)
DOCKER_COMPOSE ?= docker compose
OTEL_DEMO_COMMIT ?= 4baa77b
OTEL_DEMO_DIR ?= $(HOME)/.harness/otel-demo
OTEL_DEMO_REPO ?= https://github.com/open-telemetry/opentelemetry-demo.git

# Test infrastructure endpoints
OKAPI_TEST_NET = okapi-test-infra-network
TEST_INFRA_COMPOSE ?= compose.test-infra.yaml
TEST_AWS_ENDPOINT ?= https://dynamodb.eu-west-2.amazonaws.com
OKAPI_AWS_ENDPOINT ?= https://dynamodb.eu-west-2.amazonaws.com
OKAPI_AWS_REGION ?= eu-west-2
TEST_CLICKHOUSE_HOST ?= 127.0.0.1
TEST_CLICKHOUSE_PORT ?= 8123
TEST_POSTGRES_HOST ?= 127.0.0.1
TEST_POSTGRES_PORT ?= 5432
TEST_WEB_POSTGRES_SCHEMA ?= okapi_web
TEST_WEB_POSTGRES_USER ?= okapi_web_user
TEST_WEB_POSTGRES_PASSWORD ?= okapi_web_password
TEST_WEB_POSTGRES_MIGRATION_USER ?= okapi_web_migration_user
TEST_WEB_POSTGRES_MIGRATION_PASSWORD ?= okapi_web_migration_password
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
	OKAPI_WEB_DB_MIGRATION_USER="$(TEST_WEB_POSTGRES_MIGRATION_USER)" \
	OKAPI_WEB_DB_MIGRATION_PASSWORD="$(TEST_WEB_POSTGRES_MIGRATION_PASSWORD)" \
	VAULT_ROOT_TOKEN="$(VAULT_ROOT_TOKEN)"

# Local application endpoints
OKAPI_WEB_HOST ?= 127.0.0.1
OKAPI_WEB_PORT ?= 9001
OKAPI_INGESTER_HOST ?= 127.0.0.1
OKAPI_INGESTER_PORT ?= 9009
OKAPI_OSCAR_PORT ?= 9002

# Helm deployment settings
HELM ?= helm
HELM_NS ?= okapi
HELM_FLAGS ?=
MINIKUBE ?= minikube
CLICKHOUSE_REPO ?= https://charts.clickhouse.com/
CLICKHOUSE_CHART ?= clickhouse/clickhouse
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
OKAPI_CLUSTER_ENDPOINT ?= http://okapi-ingester.$(HELM_NS).svc.cluster.local:9009
HELM_CHART_REPO ?= oci://ghcr.io/okapi-core
HELM_CHART_DIST ?= helm/dist

.PHONY: test-infra test-infra-up test-infra-down init-test-postgres otel-harness otel-harness-down kill-stray-instances docker-okapi-ingester docker-okapi-web docker-okapi-ops docker-okapi-oscar docker-all docker-push-web docker-push-oscar docker-push-ingester docker-push-ops docker-push-all package-dashboard-yaml-lint lint-dashboard-yamls release spotless

spotless:
	mvn spotless:apply
	@if [ -n "$$(git status --porcelain)" ]; then \
		git add -A; \
		git commit -m "spotless"; \
	fi

fe-dist:
	@python3 build-scripts/fe_dist_copy.py

kill-stray-instances:
	@for service_port in \
		"okapi-ingester:$(OKAPI_INGESTER_PORT)" \
		"okapi-web:$(OKAPI_WEB_PORT)" \
		"okapi-oscar:$(OKAPI_OSCAR_PORT)"; do \
		service="$${service_port%%:*}"; \
		port="$${service_port##*:}"; \
		pids="$$(lsof -ti tcp:$$port -sTCP:LISTEN || true)"; \
		if [ -z "$$pids" ]; then \
			echo "$$service: no listener on port $$port"; \
		else \
			echo "$$service: killing PID(s) $$pids on port $$port"; \
			kill $$pids; \
		fi; \
	done

package: copy-ch-sql
	mvn package -T 4 -DskipTests=true

package-ops: copy-ch-sql
	mvn -pl okapi-ops -am package -DskipTests=true

package-dashboard-yaml-lint: copy-ch-sql
	mvn -pl okapi-web -am package -DskipTests=true

lint-dashboard-yamls: package-dashboard-yaml-lint
	java -jar okapi-web/target/okapi-web-0.0.1-SNAPSHOT-dashboard-yaml-lint.jar dashboard-yamls

docker-okapi-ingester: package
	$(DOCKER_BUILD) -t $(REPO)/okapi-ingester:$(TAG) -f okapi-ingester/Dockerfile okapi-ingester

docker-okapi-web: package
	$(DOCKER_BUILD) -t $(REPO)/okapi-web:$(TAG) -f okapi-web/Dockerfile okapi-web

docker-okapi-ops: package
	$(DOCKER_BUILD) -t $(REPO)/okapi-ops:$(TAG) -f okapi-ops/Dockerfile okapi-ops

docker-okapi-oscar: package
	$(DOCKER_BUILD) -t $(REPO)/okapi-oscar:$(TAG) -f okapi-oscar/Dockerfile okapi-oscar

docker-all: docker-okapi-ingester docker-okapi-web docker-okapi-ops docker-okapi-oscar

docker-push-web:
	$(DOCKER_PUSH) $(REPO)/okapi-web:$(TAG)

docker-push-oscar:
	$(DOCKER_PUSH) $(REPO)/okapi-oscar:$(TAG)

docker-push-ingester:
	$(DOCKER_PUSH) $(REPO)/okapi-ingester:$(TAG)

docker-push-ops:
	$(DOCKER_PUSH) $(REPO)/okapi-ops:$(TAG)

docker-push-all: docker-push-web docker-push-oscar docker-push-ingester docker-push-ops

release:
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "Working tree is not clean; commit or stash changes before releasing."; \
		git status --short; \
		exit 1; \
	fi
	@tag="release_$$(date -u +%Y%m%d_%H%M%SZ)"; \
	if git rev-parse -q --verify "refs/tags/$$tag" >/dev/null; then \
		echo "Tag $$tag already exists."; \
		exit 1; \
	fi; \
	git tag "$$tag"; \
	git push origin "$$tag"; \
	echo "Pushed release tag $$tag"

otel-harness:
	mkdir -p "$(dir $(OTEL_DEMO_DIR))"
	@if [ ! -d "$(OTEL_DEMO_DIR)/.git" ]; then \
		git clone "$(OTEL_DEMO_REPO)" "$(OTEL_DEMO_DIR)"; \
	fi
	rm -f "$(OTEL_DEMO_DIR)/compose.extras.yaml"
	rm -f "$(OTEL_DEMO_DIR)/src/otel-collector/otelcol-config-extras.yml"
	git -C "$(OTEL_DEMO_DIR)" fetch origin
	git -C "$(OTEL_DEMO_DIR)" checkout --detach "$(OTEL_DEMO_COMMIT)"
	test "$$(git -C "$(OTEL_DEMO_DIR)" rev-parse --short=7 HEAD)" = "$(OTEL_DEMO_COMMIT)"
	cp harness-artifacts/otel/compose.extras.yaml "$(OTEL_DEMO_DIR)/compose.extras.yaml"
	mkdir -p "$(OTEL_DEMO_DIR)/src/otel-collector"
	cp harness-artifacts/otel/otelcol-config-extras.yml "$(OTEL_DEMO_DIR)/src/otel-collector/otelcol-config-extras.yml"
	OKAPI_REPO_ROOT="$(CURDIR)" TAG="$(TAG)" $(MAKE) -C "$(OTEL_DEMO_DIR)" start-no-o11y

otel-harness-down:
	@if [ ! -d "$(OTEL_DEMO_DIR)" ]; then \
		echo "OpenTelemetry harness checkout not found at $(OTEL_DEMO_DIR)"; \
		exit 0; \
	fi
	OKAPI_REPO_ROOT="$(CURDIR)" TAG="$(TAG)" $(MAKE) -C "$(OTEL_DEMO_DIR)" stop

DOCKER_CMD := docker run -d --name
DOCKER_RM := sh stop_and_remove_container.sh
DOCKER_STOP := docker stop


test-infra-down:
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) down --remove-orphans


helm-okapi-web:
	$(MINIKUBE) image load $(REPO)/okapi-web:$(TAG)
	$(HELM) upgrade --install $(OKAPI_WEB_RELEASE) helm/okapi-web --namespace $(HELM_NS) --create-namespace \
	--set springOverrides.clusterEndpoint=$(OKAPI_CLUSTER_ENDPOINT)
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
	--set springOverrides.okapi.aws.region=$(OKAPI_AWS_REGION) \
	$(HELM_FLAGS)

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

migrate: package-ops
	$(MAKE) migrate-test-datastores

migrate-test-datastores:
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar ch-migrate --host $(TEST_CLICKHOUSE_HOST) --port $(TEST_CLICKHOUSE_PORT) --user default --password okapi_testing_password
	$(MAKE) migrate-test-postgres

migrate-test-postgres:
	OKAPI_WEB_DB_MIGRATION_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)?currentSchema=$(TEST_WEB_POSTGRES_SCHEMA)" \
	OKAPI_WEB_DB_MIGRATION_USER="$(TEST_WEB_POSTGRES_MIGRATION_USER)" \
	OKAPI_WEB_DB_MIGRATION_PASSWORD="$(TEST_WEB_POSTGRES_MIGRATION_PASSWORD)" \
		java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar pg-migrate

validate-test-postgres:
	OKAPI_WEB_DB_MIGRATION_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)?currentSchema=$(TEST_WEB_POSTGRES_SCHEMA)" \
	OKAPI_WEB_DB_MIGRATION_USER="$(TEST_WEB_POSTGRES_MIGRATION_USER)" \
	OKAPI_WEB_DB_MIGRATION_PASSWORD="$(TEST_WEB_POSTGRES_MIGRATION_PASSWORD)" \
		java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar pg-validate

test-users:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar users-gen --host http://$(OKAPI_WEB_HOST) --port $(OKAPI_WEB_PORT)

test-spans:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-spans-gen --host http://$(OKAPI_INGESTER_HOST) --port $(OKAPI_INGESTER_PORT)

test-metrics:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-metrics-gen --host http://$(OKAPI_INGESTER_HOST) --port $(OKAPI_INGESTER_PORT) --file data-gen/astro-metrics-config.json

test-data: test-users test-spans test-metrics

promql-testdata:
	scripts/promql/update-promqltestdata.sh

setup-test-infra:
	@if [ -z "$(OPENAI_API_KEY)" ]; then \
		echo "OPENAI_API_KEY is not set"; \
		exit 1; \
	fi
	$(TEST_INFRA_ENV) $(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) \
		up -d --wait clickhouse postgres vault
	$(MAKE) init-test-postgres
	$(TEST_INFRA_ENV) OPENAI_API_KEY="$(OPENAI_API_KEY)" \
		$(DOCKER_COMPOSE) -f $(TEST_INFRA_COMPOSE) --profile init run --rm vault-init

test-infra-up: setup-test-infra migrate

test-infra:
	$(MAKE) test-infra-up
	$(MAKE) migrate-test-datastores
	$(MAKE) validate-test-postgres

run-ingester:
	java -jar okapi-ingester/target/okapi-ingester-0.0.1-SNAPSHOT.jar --spring.profiles.active=ch &

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
	--clusterEndpoint=http://okapi-ingester:9009

test-run: test-run-ingester test-run-web

test: package test-infra start-okapi-ingester-jar start-okapi-web-jar
	OKAPI_TEST_AWS_ENDPOINT="$(TEST_AWS_ENDPOINT)" \
	OKAPI_TEST_CLICKHOUSE_HOST="$(TEST_CLICKHOUSE_HOST)" \
	OKAPI_TEST_CLICKHOUSE_PORT="$(TEST_CLICKHOUSE_PORT)" \
	OKAPI_WEB_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)?currentSchema=$(TEST_WEB_POSTGRES_SCHEMA)" \
	OKAPI_WEB_DB_USER="$(TEST_WEB_POSTGRES_USER)" \
	OKAPI_WEB_DB_PASSWORD="$(TEST_WEB_POSTGRES_PASSWORD)" \
	OKAPI_WEB_DB_MIGRATION_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)" \
	OKAPI_WEB_DB_MIGRATION_USER="$(TEST_WEB_POSTGRES_MIGRATION_USER)" \
	OKAPI_WEB_DB_MIGRATION_PASSWORD="$(TEST_WEB_POSTGRES_MIGRATION_PASSWORD)" \
	TEST_POSTGRES_ADMIN_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)" \
	TEST_POSTGRES_ADMIN_USER="$(POSTGRES_USER)" \
	TEST_POSTGRES_ADMIN_PASSWORD="$(POSTGRES_PASSWORD)" \
	OSCAR_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/okapi_oscar?currentSchema=okapi_oscar" \
	VAULT_ADDR="$(TEST_VAULT_ADDR)" \
		mvn test

publish-docker:
	docker push $(REPO)/okapi-web:$(TAG)
	docker push $(REPO)/okapi-ingester:$(TAG)
	docker push $(REPO)/okapi-ops:$(TAG)

publish: publish-docker

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
	OKAPI_WEB_DB_URL="jdbc:postgresql://$(TEST_POSTGRES_HOST):$(TEST_POSTGRES_PORT)/$(POSTGRES_DB)?currentSchema=$(TEST_WEB_POSTGRES_SCHEMA)" \
	OKAPI_WEB_DB_USER="$(TEST_WEB_POSTGRES_USER)" \
	OKAPI_WEB_DB_PASSWORD="$(TEST_WEB_POSTGRES_PASSWORD)" \
		java -jar ./okapi-web/target/okapi-web-0.0.1-SNAPSHOT.jar &

start-okapi-ingester-jar: package
	OKAPI_CLICKHOUSE_HOST="$(TEST_CLICKHOUSE_HOST)" \
	OKAPI_CLICKHOUSE_PORT="$(TEST_CLICKHOUSE_PORT)" \
		java -jar ./okapi-ingester/target/okapi-ingester-0.0.1-SNAPSHOT.jar &

test-env: package test-infra start-okapi-ingester-jar start-oscar-jar start-okapi-web-jar test-data
