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

**Circuit breaker для monolith и movies-service:** [src/kubernetes/circuit-breaker-config.yaml](src/kubernetes/circuit-breaker-config.yaml)

**Kafka и Zookeeper:** [src/kubernetes/kafka/kafka.yaml](src/kubernetes/kafka/kafka.yaml), [src/kubernetes/helm/templates/kafka/kafka.yaml](src/kubernetes/helm/templates/kafka/kafka.yaml)

**Без перегрузки, 1 соединение — все запросы проходят:** [stage5.png](stage5.png)

**Работа circuit breaker, 50 параллельных соединений — 98 % запросов отсечено с 503:** [stage5_2.png](stage5_2.png)
