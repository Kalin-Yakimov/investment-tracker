# Финално локално приемане — Етап 8

Дата: 2026-10-22. Клон: `production-auth-migrations`.
Базов публикуван commit: `d69a50018a28cd5e8906f124273233cfee044b95` (Етап 7),
качен в GitLab и GitHub по одобрение на собственика. Този отчет описва
**локално проверената кандидат-версия преди нейното публикуване**, не deployment.
Публикуван като `cbecc30` в GitLab/GitHub на 2026-10-22. GitHub Actions е
потвърден успешен за тази ревизия; одобрението не включва merge или deployment.
Таблицата по-долу запазва оригиналните Stage 8 резултати; следващи промени и
проверки са отделни checkpoints в [CURRENT_STATUS.md](CURRENT_STATUS.md).

## 1. Какво е добавено

Playwright изпълнява реалния изграден React bundle срещу реални Spring HTTP
controllers, authentication filters, services и repositories. Не се mock-ват
frontend authentication или успешните бизнес заявки. Backend fixture е само в
`src/test/java/.../acceptance/AcceptanceTestServer.java`; не е production profile,
активационен backdoor или част от application JAR.

Изолацията е задължителна:

- нова H2 in-memory база за всяко стартиране, синтетични USER/OTHER/SUPPORT/ADMIN;
- само `127.0.0.1:18080` за backend и `127.0.0.1:14173` за built frontend;
- зает порт се отказва: Playwright не използва вече работещ сървър;
- фиксирани test-only credentials; реалните environment DB/mail/JWT стойности
  са заместени изрично преди Spring startup;
- mail, Steam inventory, catalogue synchronization и price feed са заменени с
  fixtures; останалият outbound RestTemplate отказва реални HTTP заявки;
- browser external requests са блокирани; няма реален Gmail/Steam account;
- tracing е изключен, за да не съхранява токени/пароли; failure screenshots са
  само от синтетичната сесия. CI пази отчетите до 7 дни.

H2 browser проверките **не заместват** MySQL migrations/concurrency тестовете.
HTTP fixture cookie настройките са само за loopback теста, не са production
настройки. Това не е HTTPS, CSP или hosting firewall проверка.

## 2. Сценарии

10 сценария в два Chromium проекта (desktop и Pixel 7 mobile), общо 20:

1. Anonymous redirect, вход чрез клавиатура, възстановяване след reload и липса
   на Support Panel за обикновен потребител.
2. Portfolio catalogue selection, добавяне, редактиране, transaction history,
   navigation persistence и отказ за чужд запис.
3. API ownership и USER/SUPPORT/ADMIN граници: чужди read/delete — 404;
   административни операции без права — 403.
4. Две независими access сесии: след Sign out и двете дават 401.
5. PriceTracker pagination, price chart dialog, Escape, keyboard catalogue
   selection и добавяне на конкретния предмет.
6. Временна 503 грешка: Retry, без подвеждащо празна таблица, успешно повторение.
7. Inventory резултатът остава при navigation и се изчиства при Sign out.
8. Contact request се записва и се вижда от ADMIN в Support Panel.
9. Регистрация, генериран activation code от test mailbox, активация и реален вход.
10. Достъпна navigation и profile menu без излизане извън viewport при
    ширини 320, 615, 900 и 1280 px.

Тестовете проверяват и липса на unhandled browser `pageerror`. Това е целева
keyboard/responsive регресия, не пълна WCAG или cross-browser сертификация.

## 3. Реални проблеми, намерени при приемането

| Проблем | Поправка / регресионна защита |
| --- | --- |
| Production proxy препращаше само `/api/`, но Portfolio използва и `/item`, `/get-user-items`, `/portfolio-summary`, `/cs-items/`, `/items/search`; SPA fallback би върнал HTML вместо API JSON | Explicit proxy locations и един общ `investment-api-proxy.conf`. Integration test сравнява реалните Spring controller mappings с proxy locations; Python contract test проверява headers/routes |
| History сравняваше lower-case query с непроменено име, разчитайки на MySQL case-insensitive collation; при case-sensitive DB резултатът беше празен | `LOWER(i.itemName)` и `Locale.ROOT` normalization; ownership и portfolio-only filters са запазени. API и browser тест проверяват history след add/edit |
| При тесен navbar profile menu излизаше извън левия край | Под 992 px account actions остават вдясно, navigation се разполага на отделен ред. Desktop layout е запазен; проверяват се bounds при четири ширини |

Няма нова migration, промяна в ценовия източник, сроковете на сесиите, реалната
база или личните акаунти. Не се добавят измислени исторически цени.

## 4. Възпроизводимо изпълнение

Нужни са Java 21, Node 24.15+ (проверено с 24.20.0), locked npm dependencies и
официалният Playwright Chromium. От `react/investment-tracker/` на Windows:

```powershell
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

На Linux CI браузърът се инсталира с `npx playwright install --with-deps chromium`
или се използва съвпадащият Playwright container tag. Тестовете сами build-ват
frontend, стартират и спират двата изолирани сървъра. Не стартирайте обичайното
приложение или лична база за тази команда. Първото изпълнение може да свали
Gradle/dependencies; това не е provider заявка от приложението.

Други проверки от repository root:

```powershell
node --test scripts/tests/verification-manifest.test.mjs
.\scripts\Test-MySql.ps1 -MySqlBin 'PATH-TO-ISOLATED-MYSQL-BIN' -Port 13308
```

MySQL helper създава отделна GUID data directory, проверява самоличността на
сървъра и спира само него. Никога не подавайте реалната application DB като
`MYSQL_TEST_URL`. Backend/frontend стандартните команди са в README.

След final tests/builds, от repository root:

```powershell
node scripts/verification-manifest.mjs
```

Ignored `investment-tracker/build/reports/acceptance/verification-manifest.json`
съдържа base commit, dirty flag, SHA-256 fingerprint на Git-visible source files
и хешове/размери на backend/migration JAR и frontend artifacts — не файловото
съдържание. Отказва escaping paths и symlinks. Това е локална verification
разписка, **не trusted build attestation, подписан release или production approval**.
При одобрен clean release трябва да се изпълнят отново приемането и CI върху
точния commit; artifact hashes се записват в отделен release manifest.

## 5. Резултати и ограничения

Резултатите от окончателното изпълнение са описани в таблицата по-долу.
Неотметнатите hosting условия в [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md)
остават независими gates; успешните тестове не ги отбелязват автоматично.

| Проверка | Локален резултат към 2026-10-22 |
| --- | --- |
| Browser E2E | 20 passed (10 desktop + 10 mobile), без retries |
| Frontend verify | 217 unit/component tests, lint, build, 17 built HTTP smoke checks passed |
| Manifest safety | 3 Node tests passed |
| Backend | 522 tests: 521 passed, 1 pre-existing manual test skipped; 0 failures/errors; application и migration JAR builds passed |
| Disposable MySQL 8.4.11 | 27 passed, 0 failures/errors/skips; отделният сървър е спрян |
| Disposable MySQL 8.0.37 | 27 passed, 0 failures/errors/skips; отделният сървър е спрян, системният service не е променян |
| Runtime dependency audit | 136 Maven components, 0 OSV advisory matches; npm audit — 0 reported vulnerabilities (включително test dependencies) |
| Operations на Windows | 3 passed, 4 Linux ownership/permissions/flock tests skipped; последните са за Linux CI, не са local passes |
| Workflow validation | actionlint 1.7.12 passed за текущия GitHub workflow; GitLab remote execution остава непроверено |
| Artifact isolation | И двата JAR са инспектирани: няма acceptance fixture, H2 или Mockito |
| Визуална проверка | Синтетичен ADMIN, меню при 615/320 px; bounds са вътре в viewport. Screenshot остава в ignored `build/reports/acceptance/profile-menu-615.png` |

Текущият Git-visible source премина ограничена pattern проверка за GitLab/GitHub
tokens и private keys без съвпадения. Това не е пълен secret audit и не
сертифицира Git history или ignored local файлове. Предишни secrets в запазената
история трябва да се преоценят преди евентуално public repository exposure,
както е описано в [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md). Резултатът от dependency
audit също е моментна проверка, не гаранция за липса на всички уязвимости.

GitHub Actions за **публикувания Етап 7**, commit `d69a500`, е проверен като
успешен за frontend, backend и Linux operations, включително седемте operations
теста: [run 39768969950](https://github.com/Kalin-Yakimov/investment-tracker/actions/runs/39768969950).
След публикацията е проверен и **Етап 8, commit `cbecc30`**: frontend, backend,
Linux operations и browser acceptance са успешни в
[run 39774862670](https://github.com/Kalin-Yakimov/investment-tracker/actions/runs/39774862670).
Актуалният GitLab pipeline не е проверен; не е отчетен като успешен. Този резултат
не се пренася автоматично върху последващи commits.

Остават съществуващ react-datepicker source-map warning и Java/Mockito dynamic
agent warning в тестовете; те не са неуспешни production builds. Един по-стар
manual measurement backend test е изрично skipped, не counted as passed.

## 6. Решение за готовност

Локалното software acceptance е отделно от оперативното public-release acceptance.
Преди публичен достъп остават конкретните проверки на избрания hosting:

- реални DNS/TLS, proxy routing, headers/CSP/images, cookies и недостъпни отвън
  Java/management/DB ports;
- restricted runtime/migration/backup accounts, private secrets и проверен
  schema upgrade върху restored clone;
- encrypted off-host backup, успешен restore, измерени RPO/RTO, timers и alerts;
- реален project SMTP, source freshness, restart/SIGTERM и rollback rehearsal;
- clean approved release commit, green CI, идентифицирани artifacts и operator sign-off.

Не е извършен deployment и **публичното пускане още не е одобрено**. Няма нужда
да се избира hosting сега; кодът и процедурите са подготвени за тази отделна
репетиция. Не са нужни нови microservices или Kubernetes за завършването ѝ.
