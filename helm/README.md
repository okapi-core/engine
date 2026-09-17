# Helm charts

Example:

```yaml
springOverrides:
  okapi:
    clickhouse:
      host: clickhouse
      port: 8123
      secure: false
    aws:
      region: eu-west-2
```

Install:

```sh
helm install okapi-web helm/okapi-web -f values.yaml
helm install okapi-ingester helm/okapi-ingester -f values.yaml
```

## HA deployment (self-hosted)
In this setup we deploy a replicated version of okapi as a service with sub-components fronted by a load balancer.

Steps:
1) Deploy ClickHouse first.
   - Use the official ClickHouse chart or your own manifests.
   - Note the ClickHouse service DNS name (example):
     `clickhouse.okapi.svc.cluster.local`

2) Ensure AWS credentials are available to the okapi workloads.

3) Deploy okapi-ingester as a replicated service (ClusterIP). We need to point to ClickHouse via `springOverrides`.

```sh
helm install okapi-ingester helm/okapi-ingester \
  --namespace okapi --create-namespace \
  --set service.type=ClusterIP \
  --set springOverrides.okapi.clickhouse.host=clickhouse.okapi.svc.cluster.local \
  --set springOverrides.okapi.clickhouse.port=8123 \
  --set springOverrides.okapi.clickhouse.username=default \
  --set springOverrides.okapi.clickhouse.password=secure_prod_password \
  --set springOverrides.okapi.clickhouse.secure=false
```

4) Deploy okapi-web. Similar to `okapi-ingester`, we deploy a replicated version this time passing ClusterIp of okapi-ingester via `springOverrides`. 
Note - `okapi-web` serves API calls and also serves the Okapi's web UI. Here we use a `LoadBalancer` deployment so that he IP is accessible outside of Kubernetes.

```sh
helm install okapi-web helm/okapi-web \
  --namespace okapi \
  --set replicaCount=2 \
  --set service.type=LoadBalancer \
  --set springOverrides.clusterEndpoint=http://okapi-ingester.okapi.svc.cluster.local:9009
```

```sh
kubectl get svc -n okapi okapi-web
```

To fetch the external IP for the web UI once the LoadBalancer is provisioned, run:

```sh
kubectl get svc -n okapi okapi-web -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

You can now open this UI in the browser and get started :).

# Overriding spring configs
Okapi uses SPRING_APPLICATION_JSON to supply overrides to spring configs that the various components depend on. 
`okapi-web` and `okapi-ingester` use a single application.yaml file. The file is located at `src/main/resources/application.yaml` in the respective Maven sub-module.
