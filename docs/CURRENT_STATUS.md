# Текущ статус и навигация

Актуализирано: 2026-10-22. Работен клон: `production-auth-migrations`.
Този файл е отправната точка за текущото състояние. Номерираните stage/refactor
документи са записи на конкретни проверки, не обещание за действащ Production.

## Проверена публикувана основа

- Етап 7: `d69a500` — production конфигурация и operations инструменти.
- Етап 8: `cbecc3080919f70c11c954275e380cb06fb07454` — browser acceptance,
  proxy/history/responsive поправки. Публикуван в GitLab и GitHub.
- GitHub Actions за **точно `cbecc30`** е проверен: frontend, backend с MySQL,
  Linux operations и desktop/mobile browser acceptance са успешни.
  [Проверено изпълнение](https://github.com/Kalin-Yakimov/investment-tracker/actions/runs/39774862670).
- Текущ GitLab pipeline: **непроверено**. Исторически зелен pipeline не доказва
  резултата за нов commit.
- Допълнението след Етап 8 е документация и explicit-development Swagger. Неговият
  нов commit трябва да има собствен успешен CI; горният резултат не го покрива.

Локалното допълнение е проверено на 2026-10-22: 529 backend tests passed и 1
manual test skipped, 22 browser tests, 217 frontend tests и 17 smoke checks.
Всичките 27 disposable MySQL 8.4.11 проверки също са успешни; реалната база
не е използвана или променяна от тези тестове.
Описанието и development security границите са в [API_DOCUMENTATION.md](API_DOCUMENTATION.md).

Не е извършен merge или deployment. Няма избран и приет публичен release.

## Къде е необходимата информация

| Задача | Актуален документ |
| --- | --- |
| Нов локален компютър, environment variables, обичайно стартиране | [LOCAL_SETUP.md](../LOCAL_SETUP.md) |
| Swagger/OpenAPI и безопасно тестване на API | [API_DOCUMENTATION.md](API_DOCUMENTATION.md) |
| Production deployment, grants/secrets, HTTPS proxy, maintenance JAR, backup, release/rollback | [PRODUCTION_OPERATIONS.md](PRODUCTION_OPERATIONS.md) |
| Fresh/upgrade/adoption migration процедура | [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md) |
| Alerts, диагностика, инциденти и recovery ограничения | [INCIDENT_RUNBOOK.md](INCIDENT_RUNBOOK.md) |
| Какво означават цените, графиките, FX и Portfolio totals | [PRICE_DATA_SEMANTICS.md](PRICE_DATA_SEMANTICS.md) |
| Последни локални acceptance резултати и граници | [FINAL_ACCEPTANCE_STAGE8.md](FINAL_ACCEPTANCE_STAGE8.md) |
| Условия за публичен release | [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md) |
| История на изпълнението на плана | [PRODUCTION_PLAN.md](PRODUCTION_PLAN.md) |

Не дублирайте процедури в нови stage файлове. При промяна обновявайте съответния
оперативен документ; старите измервания запазват дата/commit и ограничения.

## Проверен runtime baseline, не списък с „последни версии“

| Компонент | Repository baseline / проверка |
| --- | --- |
| Java | JDK 21; използвайте поддържан vendor build с актуални security patches |
| Spring Boot / Gradle | 4.1.1 / checksum-verified wrapper 8.14.6 |
| Node.js | CI 24.20.0; нова инсталация — поддържаната 24 LTS линия, минимум 24.15 |
| React / Vite / Vitest | 18.3.1 / 8.3.4 / 5.0.3 |
| Playwright | 1.64.1; browser binaries/container трябва да съответстват на locked версията |
| MySQL | 8.4.11 — избран и тестван target; съществуващият 8.0.37 е само compatibility checkpoint |

Това не твърди, че конкретният patch ще е актуален в деня на deployment.
Преди release повторете dependency/runtime advisory проверката и изолирания
MySQL upgrade тест за избраната поддържана версия. Не обновявайте system MySQL
или schema без отделен maintenance план. Подробности: [RUNTIME_UPGRADE.md](RUNTIME_UPGRADE.md).

## Какво остава преди публичен достъп

Кодът и operations шаблоните са подготвени, но не са активирана инфраструктура.
Остават hosting-specific DNS/TLS/firewall/CSP/cookies, restricted DB grants,
encrypted off-host backup с действителен restore, alerts, graceful restart и
rollback rehearsal. Checklist се подписва за точен release commit и среда, не
само защото стар local test е успешен. Не е нужно тези услуги да се публикуват сега.
