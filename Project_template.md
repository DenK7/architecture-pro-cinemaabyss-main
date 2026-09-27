## Изучите [README.md](README.md) файл и структуру проекта.

## Задание 1

Старый контекст
[ссылка на файл](schemas/context/monolith-schema.md)

Схема контейнеров после переезда
[ссылка на файл](schemas/containers/containers-schema.md)

## Задание 2

### 1. Proxy

**Реализация:** [src/microservices/proxy](src/microservices/proxy)

### 2. Kafka

**Реализация:** [src/microservices/events](src/microservices/events)

## Задание 3

### CI/CD

**Сборка и публикация образов:** [.github/workflows/docker-build-push.yml](.github/workflows/docker-build-push.yml)

**API-тесты:** [.github/workflows/api-tests.yml](.github/workflows/api-tests.yml)

### Proxy в Kubernetes

**Proxy:** [src/kubernetes/proxy-service.yaml](src/kubernetes/proxy-service.yaml)

**Events:** [src/kubernetes/events-service.yaml](src/kubernetes/events-service.yaml)

**Ingress:** [src/kubernetes/ingress.yaml](src/kubernetes/ingress.yaml)

**Конфигурация:** [src/kubernetes/configmap.yaml](src/kubernetes/configmap.yaml)

**Kafka:** [src/kubernetes/kafka/kafka.yaml](src/kubernetes/kafka/kafka.yaml)

## Задание 4

**Helm-чарт:** [src/kubernetes/helm](src/kubernetes/helm)

**Параметры:** [src/kubernetes/helm/values.yaml](src/kubernetes/helm/values.yaml)

**Proxy:** [src/kubernetes/helm/templates/services/proxy-service.yaml](src/kubernetes/helm/templates/services/proxy-service.yaml)

**Events:** [src/kubernetes/helm/templates/services/events-service.yaml](src/kubernetes/helm/templates/services/events-service.yaml)

**Секрет для ghcr:** [src/kubernetes/helm/templates/dockerconfigsecret.yaml](src/kubernetes/helm/templates/dockerconfigsecret.yaml)

Секрет с доступом к ghcr создаётся, только если значение `imagePullSecrets.dockerconfigjson` передано при установке. Иначе используется секрет `dockerconfigjson`, заранее созданный в namespace вручную. Токен в `values.yaml` репозитория не хранится.

**Вывод http://cinemaabyss.example.com/api/movies:** [stage4.png](stage4.png)

# Задание 5
Компания планирует активно развиваться и для повышения надежности, безопасности, реализации сетевых паттернов типа Circuit Breaker и канареечного деплоя вам как архитектору необходимо развернуть istio и настроить circuit breaker для monolith и movies сервисов.

```bash

helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

helm install istio-base istio/base -n istio-system --set defaultRevision=default --create-namespace
helm install istio-ingressgateway istio/gateway -n istio-system
helm install istiod istio/istiod -n istio-system --wait

helm install cinemaabyss .\src\kubernetes\helm --namespace cinemaabyss --create-namespace

kubectl label namespace cinemaabyss istio-injection=enabled --overwrite

kubectl get namespace -L istio-injection

kubectl apply -f .\src\kubernetes\circuit-breaker-config.yaml -n cinemaabyss

```

Тестирование

# fortio
```bash
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.25/samples/httpbin/sample-client/fortio-deploy.yaml -n cinemaabyss
```

# Get the fortio pod name
```bash
FORTIO_POD=$(kubectl get pod -n cinemaabyss | grep fortio | awk '{print $1}')

kubectl exec -n cinemaabyss $FORTIO_POD -c fortio -- fortio load -c 50 -qps 0 -n 500 -loglevel Warning http://movies-service:8081/api/movies
```
Например,

```bash
kubectl exec -n cinemaabyss fortio-deploy-b6757cbbb-7c9qg  -c fortio -- fortio load -c 50 -qps 0 -n 500 -loglevel Warning http://movies-service:8081/api/movies
```

Вывод будет типа такого

```bash
IP addresses distribution:
10.106.113.46:8081: 421
Code 200 : 79 (15.8 %)
Code 500 : 22 (4.4 %)
Code 503 : 399 (79.8 %)
```
Можно еще проверить статистику

```bash
kubectl exec -n cinemaabyss fortio-deploy-b6757cbbb-7c9qg -c istio-proxy -- pilot-agent request GET stats | grep movies-service | grep pending
```

И там смотрим 

```bash
cluster.outbound|8081||movies-service.cinemaabyss.svc.cluster.local;.upstream_rq_pending_total: 311 - столько раз срабатывал circuit breaker
You can see 21 for the upstream_rq_pending_overflow value which means 21 calls so far have been flagged for circuit breaking.
```

Приложите скриншот работы circuit breaker'а

Удаляем все
```bash
istioctl uninstall --purge
kubectl delete namespace istio-system
kubectl delete all --all -n cinemaabyss
kubectl delete namespace cinemaabyss
```
