# Development API документация

Swagger UI е помощен инструмент за разработчик, не отделен вход в приложението.
По подразбиране OpenAPI и UI са изключени. Изричният профил `dev` ги включва,
обвързва listener-а с `127.0.0.1` и допуска само local GET/HEAD към docs/resources.
Без `dev`, както и при `prod`, документационните маршрути са отказани дори за
влязъл потребител. Комбинация `dev,prod` се отказва преди beans/DB startup,
независимо от реда на профилите. Не използвайте `dev` на публичен host или зад proxy.

## Стартиране

След обичайните local environment настройки от repository root:

```powershell
cd investment-tracker
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

В IntelliJ задайте `dev` в Active profiles само за development run configuration,
или program argument `--spring.profiles.active=dev`. Не е необходимо да сменяте
Windows environment variables глобално. Нормалното `bootRun` продължава да
работи, но без Swagger. След промяна на профила е нужен restart на backend.

- UI: `http://127.0.0.1:8080/swagger-ui.html` (redirect към `/swagger-ui/index.html`).
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`.
- OpenAPI YAML: `http://127.0.0.1:8080/v3/api-docs.yaml`.

## Authorize и Try it out

1. Използвайте отделен тестов акаунт. Отворете Authentication →
   `POST /api/v1/auth/authenticate`, въведете неговите credentials и изпълнете.
2. Копирайте `token` от отговора и в Authorize → `bearerAuth` поставете само
   стойността, без `Bearer `, кавички или целия JSON. Не изпращайте токена в чат.
3. Защитените операции изпращат `Authorization: Bearer …`; backend проверява
   истинската сесия, ownership и role. Документацията не предоставя допълнителни права.
4. Refresh/logout изискват `X-Requested-With: XMLHttpRequest`. Swagger го описва
   и предлага стойността, вместо да премахва cookie/CSRF защитата. Refresh използва
   HttpOnly cookie от входа в същия browser/origin; то не се копира ръчно.

Authorization не се пази след reload. Токенът все пак е чувствителен, докато е
зареден в страницата; не запазвайте network traces, screenshots с credentials
или цели authentication responses. Изтекъл/revoked token дава 401; след нов вход
обновете Authorize. Swagger не реализира автоматичния refresh на React клиента.

**Try it out не е симулация.** При нормален dev backend използва конфигурираната
база и SMTP/providers: write/delete/maintenance действия могат да променят данни
или да изпратят email. За напълно изолирана проверка използвайте browser acceptance
fixture, не личната база. Профилът `dev` сам по себе си не създава тестова база.

Проверки: реални springdoc HTML/assets/schema и bearer/security metadata,
remote/forwarded-header denial, default denial, mixed-profile fail-closed и
Chromium Authorize → защитен endpoint. Допълнение след `cbecc30`, проверено
локално на 2026-10-22: 529 backend tests passed + 1 предварително skipped manual
test; 22 browser scenarios passed (11 desktop + 11 mobile), включително проверка
на реално изпратения Authorization header и HTTP 200 от защитения endpoint;
217 frontend tests, lint, build и 17 smoke checks passed. Всичките 27 regression
tests с disposable MySQL 8.4.11 също минаха, включително Production startup;
реалната база не е използвана. Application и migration JAR се изграждат без
acceptance fixture/H2/Mockito. Този checkpoint не е CI резултат за бъдещ commit.

Източници:
[springdoc configuration](https://springdoc.org/),
[Spring Security request rules](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html).
