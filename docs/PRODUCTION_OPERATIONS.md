# Production конфигурация и операции — Етап 7

Подготвено на 2026-10-22 в `production-auth-migrations`, след Етап 6 (`0cdc3b1`).
Това е реализация и проверка на конфигурацията и инструментите, **не deployment**.
Не е променяна реалната база, не са активирани timers, proxy или backup акаунти.
Собственикът одобри commit/push на Етап 7 и започването на Етап 8 на 2026-10-22.
Етап 8 и проверките върху избрания hosting остават задължителни.

## 1. Поддържана начална топология

Един Linux host, един backend, MySQL 8.4 LTS и същия публичен origin за React и
`/api/`. NGINX прекратява HTTPS и препраща към `127.0.0.1:8080`. Метриките са на
отделен `127.0.0.1:8081`, никога през публичния proxy. Базата може да е локална
или частна remote услуга с `sslMode=VERIFY_IDENTITY` и доверена CA.

Това е provider-independent отправна точка, не задължителен cloud продукт.
Не са нужни Kubernetes, microservices или Redis за тази конфигурация. При
containers, външен load balancer или няколко backend instances трябва нова
проверка на proxy trust, общите rate limits, cache invalidation и jobs locking.
Не променяйте само `server.address` на `0.0.0.0` — startup проверката ще откаже.

## 2. Fail-closed startup

`ProductionSettingsValidator` се зарежда чрез `META-INF/spring.factories` след
config data и **преди създаване на beans / connection pool / Flyway**. Само при
активен `prod` проверява ефективните настройки, включително overrides:

- Explicit DB URL, отделен runtime username (не `root/admin/mysql`) и secrets;
  credentials в JDBC URL и произволни connection options са забранени.
- Base64 JWT key поне 32 bytes, без очевиден повтарящ се тестов ключ. Това не
  измерва истинска entropy: ключът трябва да се генерира с CSPRNG, не на ръка.
- Explicit issuer/audience и HTTPS frontend origin; CORS трябва да е точно той.
  Няма localhost fallback в Production. Не използвайте wildcard или списък.
- `Secure`, `HttpOnly` (в controller), host-only refresh cookie, `SameSite=Strict`,
  path `/api/v1/auth`. Access token остава 30 min; remembered session — 60 дни.
- Loopback listeners, отделен management port, native Tomcat forwarding и само
  точния loopback peer като trusted proxy. Няма trust на всички private ranges.
- Hibernate `validate`, Flyway validation, без automatic baseline/clean;
  Swagger/JMX, SQL/request-detail logging и debug/trace са изключени.

Съобщението при грешка съдържа **името на настройката, не стойността**. Промяна
на Java system properties след тази проверка не е поддържан deployment подход.
Guard-ът не може да докаже DB grants, firewall, DNS ownership или backup policy:
те се проверяват оперативно, а не чрез произволен boolean „готово“.

Локалният default profile остава съвместим с `LOCAL_SETUP.md`; `npm.cmd start`
и обичайното локално стартиране не стават автоматично Production режим.

## 3. Secrets и права

Шаблон: `ops/runtime.env.example`. Реалният файл е извън checkout, например
`/etc/investment/runtime.env`, root-owned mode 600. Не го изпращайте в чат, CI
artifacts, screenshots или shell history. Backend не чете `.env` автоматично;
systemd подава EnvironmentFile. За secret manager използвайте еквивалентно
инжектиране без промяна на публичния bundle. Нито едно `VITE_*` поле не е тайно.

Разделете три DB accounts с различни пароли и ограничен host:

| Account | Необходими права / употреба |
| --- | --- |
| runtime `investment_app` | SELECT на schema, INSERT/UPDATE/DELETE само на 16-те application tables; без DDL/GRANT и без DML на `flyway_schema_history` |
| migration | DDL/DML само на конкретната application schema; достъпен само по време на maintenance, никога в runtime EnvironmentFile |
| backup | SELECT и нужните dump metadata права върху тази schema; без application writes/DDL; без `PROCESS` чрез `--no-tablespaces` |

Application tables: `_user`, `_user_roles`, `_user_seq`, `role`, `role_seq`,
`token`, `token_seq`, `cs_items`, `item`, `auth_session`, `refresh_token`,
`price_history`, `market_price_snapshot`, `market_price_write_lock`,
`activity_event`, `support_ticket`. Списъкът трябва да се обновява при migration,
който добавя таблица. Backup включва и Flyway history; текущата schema няма
stored routines/events. Ако се добавят, прегледайте dump flags и grants, вместо
да приемате, че са архивирани. Никога не раздавайте `ALL ON *.*` на runtime.

`ADMIN_EMAIL` трябва да е празно в `prod`, за да няма постоянно автоматично
повишаване на акаунт. Първоначален ADMIN се пренася от проверената база или се
назначава еднократно от оператор след проверена регистрация: в transaction се
заключва точният enabled user, намира се ролята ADMIN по име и се добавя само
липсващата membership. Записват се operator/change reference и избраният user ID.
Не избирайте hardcoded role IDs и не автоматизирайте повишаване по свободен email.
След това управлението на SUPPORT става през съществуващия Support Panel.

## 4. Schema changes преди стартиране

Production Flyway strategy прави `validate()` и отказва при липсваща или pending
schema, **без `migrate()`**. Runtime account не се нуждае от DDL. Това е промяна
само за `prod`; default local profile продължава да прилага pending migrations.

Създават се два Java 21 артефакта от един и същ проверен commit:

```bash
cd investment-tracker
./gradlew --no-daemon test mysqlTest bootJar migrationBootJar
```

`mysqlTest` изисква изрично изолиран `MYSQL_TEST_URL`, не реалната база. При
release пакетирайте application JAR като `backend.jar`, а classifier
`-migrations.jar` като `migrations.jar`. Maintenance инструментът е независим:

```bash
java -jar migrations.jar migrate investment
```

Преди командата операторът подава `MIGRATION_DB_URL`, `MIGRATION_DB_USERNAME`,
`MIGRATION_DB_PASSWORD` чрез отделен private process environment. Не копирайте
credentials в аргументи. Database name трябва да съвпада изрично. Remote DB
изисква `sslMode=VERIFY_IDENTITY` и подходяща CA и за migration credential.
Този JAR не стартира Spring, HTTP, SMTP, catalogue init или price jobs.
За adoption/upgrade използвайте [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).
Изграждане на JAR не е разрешение за изпълнение върху реална база.

## 5. Frontend, HTTPS и proxy

Deployment build се прави с explicit публичен origin без `/api` и trailing slash:

```bash
VITE_API_URL=https://your-real-domain.example npm run build:production
```

Командата отказва HTTP, липсващ URL, URL credentials/query/path; няма legacy или
localhost fallback. Обикновеното `npm run build` остава за local/CI smoke.
Изходът е `react/investment-tracker/build`; Vite dev/preview НЕ е Production server.

`ops/nginx/investment.conf.example` и двата snippets
`investment-api-proxy.conf` / `investment-security-headers.conf` са шаблони.
Копирайте snippets в `/etc/nginx/snippets/`; заменете примерния домейн както в
server configuration, така и в фиксирания proxy `Host`. API snippet се включва
и за legacy Portfolio routes извън `/api/`; те не трябва да попадат в SPA fallback.
След всяка промяна проверете `nginx -t` преди reload.

1. Изберете реален домейн, проверете DNS и инсталирайте валиден сертификат с
   автоматично renewal и alert преди изтичане. Не активирайте `.invalid` примерите.
2. NGINX презаписва `X-Forwarded-For` с socket peer и `X-Forwarded-Proto=https`;
   не използва `$proxy_add_x_forwarded_for`. `Forwarded` и host/port overrides
   се изчистват. Поддържа се точно един same-host trusted proxy.
3. Java слуша loopback; firewall допуска публично само 80/443, SSH с ограничен
   административен достъп. 8080/8081/3306 не са публични. Не публикувайте metrics
   чрез произволен location. Host/SNI извън конфигурацията се отказват.
4. CSP, HSTS, nosniff, frame denial, referrer/permissions policy са в shared
   snippet, включен и в API location, заради NGINX header inheritance. API е
   `no-store`, fingerprinted assets се кешират, HTML се revalidate-ва.
5. Има request-body limit, bounded connection timeouts и изключени proxy retries
   за writes. Inventory може да отнеме време; timeout не трябва да повтаря POST.

CSP допуска self scripts и конкретни Steam image hosts, не `unsafe-eval` или
wildcard scripts. Inline styles са нужни за React Bootstrap и tooltip позиции.
Проверете реалните catalogue/Steam avatars в staging; добавяйте само доказано
нужен image host, не премахвайте CSP. Бъдещи рекламни банери изискват отделен review.

## 6. Health, metrics и logging

Само `prod` expose-ва read-only `/actuator/health` и `/actuator/prometheus` на
локалния management port. Main port отказва actuator и за нормален user/admin.
`env`, `configprops`, `heapdump`, shutdown и mappings не се expose-ват. Само
локални GET/HEAD към конкретните health/probe/metrics paths са позволени.
Отговорът на health е само `{"status":"UP"}`, без schema/credentials/details.

- Liveness проверява процеса, не Steam/SMTP/MySQL. Provider outage не трябва да
  предизвиква restart loop.
- Readiness включва DB. Наблюдавайте го отделно през private probe на всеки 30s;
  503 означава временно неприемане на трафик, не разрешение за schema repair.
- Prometheus: JVM, HTTP timings/status, pool metrics и fixed-name job/price-age
  gauges. Scrape не тегли цени, не изпраща email и не изпълнява DB business queries.
  Не добавяйте user/email/item/search/query labels с неограничена cardinality.
- `ops/monitoring/` съдържа scrape/alert правила за unavailable backend, failed
  jobs, стар feed/FX, DB waiters и server errors. Трябва да се настроят реални
  получатели/Alertmanager и да се докаже получено тестово известие. Нищо не е
  активирано тук; gauges отразяват текущия процес, не вечна job history.

Production console използва ECS JSON. Всеки request получава server-generated
`X-Request-ID`, свързан с MDC; входящ произволен ID не се отразява. Request bodies,
cookies, Authorization и SQL bind values не трябва да се логват. Общите error
handlers логват exception type, не potentially sensitive exception messages.

UI Audit Log остава последните **100** записи, както е избрано от собственика.
Важни security/support/admin/system events се логват и след successful commit,
без reason/details/email/IP. Rollback не създава успешен audit log. USER_ACTION
не се дублира в журнала. Числовите user IDs все пак са ограничени лични данни.

Задайте restricted journald/log storage (примерна политика 30 дни и общ cap
1 GB, според нуждите), logrotate за NGINX и защитено off-host събиране на
важните security events. Това не е tamper-proof SIEM и DB ring от 100 не е
дългосрочен audit archive. Проверете ограничените права и изтриването по политика.

## 7. Encrypted automated off-host backup

`ops/backup/backup-mysql.sh` и systemd template/timers са **неактивирани**.
Нужни са Linux, Bash, `flock`, MySQL 8.4 client и поддържан restic с
`--stdin-from-command` (проверете наличието с `restic backup --help`).

1. Създайте отделен backup OS/DB account и отделно off-host S3/HTTPS/SFTP
   хранилище. Инициализирайте restic repository ръчно само след проверка на target.
2. Запазете repository password и достъпа за recovery отделно от host-а и от
   application secrets, с private offline/password-manager copy. Ако загубите
   паролата, encrypted backup не може да бъде възстановен.
3. Private `backup.env`, `mysql.cnf`, restic-password са извън Git, owned by
   backup user, mode 400/600. State dir е owned mode 700; credential symlinks и
   директен filesystem repository се отказват. Самият remote адрес не доказва
   физически отделен host — операторът проверява това. Не използвайте `bash -x`.
4. Скриптът извиква `restic backup --stdin-from-command -- mysqldump ...`.
   SQL потокът се шифрира от restic преди remote storage; няма plaintext dump
   на диска. При неуспешен mysqldump restic отказва snapshot; wrapper не записва
   success heartbeat при грешка. Не използвайте наивно `mysqldump | restic --stdin`.
5. Едва след пробно backup/restore/check инсталирайте timers: daily backup в
   02:00 UTC + jitter, weekly integrity check 5% data. Lock предотвратява
   едновременно backup/check. Cache/state са private и writable за backup user.
6. Наблюдавайте exit status и `last-backup-success-utc`; alert при възраст над
   26h или липса след първия прозорец. За weekly check alert при над 8 дни.
   Ежедневната политика дава целеви RPO до 24h, не нулева загуба на данни.

Retention цел: 7 daily / 4 weekly / 6 monthly, според storage budget и privacy
policy. Не е включено автоматично `forget/prune` или repository initialization.
Преди enabling deletion проверете с restic dry-run точните host/tag/path групи
и независимата recovery copy; никога `forget latest` или glob върху общ repo.
При възможност използвайте отделна append-only credential за backup и отделна
admin credential за retention, плюс object-lock/offline copy. Repository с write
достъп сам по себе си не предпазва от злонамерено изтриване с откраднат account.

Поне месечно и преди schema release: изберете конкретен successful snapshot ID,
възстановете в **отделен** изолиран MySQL със същата major версия; сравнете
schema/Flyway history, броеве/relations и ключови user workflows. `restic check`
или checksum не доказва логически коректен restore. Не стартирайте app срещу
възстановена база с истински SMTP/provider достъп. Примерен RTO target: 4h —
трябва да се измери, не е сертифициран от тези source-code проверки.

Dump съдържа сесии и password hashes. При restore на стара база обезсилете всички
възстановени refresh/access sessions **преди** отваряне на трафик; иначе стар
backup може да възстанови вече revoked session. Това е отделна, изрично одобрена
maintenance операция върху клона, не автоматична промяна в реалната база.

## 8. Release, shutdown и rollback

`ops/systemd/investment-tracker.service` е пример за non-root service, ограничен
heap, read-only filesystem, SIGTERM и достатъчен stop timeout. Не се инсталира
автоматично. Cron/async workers изчакват bounded completion; readiness и
graceful shutdown са част от release проверката. Не използвайте kill -9 за нормален stop.
Изберете и документирайте timezone преди първия import: съществуващи naive
`LocalDateTime` expiry стойности не трябва тихо да се интерпретират в друга зона.
Нов UTC host и стара локална база изискват изрична репетиция на auth expiry.

Последователност за release от **clean approved commit**:

1. Проверете green CI за точно този commit, tests, dependency audit и checklist.
2. Изградете backend, migration JAR и HTTPS frontend веднъж; запишете commit,
   runtime versions и SHA-256 на всички release files в manifest. Не build-вайте
   на production host и не използвайте само mutable `latest` име.
3. Копирайте в нова immutable versioned release directory, без credentials,
   `.env`, source backups или developer DB. Запазете предишния release/manifest.
4. Репетирайте същите artifacts на staging clone с безопасни accounts/outbound
   stubs. Проверете CSP/images, cookies/refresh/logout, headers, readiness и jobs.
5. Maintenance window: затворете трафика и спрете всички writers/jobs. Направете
   и проверете backup; изпълнете само необходимите migrations с maintenance user.
6. Стартирайте новия backend с restricted runtime account; проверете readiness,
   schema validation и smoke workflows. Активирайте frontend/proxy към същия
   release и отворете трафика. Секретите не са в release directory.
7. Наблюдавайте errors, latency, DB pool, свежестта на цените и backup heartbeat.

Rollback на binary/frontend е допустим само ако предишният backend е съвместим
с текущата schema. Старият артефакт трябва предварително да е тестван с нея.
Git revert или смяна на `current` symlink **не връща MySQL DDL**. При несъвместима
schema/частична migration: спрете трафика, предпочетете reviewed forward fix или
възстановете verified backup на отделна инстанция с отчетена загуба след RPO.
Никога blind Flyway `repair/clean`, automatic downgrade или overwritten V1–V3.

## 9. Проверки и граници (2026-10-22)

| Проверка | Резултат |
| --- | --- |
| Backend `test bootJar migrationBootJar` | 521 tests: 520 passed, 1 pre-existing manual measurement skipped, 0 failures/errors; двата JAR се изграждат |
| Disposable MySQL 8.4.11 | 27 passed, включително restricted Production startup, отказ при pending V2/V3, login/logout cookie flags, DB-down readiness и жив liveness |
| Disposable MySQL 8.0.37 | Същите 27 passed; системният MySQL service не е променян |
| Frontend `npm.cmd run verify` | Lint, 217 tests, build и 17 HTTP smoke checks passed; съществуващият react-datepicker source-map warning остава |
| Deployment build | HTTPS test origin accepted; HTTP origin refused; реални конфигурационни файлове не са променяни |
| Runtime dependency audit | 136 Maven components, 0 OSV advisory matches към 2026-10-22; това не е гаранция за липса на уязвимости |
| Maintenance JAR entry point | Отказва липсващ action преди environment/DB access; не стартира web application |
| Operations tests на Windows | 3 passed; 4 Linux ownership/permissions/flock cases skipped и добавени към Linux CI |
| Original database / real SMTP / providers | Не са използвани от тези проверки; няма нова migration в този етап |

Всички временни MySQL/HTTP listeners от репетициите са спрени. Тестовите данни и
отчетите остават в ignored build directories, не са release съдържание.

Изолираният Production test стартира реалните HTTP listeners и MySQL с runtime
user без DDL и без DML на Flyway history, outbound HTTP/mail са mock-нати.
Unit tests проверяват early failure, migration refusal, metrics/cache-only reads,
request IDs и audit-after-commit. Backup template tests не използват real remote
repository; Linux permissions/flock cases са маркирани за Linux CI и се skip-ват
на Windows. GitLab `mysql` alias няма verified TLS, затова локалната Production
HTTP rehearsal се skip-ва там, вместо да отслабва TLS guard-а. GitHub loopback
service и локалният MySQL harness я изпълняват.

Реален hosting firewall/TLS/CSP/browser, off-host restore, timers, alert delivery,
recovery time и remote CI за този commit са **непроверени до съответната репетиция**.
Етап 7 не превръща автоматично локалните тестове в публична Production сертификация.

## Официални източници

- [Spring Boot endpoints и security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Отделен management port/address](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
- [Tomcat RemoteIpValve trust](https://tomcat.apache.org/tomcat-11.0-doc/api/org/apache/catalina/valves/RemoteIpValve.html)
- [NGINX proxy headers](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Restic command backup / incomplete stream safety](https://restic.readthedocs.io/en/stable/040_backup.html)
