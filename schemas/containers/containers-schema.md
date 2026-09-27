```mermaid
%% C4 Level 2 — Containers. КиноБездна, To-Be
%% Переход от монолита к микросервисам по паттерну Strangler Fig через API Gateway.
flowchart TB
    user["Пользователь<br/>[Person]"]
    admin["Контент-менеджер<br/>[Person]"]

    subgraph clients["Клиентские приложения"]
        web["Web-приложение<br/>[Container: Web SPA]<br/>Браузеры и ноутбуки,<br/>админка каталога и тарифов"]
        mobile["Мобильное приложение<br/>[Container: iOS / Android]<br/>Смартфоны и планшеты"]
        tv["Smart TV приложение<br/>[Container: Smart TV]<br/>Телевизоры"]
    end

    subgraph cloud["КиноБездна — Kubernetes"]
        gw["API Gateway<br/>[Container: Go, Proxy Service]<br/>Единый вход, проверка токена,<br/>ограничение частоты,<br/>Strangler Fig: монолит → микросервисы"]

        subgraph bff["Backend for Frontend"]
            bffweb["Web BFF<br/>[Container: Go]<br/>Ответы под web"]
            bffmob["Mobile BFF<br/>[Container: Go]<br/>Облегчённые ответы<br/>для мобильных"]
            bfftv["Smart TV BFF<br/>[Container: Go]<br/>Ответы под интерфейс ТВ"]
        end

        subgraph contentgroup["Пользователи и контент"]
            usr["User Service<br/>[Container: Go]<br/>Учётные записи, профили,<br/>аутентификация, выдача токенов"]
            movies["Movies Service<br/>[Container: Go]<br/>Метаданные фильмов: жанры,<br/>актёры, описания, рейтинг"]
            activity["Ratings & Favorites Service<br/>[Container: Go]<br/>Оценки, папки избранного,<br/>история просмотров"]
            video["Video Service<br/>[Container: Go]<br/>Источники видео,<br/>выдача ссылок на просмотр"]
            recs["Recommendations Adapter<br/>[Container: Go]<br/>Обмен с рекомендательной<br/>системой, кэш подборок"]
        end

        subgraph billing["Монетизация"]
            sub["Subscription Service<br/>[Container: Go]<br/>Тарифы, оформление<br/>и продление подписок"]
            pay["Payment Service<br/>[Container: Go]<br/>Платежи, интеграция<br/>с платёжными системами"]
            disc["Discount & Loyalty Service<br/>[Container: Go]<br/>Скидки, промокоды,<br/>бонусы партнёров"]
        end

        events["Events Service<br/>[Container: Go]<br/>Публикация и обработка<br/>событий User / Movie / Payment"]

        subgraph legacy["Legacy — выводится из эксплуатации"]
            mono["Монолит<br/>[Container: Go]<br/>Функциональность,<br/>ещё не вынесенная в сервисы"]
            monodb[("PostgreSQL<br/>[Database]<br/>Общая БД монолита")]
        end

        kafka[("Kafka<br/>[Message Broker]<br/>user-, movie-, payment-,<br/>subscription-events")]
        redis[("Redis<br/>[Cache]<br/>Частота запросов,<br/>кэш подборок")]
        pg[("PostgreSQL<br/>[Database]<br/>Отдельная БД на каждый сервис")]
    end

    recsys["Рекомендательная система<br/>[External System]<br/>Персональные подборки"]
    payext["Платёжные системы<br/>[External System]<br/>Эквайринг"]
    loyalty["Сервисы лояльности<br/>[External System]<br/>Бонусные программы партнёров"]
    market["Маркетплейсы<br/>[External System]<br/>Продажа подписок и промокодов"]
    source["Источники контента<br/>[External System]<br/>Стриминги-партнёры,<br/>открытые источники"]

    user --> web
    user --> mobile
    user --> tv
    admin --> web
    web -->|"REST/JSON, HTTPS"| gw
    mobile -->|"REST/JSON, HTTPS"| gw
    tv -->|"REST/JSON, HTTPS"| gw
    market -->|"REST, активация промокода"| gw
    payext -->|"webhook, статус платежа"| gw

    gw -->|REST| bffweb
    gw -->|REST| bffmob
    gw -->|REST| bfftv
    gw -->|"REST, вход и открытый ключ"| usr
    gw -->|"REST, доля трафика<br/>MOVIES_MIGRATION_PERCENT"| movies
    gw -->|"REST, остальной трафик<br/>немигрированных доменов"| mono
    gw -->|"REST, webhook"| pay

    bff -->|REST| movies
    bff -->|REST| activity
    bff -->|REST| video
    bff -->|REST| recs
    bff -->|REST| sub
    bff -->|REST| pay
    bff -->|REST| disc

    video -->|"REST, проверка доступа"| sub
    pay -->|"REST, цена со скидкой"| disc

    usr ==>|"регистрация, вход"| kafka
    activity ==>|"оценка, просмотр, избранное"| kafka
    pay ==>|"платёж прошёл / отклонён"| kafka
    sub ==>|"подписка оформлена / истекла"| kafka
    events ==>|"публикует и читает события"| kafka
    kafka ==>|"платёж прошёл"| sub
    kafka ==>|"оценка"| movies
    kafka ==>|"платёж прошёл"| disc
    kafka ==>|"вход, просмотр, оценка"| recs

    recs -->|"HTTPS, события и подборки"| recsys
    pay -->|"HTTPS"| payext
    disc -->|"HTTPS"| loyalty
    video -->|"HTTPS, ссылки на контент"| source

    gw -.-> redis
    recs -.-> redis
    usr -.-> pg
    movies -.-> pg
    activity -.-> pg
    video -.-> pg
    sub -.-> pg
    pay -.-> pg
    disc -.-> pg
    mono -.-> monodb

    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef store fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#ffffff
    classDef legacyStyle fill:#b3b3b3,stroke:#6b6b6b,color:#1c2230
    class user,admin person
    class web,mobile,tv,gw,bffweb,bffmob,bfftv,usr,movies,activity,video,recs,sub,pay,disc,events container
    class kafka,redis,pg store
    class mono,monodb legacyStyle
    class recsys,payext,loyalty,market,source ext
```

**Легенда связей:** сплошная стрелка — синхронный вызов REST, жирная — асинхронное сообщение через Kafka, пунктир — обращение к хранилищу.

## Домены

| Домен | Сервисы | Что входит |
|---|---|---|
| Пользователи | User Service | учётные записи, профили, аутентификация |
| Каталог | Movies Service | метаданные фильмов: жанры, актёры, описания, агрегированный рейтинг |
| Активность пользователя | Ratings & Favorites Service | оценки, избранное, история просмотров |
| Контент | Video Service | источники видео, выдача ссылок на просмотр |
| Рекомендации | Recommendations Adapter | обмен данными со сторонней рекомендательной системой |
| Подписки и платежи | Subscription Service, Payment Service | тарифы, подписки, платежи |
| Скидки и лояльность | Discount & Loyalty Service | скидки, промокоды, партнёрские программы, маркетплейсы |
| Интеграция | API Gateway, BFF, Events Service, Kafka | единая точка входа, адаптация под клиента, доменные события |

## Владение хранилищами

| Сервис | Назначение | Хранилище |
|---|---|---|
| API Gateway | единая точка входа, проверка токена, ограничение частоты, маршрутизация Strangler Fig | Redis — счётчики частоты запросов |
| Web / Mobile / Smart TV BFF | сборка ответа под конкретный клиент | нет, только вызовы сервисов |
| User Service | учётные записи, профили, выдача токенов | PostgreSQL |
| Movies Service | метаданные фильмов, агрегированный рейтинг | PostgreSQL |
| Ratings & Favorites Service | оценки, папки избранного, история просмотров | PostgreSQL |
| Video Service | источники видео, права на просмотр | PostgreSQL |
| Recommendations Adapter | подборки от рекомендательной системы | Redis — кэш подборок |
| Subscription Service | тарифы, подписки, сроки действия | PostgreSQL |
| Payment Service | платежи и их статусы | PostgreSQL |
| Discount & Loyalty Service | скидки, промокоды, бонусы | PostgreSQL |
| Events Service | MVP событийного обмена: публикует и обрабатывает события | Kafka |
| Монолит | ещё не вынесенная функциональность | PostgreSQL — общая БД монолита |

Общей базы у новых сервисов нет

## Ключевые взаимодействия

**Весь внешний трафик идёт через API Gateway.** Клиенты, маркетплейсы и платёжные системы с их уведомлениями обращаются к одному адресу. Шлюз проверяет токен, ограничивает частоту запросов и решает, куда отправить запрос. Внутренние сервисы наружу не публикуются.

**Переход с монолита — через Strangler Fig.** Шлюз знает, какие домены уже вынесены. Для домена в процессе переезда включается фиче-флаг `MIGRATION`, и в новый сервис уходит доля трафика `MIGRATION_PERCENT`, остальное — в монолит. Долю поднимают постепенно до 100 %, после чего код и таблицы домена из монолита удаляют. Первым выносится Movies Service. Клиенты адреса не меняют, поэтому переход проходит без простоя и незаметно для пользователей.

**Под каждый тип устройства — свой BFF.** Мобильным, ноутбукам и Smart TV нужны разные интерфейсы и разный объём данных. BFF собирает ответ из нескольких доменных сервисов и отдаёт ровно то, что нужно экрану: мобильному — облегчённую карточку фильма, телевизору — крупные подборки. Бизнес-логики в BFF нет, только агрегация и форма ответа, поэтому изменение под один клиент не задевает остальные.

**Синхронно — только то, где нужен ответ сразу.** Проверка доступа к видео по подписке и расчёт цены со скидкой при оплате делаются вызовом REST: без результата пользователь не может продолжить. Остальное межсервисное взаимодействие идёт через события.

**Последствия действий пользователя обрабатываются событиями.** Payment Service публикует факт платежа, по нему Subscription Service продлевает подписку, а Discount & Loyalty начисляет бонусы. Ratings & Favorites публикует оценку, Movies Service пересчитывает агрегированный рейтинг. Сервис-источник не знает о потребителях, и новый потребитель подключается без изменения источника.

**Рекомендации отделены адаптером.** Рекомендательная система сторонняя и уже работает асинхронно. Recommendations Adapter читает события о входе, просмотре и оценке, передаёт их во внешнюю систему, а подборки кэширует в Redis. Недоступность внешней системы не ломает процесс - BFF получает последнюю сохранённую подборку.

**Каждая внешняя интеграция принадлежит одному домену.** Платёжные системы — Payment Service, программы лояльности — Discount & Loyalty, источники контента — Video Service, рекомендательная система — Recommendations Adapter. Смена провайдера затрагивает один сервис. Уведомления платёжных систем и запросы маркетплейсов приходят через шлюз, как и остальной внешний трафик.

**Events Service — MVP событийного обмена.** Он проверяет, как Kafka встраивается в архитектуру: по вызову API публикует события User, Movie и Payment в топики `user-events`, `movie-events`, `payment-events` и сам же их читает, записывая обработку в лог. После проверки гипотезы публикация переходит в доменные сервисы, как показано на диаграмме.

**Токены выдаёт User Service, проверяет шлюз.** Подпись асимметричная: шлюз получает открытый ключ у User Service и кэширует его, поэтому на каждый запрос в User Service ходить не нужно. Сервисы получают от шлюза уже проверенный контекст пользователя.

**Всё разворачивается в Kubernetes.** Каждый сервис — отдельный Deployment со своим числом реплик. Установка идёт Helm-чартом, сборка образов и тесты — через CI/CD в GitHub Actions. Istio добавляет Circuit Breaker между сервисами и канареечные выкладки.
