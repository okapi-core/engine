ch_dir = ${HOME}/.okapi-data

FE_SETUP ?= fe-setup.json
REPO = ghcr.io/okapi-core
OKAPI_TEST_NET = okapi-test-network

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
POSTGRES_DB ?= okapi_oscar
POSTGRES_USER ?= okapi_oscar_user_admin
POSTGRES_PASSWORD ?= okapi_oscar_password
VAULT_ROOT_TOKEN ?= 0d94159a1b7e9c8f563e4e9e383185dc402ef70e

fe-dist:
	@python3 build-scripts/fe_dist_copy.py

package: copy-ch-sql
	mvn package -T 4 -DskipTests=true

package-ops: copy-ch-sql
	mvn -pl okapi-ops package -DskipTests=true

TAG ?= latest

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
	localstack stop || true
	$(DOCKER_RM) localstack-main
	docker run --name localstack-main \
	--network $(OKAPI_TEST_NET) \
	-p 4566:4566 -d \
	localstack/localstack:latest

tables:
	java -cp okapi-data-ddb/target/okapi-data-ddb-0.0.1-SNAPSHOT.jar  org.okapi.data.CreateDynamoDBTables $(ENV:ENV=test)
	java -cp okapi-data-ddb/target/okapi-data-ddb-0.0.1-SNAPSHOT.jar  org.okapi.data.CreateS3Bucket  $(ENV:ENV=test)

stop-test-infra:
	$(DOCKER_RM) localstack-main
	$(DOCKER_RM) okapi-clickhouse
	$(DOCKER_RM) okapi-postgres
	$(DOCKER_RM) okapi-vault-dev

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
	$(DOCKER_RM) okapi-clickhouse
	$(DOCKER_CMD) \
	okapi-clickhouse \
	--network $(OKAPI_TEST_NET) \
	-p 8123:8123 \
	-p 9000:9000 \
	-e CLICKHOUSE_PASSWORD=okapi_testing_password \
	--ulimit nofile=262144:262144 \
	-v "$(ch_dir)/ch_data:/var/lib/clickhouse/" \
	-v "$(ch_dir)/ch_logs:/var/log/clickhouse-server/" \
	clickhouse/clickhouse-server

postgres: testnetwork
	$(DOCKER_RM) okapi-postgres
	$(DOCKER_CMD) \
	okapi-postgres \
	--network $(OKAPI_TEST_NET) \
	-p 5432:5432 \
	-e POSTGRES_DB=$(POSTGRES_DB) \
	-e POSTGRES_USER=$(POSTGRES_USER) \
	-e POSTGRES_PASSWORD=$(POSTGRES_PASSWORD) \
	-v ./postgres-init/init.sql:/docker-entrypoint-initdb.d/init.sql \
	postgres:16

oscar-vault-dev:
	@if [ -z "$(OPENAI_API_KEY)" ]; then \
		echo "OPENAI_API_KEY is not set"; \
		exit 1; \
	fi
	$(DOCKER_RM) okapi-vault-dev
	$(DOCKER_CMD) \
	okapi-vault-dev \
	--network $(OKAPI_TEST_NET) \
	-p 8200:8200 \
	-e VAULT_DEV_ROOT_TOKEN_ID=$(VAULT_ROOT_TOKEN) \
	hashicorp/vault:1.15 \
	server -dev -dev-root-token-id=$(VAULT_ROOT_TOKEN)
	@echo "Waiting for Vault..."
	@sleep 2
	docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=$(VAULT_ROOT_TOKEN) okapi-vault-dev vault secrets enable -path=secret kv-v2 || true
	docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=$(VAULT_ROOT_TOKEN) okapi-vault-dev vault kv put secret/openai value="$(OPENAI_API_KEY)"

test-secret:
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar create-secrets \
	--endpoint http://localhost:4566 \
	--region us-west-2


migrate: package-ops
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar ddb-migrate --region us-west-2 --endpoint http://localhost:4566
	java -jar okapi-ops/target/okapi-ops-0.0.1-SNAPSHOT.jar ch-migrate --host localhost --port 8123 --user default --password okapi_testing_password

test-users:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar users-gen --host http://localhost --port 9001

test-spans:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-spans-gen --host http://localhost --port 9009

test-metrics:
	java -jar okapi-datagen/target/okapi-datagen-0.0.1-SNAPSHOT.jar astro-metrics-gen --host http://localhost --port 9009 --file data-gen/astro-metrics-config.json

test-data: test-users test-spans test-metrics

promql-testdata:
	scripts/promql/update-promqltestdata.sh

test-infra: testnetwork localstack ch migrate test-secret oscar-vault-dev postgres

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

start-oscar:
	export POSTGRES_HOST=localhost
	export POSTGRES_PORT=5432
	export OSCAR_DB_URL='jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/okapi_oscar'
	export OSCAR_DB_USER=okapi_oscar_user
	export OSCAR_DB_PASSWORD=okapi_oscar_password
	export OKAPI_INGESTER_HOST='localhost'
	export OKAPI_INGESTER_PORT=9009
	java -jar ./okapi-oscar/target/okapi-oscar-0.0.1-SNAPSHOT.jar \
		--okapi.oscar.cluster-endpoint=http://${OKAPI_INGESTER_HOST}:${OKAPI_INGESTER_PORT} \
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

start-okapi-web: package
	java -jar ./okapi-web/target/okapi-web-0.0.1-SNAPSHOT.jar &

start-okapi-ingester: package
	java -jar ./okapi-ingester/target/okapi-ingester-0.0.1-SNAPSHOT.jar &

test-env: package test-infra start-okapi-ingester start-oscar start-okapi-web test-data