# Frontend tooling migration (Stage 8)

Updated: 2026-10-12. Branch: `security-reliability-refactor`.

Implementation is complete and initially verified with Node.js 24.19.0.
Following separate user approval on 2026-10-12, system Node.js was updated from
20.14.0 to 24.20.0 LTS through the standard Windows package manager, and the old
global npm override was updated to the installer-bundled npm 11.19.0. Normal
commands no longer require an alternate runtime. Commit/push/merge require
separate approval. Stage 9 was subsequently approved and is recorded in
[FINAL_VERIFICATION.md](FINAL_VERIFICATION.md).

## Scope

- Replace Create React App / `react-scripts` with Vite 8.3.4 and the React plugin
  6.1.2. React and React DOM remain 18.3.1; no React Compiler is enabled.
- Move `public/index.html` to the frontend root and rename the JSX bootstrap to
  `src/index.jsx`. Public image/manifest URLs, page title, root element and React
  StrictMode/BrowserRouter composition are retained. Update the template's
  description without changing page content.
- Preserve `npm start`, `localhost:3000` and the `build/` output directory.
  `npm run dev` is an alias; ports are strict and servers bind to localhost.
  Vite's filesystem allowance is restricted to the frontend directory.
- Replace CRA's Jest runner with Vitest 5.0.3, jsdom 30.1.2 and compatible Testing
  Library packages. Preserve existing test names, cases and assertions; use
  explicit Vitest imports. Remove the test-only Axios CJS resolver workaround.
  Module-reload tests use resetModules/dynamic import; constructor mocks use
  ordinary functions as required by the new runner.
- Add ESLint 10.12.0 flat configuration, core recommended rules, React hook
  checks and Fast Refresh export checks. Lint fails on warnings as well as
  errors; do not disable rules to conceal existing issues. Remove only unused
  default React imports under the automatic JSX runtime.
- Update Axios to 1.20.0 and React Router DOM to 7.18.4. Routes retain the
  declarative component API; this does not adopt Router framework mode, SSR,
  loaders or server actions. Existing authentication/request-lifecycle tests
  continue passing. UI/chart/date-picker package versions are deliberately
  retained so their styles and controls are not changed as part of tooling.
- Remove unused direct `jwt-decode`, `prop-types`, `@testing-library/user-event`
  and `web-vitals` dependencies. Remove the unconfigured `reportWebVitals.js`
  scaffold. Libraries still required transitively by used packages may remain.

The backend, database schema, API contracts, role policies, price-history rules,
cache timings and application JSX bodies are unchanged by this stage. No real
database/provider requests or backend startup are needed for these checks.

## Runtime and commands

Use Node.js 24 LTS, at least 24.15. Node.js 22.22.2+ is also compatible with the
current dependency set. The engines field reflects dependency requirements;
`.npmrc` enforces it, `.nvmrc` selects the 24 line, and `check:runtime` provides
a readable diagnostic for older installations. The migration itself used an
alternate compatible runtime; the subsequent system update was separately
approved and uses the standard installation path. No manual persistent PATH
edit or machine-specific runtime dependency was added to the project.

```powershell
npm ci
npm start
```

Verification commands:

```powershell
npm run lint
npm test
npm run build
```

At the Stage 8 checkpoint, `npm run verify` ran all three. Stage 9 adds the
production HTTP smoke check to that command. `npm test` runs once, while
`npm run test:watch` enables watch mode. `npm run preview` serves the already
built files locally on port 3000; it is not a public hosting solution.

The dependency lock file is regenerated from a clean, compatible dependency
tree and verified with `npm ci`, without `--force` or `--legacy-peer-deps`.
Old generated dependencies are kept temporarily in ignored local backups,
not in the repository deliverable. They are not used by the new commands.

## Public configuration

New public setting names are `VITE_API_URL` and
`VITE_ITEM_SEARCH_DEBOUNCE_MS`. The old `REACT_APP_API_URL` and
`REACT_APP_ITEM_SEARCH_DEBOUNCE_MS` are fallback names only, preserving existing
local configuration. New names take precedence; defaults remain
`http://localhost:8080` and 1000 milliseconds. Debounce parsing keeps the existing
zero/empty/invalid-value semantics.

`readPublicEnvironment` maps only those two known settings into explicit client
definitions. It does not spread the backend/process environment into the
browser bundle. Vite also treats any `VITE_` variable as public by design: never
place database/mail passwords, JWT secrets or private keys under that prefix.
Ignored `.env` files and real OS secrets are not edited or printed by this stage.

See [LOCAL_SETUP.md](../LOCAL_SETUP.md) for normal Windows installation commands.

## Verified results

- Clean `npm ci`: successful, with no deprecated-tool or incompatible-peer
  warning in the final clean installation.
- After the separately approved system update, `npm ci` and `npm run verify`
  also passed using normal Node.js 24.20.0 / npm 11.19.0 commands. The lock file
  stayed identical; lint, all 94 tests and production build passed again.
- Vitest: 94 tests in 22 files, all passed. All 89 earlier tests are retained;
  five new cases check public config defaults, fallback, precedence, zero/empty
  handling and exclusion of unrelated/private settings.
- ESLint: 0 errors and 0 warnings with `--max-warnings 0`.
- Production build: successful, retaining the existing `build/` output.
- Local HTTP smoke checks: 18 passed across the dev server and built preview,
  including SPA fallback routes, JSX/HMR modules, public settings/assets and
  denial of repository-private filesystem access. Both temporary servers were
  closed. This is not a visual or authenticated end-to-end workflow review.
- Structural comparison: all 14 source CSS files are identical; 63 production
  module bodies are unchanged, apart from the two explicitly allowed environment
  access replacements. Import cleanup and the bootstrap/no-op telemetry removal
  are separately accounted for.
- Backend source/resources are unchanged; no real MySQL data is accessed.
- npm audit: 0 known findings across runtime and development dependencies,
  compared with 82 before migration. This is a registry/advisory snapshot, not a
  claim that the application has no security risks.

## Remaining boundaries

Vite reports a main JavaScript chunk above its default 500 kB warning threshold
(about 847 kB minified / 255 kB gzip at this checkpoint). Build succeeds. This
warning is not hidden by raising the threshold; route-level lazy loading/bundle
optimization is a separate change that would need its own verification. No
claim about production load time or capacity is made here.

Configured syntax targets are Chrome 87, Edge 88, Firefox 78 and Safari 14.
These are compilation targets, not proof of full browser/API compatibility.
Stage 9 adds integrated API/role workflows and frontend route checks; its
results and remaining browser acceptance/CI boundaries are recorded separately.
The normal system runtime update is complete; the user manual review remains
pending. Publication and merge are not performed in this stage.

## Browser date-picker follow-up (2026-10-12)

Manual browser verification found that Vite's client chose `react-datepicker`
7.3.0's UMD browser build and imported a module object as the component, crashing
the authenticated portfolio. An exact-match `resolve.alias` selects the existing
ESM entry without changing package versions, stylesheet imports, form bodies or
backend behavior. A real Vite-client resolution regression and an unmocked form/
calendar test were added; 110 tests in 25 files, lint, build and 17 production
HTTP checks pass. Targeted existing-session Chrome checks pass for the portfolio
and registration/edit calendars. See [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md)
for the remaining acceptance boundaries and the upstream sourcemap warning.

## Primary references

- [React: Sunsetting Create React App](https://react.dev/blog/2025/02/14/sunsetting-create-react-app)
- [Vite: Getting Started](https://vite.dev/guide/)
- [Vite: Environment Variables](https://vite.dev/guide/env-and-mode)
- [Vite: Resolve Alias](https://vite.dev/config/shared-options#resolve-alias)
- [Vitest: Jest Migration](https://vitest.dev/guide/migration/)
- [ESLint: Migrating to v10](https://eslint.org/docs/latest/use/migrate-to-10.0.0)
- [React Router: Changelog](https://reactrouter.com/start/start/changelog)
- [Node.js: Releases](https://nodejs.org/en/about/previous-releases)
