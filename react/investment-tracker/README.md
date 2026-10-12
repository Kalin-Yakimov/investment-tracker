# Investment Tracker frontend

React 18 interface built with Vite. Backend endpoints, page URLs and the visual
design are retained; this is not a React Router framework/SSR application.

## Requirements

Use Node.js 24 LTS (24.15 or newer). Node.js 22.22.2+ is also accepted by the
current dependency set. Node.js 20 is not supported. Reopen the terminal after
installing/updating Node, then check `node --version`.

## Local development

```powershell
npm ci
npm start
```

Open `http://localhost:3000`. `npm run dev` is an alias. The dev server is local
only and will not silently switch ports when port 3000 is occupied.

For local public settings, copy `.env.example` to `.env` if needed. Set
`VITE_API_URL` and optionally `VITE_ITEM_SEARCH_DEBOUNCE_MS`. Existing
`REACT_APP_API_URL` and `REACT_APP_ITEM_SEARCH_DEBOUNCE_MS` values remain fallback
settings. Do not put passwords, JWT secrets or private API keys in frontend
configuration: values in the browser bundle are public.

## Verification and build

```powershell
npm run lint
npm test
npm run build
npm run smoke:build
```

`npm run verify` runs all four. The smoke check serves the built files on a
temporary loopback port and closes its own server; it does not access the API.
`npm run test:watch` is interactive watch mode;
`npm test` runs once. Vitest uses simulated DOM/test adapters, not real user data.

The production output stays in `build/`. `npm run preview` serves the built files
locally on port 3000; it is not intended as a public production server. Hosting
must serve `index.html` for client-side page URLs, just as before.

For the database, email, backend secrets and a full clean-computer setup, see
[LOCAL_SETUP.md](../../LOCAL_SETUP.md). Source organization is in
[CODEBASE_STRUCTURE.md](../../docs/CODEBASE_STRUCTURE.md); migration boundaries
and verification are in [FRONTEND_TOOLING.md](../../docs/FRONTEND_TOOLING.md).
Final integrated/CI checks and the remaining acceptance steps are in
[FINAL_VERIFICATION.md](../../docs/FINAL_VERIFICATION.md).
