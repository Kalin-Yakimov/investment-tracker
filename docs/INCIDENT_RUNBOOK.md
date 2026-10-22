# Monitoring и реакция при инцидент

Приложимо към single-host topology от [PRODUCTION_OPERATIONS.md](PRODUCTION_OPERATIONS.md).
Това е процедура за бъдещия оператор, не доказателство за активирани monitoring,
backup или alert recipients. Преди release попълнете отговорник, канал за сигнал,
backup owner, recovery credentials location и избран release commit — без самите secrets.

## 1. Първи действия

Запишете UTC начало, симптом, засегнат workflow, release commit, последна промяна
и request ID, ако е наличен. Не събирайте credentials, Authorization/cookies,
request bodies, SQL dumps или customer messages в публичен ticket.

На самия Linux host следните проверки са read-only:

```bash
systemctl status investment-tracker --no-pager
curl --fail --max-time 5 http://127.0.0.1:8081/actuator/health/liveness
curl --fail --max-time 5 http://127.0.0.1:8081/actuator/health/readiness
journalctl -u investment-tracker --since '-15 min' --no-pager
```

Порт 8081 е private. Не го отваряйте към Internet за диагностика и не публикувайте
целия journal. Няма публичен `/actuator/env` или heapdump diagnostic endpoint.

## 2. Решение според симптома

| Сигнал / симптом | Проверка и безопасно следващо действие |
| --- | --- |
| `InvestmentBackendUnavailable` | Проверете service/listener, scrape target и host resources. Scrape failure не доказва причината. Не правете безкраен restart loop |
| Liveness UP, readiness DOWN | Проверете MySQL availability, disk и pool; readiness включва DB. Не изпълнявайте schema repair/clean и не увеличавайте pool произволно |
| `InvestmentDatabasePoolBusy` | Сравнете latency/traffic и DB connections/slow queries; ограничете входящото натоварване с операторско решение. Първо открийте забавената работа |
| `InvestmentServerErrors` | Съпоставете request ID, timestamp и последния release; проверете дали грешките са общи или само в един provider-dependent workflow |
| `InvestmentBackgroundJobFailed` | Вижте job/last completed и минималните logs. Еднократен manual refresh само след проверка на cooldown/provider health, не многократни натискания |
| `InvestmentPricePublicationUnavailable` / `InvestmentPricePublicationOld` | Проверете source publication metadata и последния успешен check; няма гаранция за ежедневна публикация. Запазете старите стойности с freshness, не ги заменяйте с нула/измислени дати |
| `InvestmentFxOld` | Проверете FX source и `fxRateAsOf`; fallback не е нов курс. Не променяйте датата, за да изглежда актуален |
| Много 401/403/429 | Проверете session expiry/revocation, permissions и request budgets. Не изключвайте auth, CORS, cookie headers или limits, за да „тръгне“ |
| Неуспешен backup или липсващ heartbeat | Проверете backup service/timer, remote access, storage и key availability. Стар successful snapshot не е текущ backup; не изтривайте единственото копие |

Шаблонните application alert правила са в `ops/monitoring/investment-alerts.yml`.
Backup heartbeat е отделен host-level monitor: daily success до 26h, weekly check
до 8 дни. Преди пускане трябва да има проверено получаване на тестов alert, не
само наличен YAML. При известен startup grace период не скривайте истинска outage.

## 3. Подозрение за откраднат token/secret

За application user: проверен ADMIN използва предвиденото revoke/lock управление,
а собственикът сменя паролата по нормалния flow. Revoke обезсилва сесиите; системата
не разпознава автоматично всяка кражба на token. Не разчитайте само на UI ring log.

При JWT signing key, DB или SMTP credential: ограничете достъпа/трафика според
обхвата, запазете безопасните evidence logs и сменете конкретния credential чрез
неговия доставчик. Key rotation и revocation се планират заедно; не правете
обещание, че rotation на един secret поправя всички други компрометирани права.
Не препращайте ключа в ticket и не го записвайте в Git или command arguments.

## 4. Release rollback и restore

Първо проверете последната промяна и schema compatibility. Използвайте
[release/rollback процедурата](PRODUCTION_OPERATIONS.md#8-release-shutdown-и-rollback)
и [migration recovery правилата](DATABASE_MIGRATIONS.md#release-and-recovery-rules).
Запазете предишните artifacts и manifest. Git revert не отменя MySQL DDL.

При restore спрете трафика и всички writers/jobs, възстановете конкретен проверен
snapshot в отделен MySQL и сравнете schema/data/workflows. Възстановени auth сесии
се обезсилват преди отваряне; иначе backup може да върне вече revoked сесия.
Запишете действителните загубени данни/RPO и време/RTO. Не правете restore върху
единствената работеща база без одобрен recovery план и запазено независимо копие.

## 5. Приключване

Потвърдете readiness, основния засегнат workflow, error rate, source freshness и
backup age. Запишете причина, действия, release/schema версии, резултат и какъв
regression test/alert предотвратява повторение. Операторът потвърждава recovery;
единичен green health response не доказва, че потребителският проблем е решен.
