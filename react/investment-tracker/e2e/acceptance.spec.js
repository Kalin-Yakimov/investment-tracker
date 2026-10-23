import { expect, test } from "@playwright/test";

const API = "http://127.0.0.1:18080";
const PASSWORD = "acceptance-test-password";
const KEY = "synthetic-acceptance-only";
const accounts = { user: "acceptance.user@example.invalid", admin: "acceptance.admin@example.invalid", support: "acceptance.support@example.invalid" };
const pageErrors = new WeakMap();

async function signIn(page, role = "user") {
  await page.goto("/login");
  await page.getByLabel("Email Address", { exact: true }).fill(accounts[role]);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Register Portfolio Item", exact: true })).toBeVisible();
}

async function profileMenu(page) { await page.locator(".profile-nav-btn").click(); }

test.beforeEach(async ({ page, request }) => {
  pageErrors.set(page, []);
  page.on("pageerror", error => pageErrors.get(page).push(error.message));
  const marker = await request.get(`${API}/__acceptance__/ready`);
  expect(await marker.json()).toEqual({ fixture: "isolated-loopback", ready: true });
  // Browser checks cannot contact Steam, SMTP or any unrelated external service.
  await page.route("**/*", route => {
    const url = new URL(route.request().url());
    return ["127.0.0.1"].includes(url.hostname) ? route.continue() : route.abort();
  });
});
test.afterEach(async ({ page }) => { expect(pageErrors.get(page)).toEqual([]); });

test("protected navigation redirects anonymously and sign-in works by keyboard", async ({ page }) => {
  await page.goto("/tracker");
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel("Email Address", { exact: true }).fill(accounts.user);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByLabel("Password", { exact: true }).press("Enter");
  await expect(page.getByRole("heading", { name: "Register Portfolio Item" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Register Portfolio Item" })).toBeVisible();
  await profileMenu(page);
  await expect(page.getByRole("button", { name: "Support Panel", exact: true })).toHaveCount(0);
});

test("portfolio writes persist through navigation and private records cannot be read", async ({ page, request }, testInfo) => {
  await signIn(page);
  const name = testInfo.project.name.startsWith("desktop") ? "QA Browser Case 22" : "QA Browser Case 25";
  await page.getByRole("combobox", { name: "Item Name", exact: true }).fill(name);
  await page.getByRole("option", { name, exact: true }).click();
  await page.getByLabel("Status", { exact: true }).selectOption("BOUGHT");
  await page.getByLabel("Quantity", { exact: true }).fill("2");
  await page.getByLabel("Price (€)", { exact: true }).fill("3.25");
  await page.getByRole("button", { name: "Register Item", exact: true }).click();
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  await page.getByRole("button", { name, exact: true }).click();
  await page.getByRole("link", { name: "Edit Item", exact: true }).click();
  await page.getByLabel("Quantity", { exact: true }).fill("3");
  await page.getByRole("button", { name: "Update Item", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Item Details", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "3", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Steam Price Tracker", exact: true }).click();
  await page.getByRole("link", { name: "Portfolio Overview", exact: true }).click();
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  const meta = await (await request.get(`${API}/__acceptance__/meta`, { headers: { "X-Acceptance-Key": KEY } })).json();
  await page.goto(`/viewItem/${meta.privatePortfolioId}`);
  await expect(page.getByText("Item not found", { exact: true })).toBeVisible();
  await expect(page.getByText("Private fixture record", { exact: true })).toHaveCount(0);
});

test("ordinary accounts and support cannot bypass API ownership or admin roles", async ({ request }) => {
  const meta = await (await request.get(`${API}/__acceptance__/meta`, { headers: { "X-Acceptance-Key": KEY } })).json();
  async function credentials(role) {
    const response = await request.post(`${API}/api/v1/auth/authenticate`, { data: { email: accounts[role], password: PASSWORD } });
    expect(response.status()).toBe(200);
    return { Authorization: `Bearer ${(await response.json()).token}` };
  }
  const user = await credentials("user");
  expect((await request.get(`${API}/item/${meta.privatePortfolioId}`, { headers: user })).status()).toBe(404);
  expect((await request.delete(`${API}/item/${meta.privatePortfolioId}`, { headers: user })).status()).toBe(404);
  expect((await request.get(`${API}/api/admin/overview`, { headers: user })).status()).toBe(403);
  const support = await credentials("support");
  expect((await request.get(`${API}/api/admin/overview`, { headers: support })).status()).toBe(200);
  expect((await request.get(`${API}/api/admin/audit`, { headers: support })).status()).toBe(403);
  expect((await request.patch(`${API}/api/admin/users/1/support-role`, { headers: support,
    data: { enabled: true, reason: "Unauthorized fixture mutation attempt" } })).status()).toBe(403);
});

test("portfolio detail return, edit and deletion retain the list position without URL or reload persistence", async ({ page, request }, testInfo) => {
  const role = testInfo.project.name.startsWith("desktop") ? "support" : "admin";
  const login = await request.post(`${API}/api/v1/auth/authenticate`, { data: { email: accounts[role], password: PASSWORD } });
  expect(login.status()).toBe(200);
  const headers = { Authorization: `Bearer ${(await login.json()).token}` };
  const prefix = `Return page ${testInfo.project.name}`;
  const records = [];
  for (let number = 1; number <= 22; number++) {
    const created = await request.post(`${API}/item`, { headers,
      data: { itemName: `${prefix} ${String(number).padStart(2, "0")}`, itemStatus: "BOUGHT", date: "01.10.2026", quantity: 1, price: "3.25" } });
    expect(created.status()).toBe(200);
    records.push(await created.json());
  }
  await signIn(page, role);
  const pagination = page.getByRole("navigation", { name: "Portfolio pagination" });
  await pagination.getByRole("button", { name: "2", exact: true }).click();
  const name = records[11].itemName;
  await page.getByRole("button", { name, exact: true }).scrollIntoViewIfNeeded();
  async function openRecord() {
    // Capture the actual click position: mobile focus/tap can move the viewport
    // slightly after Playwright has scrolled the target into view.
    const [scrollTop] = await Promise.all([
      page.evaluate(() => new Promise(resolve => window.addEventListener("click", () => resolve(window.scrollY), { capture: true, once: true }))),
      page.getByRole("button", { name, exact: true }).click(),
    ]);
    return scrollTop;
  }
  const returnScrollTop = await openRecord();
  expect(returnScrollTop).toBeGreaterThan(0);
  await expect(page.getByRole("heading", { name: "Item Details", exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  await page.goBack();
  await expect(pagination.getByRole("button", { name: "2", exact: true })).toHaveAttribute("aria-current", "page");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeCloseTo(returnScrollTop, 0);
  const editReturnScrollTop = await openRecord();
  await page.getByRole("link", { name: "Edit Item", exact: true }).click();
  await page.getByLabel("Quantity", { exact: true }).fill("3");
  await page.getByRole("button", { name: "Update Item", exact: true }).click();
  await page.getByRole("link", { name: /Back to Portfolio/ }).click();
  await expect(pagination.getByRole("button", { name: "2", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("button", { name, exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeCloseTo(editReturnScrollTop, 0);
  await page.getByRole("button", { name, exact: true }).click();
  await page.getByRole("button", { name: /Delete Item/ }).click();
  await expect(pagination.getByRole("button", { name: "2", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("button", { name, exact: true })).toHaveCount(0);
  await pagination.getByRole("button", { name: "3", exact: true }).click();
  await page.getByRole("button", { name: records[0].itemName, exact: true }).click();
  await page.getByRole("button", { name: /Delete Item/ }).click();
  await expect(pagination.getByRole("button", { name: "2", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(pagination.getByRole("button", { name: "3", exact: true })).toHaveCount(0);
  await expect(page).toHaveURL("http://127.0.0.1:14173/");
  await page.reload();
  await expect(pagination.getByRole("button", { name: "1", exact: true })).toHaveAttribute("aria-current", "page");
});

test("sign-out revokes both independently issued sessions", async ({ request }) => {
  const tokens = [];
  for (let i = 0; i < 2; i++) {
    const response = await request.post(`${API}/api/v1/auth/authenticate`, { data: { email: accounts.user, password: PASSWORD } });
    expect(response.status()).toBe(200); tokens.push((await response.json()).token);
  }
  for (const token of tokens) {
    expect((await request.get(`${API}/api/v1/auth/validate-token`, { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(200);
  }
  const logout = await request.post(`${API}/api/v1/auth/logout`,
    { headers: { Authorization: `Bearer ${tokens[0]}`, "X-Requested-With": "XMLHttpRequest" } });
  expect(logout.status()).toBe(204);
  for (const token of tokens) {
    expect((await request.get(`${API}/api/v1/auth/validate-token`, { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(401);
  }
});

test("development Swagger sends bearer authorization to a protected real endpoint", async ({ page, request }) => {
  const login = await request.post(`${API}/api/v1/auth/authenticate`, { data: { email: accounts.user, password: PASSWORD } });
  expect(login.status()).toBe(200);
  const token = (await login.json()).token;
  await page.goto(`${API}/swagger-ui/index.html`);
  await expect(page.getByRole("heading", { name: /Investment Tracker API/ })).toBeVisible();
  await page.getByRole("button", { name: "Authorize", exact: true }).click();
  const authorization = page.locator(".dialog-ux");
  await authorization.locator('input[type="text"]').fill(token);
  await authorization.getByRole("button", { name: "Apply credentials", exact: true }).click();
  await authorization.getByRole("button", { name: "Close", exact: true }).click();
  const endpoint = page.locator(".opblock").filter({ has: page.getByText("/api/v1/auth/validate-token", { exact: true }) });
  await endpoint.locator(".opblock-summary-control").click();
  await endpoint.getByRole("button", { name: "Try it out", exact: true }).click();
  let bearerSent = false;
  page.on("request", outgoing => {
    if (outgoing.url() === `${API}/api/v1/auth/validate-token`) bearerSent = outgoing.headers().authorization === `Bearer ${token}`;
  });
  const [response] = await Promise.all([
    page.waitForResponse(result => result.url() === `${API}/api/v1/auth/validate-token`),
    endpoint.getByRole("button", { name: "Execute", exact: true }).click(),
  ]);
  expect(bearerSent).toBe(true);
  expect(response.status()).toBe(200);
});

test("catalogue keyboard selection, tracking pagination and chart dialog work", async ({ page }, testInfo) => {
  await signIn(page);
  await page.getByRole("link", { name: "Steam Price Tracker", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Steam Community Market Price Tracker" })).toBeVisible();
  const pagination = page.getByRole("navigation", { name: "Tracked items pagination" });
  await pagination.getByRole("button", { name: "2", exact: true }).click();
  await expect(page.getByRole("button", { name: "QA Browser Case 01", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "QA Browser Case 01", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("dialog").locator("canvas")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const search = page.getByRole("combobox", { name: "Search CS items to track" });
  const targetName = testInfo.project.name.startsWith("desktop") ? "AK-47 | Redline (Field-Tested)" : "AK-47 | Redline (Minimal Wear)";
  await search.fill(testInfo.project.name.startsWith("desktop") ? "ak red field" : "ak red minimal");
  await expect(page.getByRole("option", { name: targetName, exact: true })).toBeVisible();
  await search.press("ArrowDown");
  await search.press("Enter");
  await expect(page.getByRole("button", { name: targetName, exact: true })).toBeVisible();
});

test("request failure offers a retry without inventing empty tracked data", async ({ page }) => {
  await signIn(page);
  let fail = true;
  await page.route("**/api/items?*", route => fail ? route.fulfill({ status: 503, contentType: "application/json", body: '{"error":"Synthetic temporary outage"}' }) : route.continue());
  await page.getByRole("link", { name: "Steam Price Tracker", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry tracked items" })).toBeVisible();
  await expect(page.getByText("No items tracked yet.", { exact: true })).toHaveCount(0);
  fail = false;
  await page.getByRole("button", { name: "Retry tracked items" }).click();
  await expect(page.locator(".tracker-table tbody tr")).toHaveCount(20);
});

test("inventory result survives navigation and is cleared at sign-out", async ({ page }) => {
  await signIn(page);
  await page.getByRole("link", { name: "Inventory Calculator", exact: true }).click();
  await page.getByRole("textbox", { name: "Steam profile URL or SteamID64" }).fill("76561198000000000");
  await page.getByRole("button", { name: /Calculate Value/ }).click();
  await expect(page.getByText("Acceptance Inventory", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Portfolio Overview", exact: true }).click();
  await page.getByRole("link", { name: "Inventory Calculator", exact: true }).click();
  await expect(page.getByText("Acceptance Inventory", { exact: true })).toBeVisible();
  await profileMenu(page); await page.getByRole("button", { name: "Sign Out", exact: true }).click();
  await expect(page).toHaveURL(/\/login/);
  await signIn(page);
  await page.getByRole("link", { name: "Inventory Calculator", exact: true }).click();
  await expect(page.getByText("Acceptance Inventory", { exact: true })).toHaveCount(0);
});

test("contact request is stored and can be read by an administrator", async ({ page }) => {
  await signIn(page);
  await profileMenu(page); await page.getByRole("button", { name: "Contact Us", exact: true }).click();
  await page.getByLabel("Message", { exact: true }).fill("Synthetic acceptance contact request, no real user data.");
  await page.getByRole("button", { name: /Send Message/ }).click();
  await expect(page.getByText(/Your message was sent successfully/)).toBeVisible();
  await page.getByRole("button", { name: "Close", exact: true }).last().click();
  await profileMenu(page); await page.getByRole("button", { name: "Sign Out", exact: true }).click();
  await signIn(page, "admin"); await profileMenu(page);
  await page.getByRole("button", { name: "Support Panel", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Support Panel", exact: true })).toBeVisible();
  await page.getByRole("button", { name: /Contact requests/i }).click();
  await expect(page.getByText("Synthetic acceptance contact request, no real user data.").first()).toBeVisible();
});

test("registration, mailbox activation and a fresh real session work together", async ({ page, request }) => {
  const name = `browser-${Date.now()}`;
  const email = `${name}@example.invalid`;
  await page.goto("/registration");
  await page.getByLabel("Username", { exact: true }).fill(name);
  await page.getByLabel("Email Address", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByLabel("Confirm Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Register Account", exact: true }).click();
  await expect(page.getByLabel("Activation Code", { exact: true })).toBeVisible();
  let code;
  await expect.poll(async () => {
    code = (await (await request.get(`${API}/__acceptance__/mailbox`, { params: { email }, headers: { "X-Acceptance-Key": KEY } })).json()).code;
    return code;
  }).toMatch(/^\d{6}$/);
  await page.getByLabel("Activation Code", { exact: true }).fill(code);
  await page.getByRole("button", { name: "Activate Account", exact: true }).click();
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel("Email Address", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Register Portfolio Item", exact: true })).toBeVisible();
});

test("page layout stays within the viewport and navigation remains reachable", async ({ page }) => {
  await signIn(page);
  for (const link of ["Steam Price Tracker", "Inventory Calculator", "Portfolio Overview"]) {
    await page.mouse.wheel(0, 600);
    await page.getByRole("link", { name: link, exact: true }).click();
    await expect(page.getByRole("link", { name: link, exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  }
  for (const width of [320, 615, 900, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    await profileMenu(page);
    const bounds = await page.locator(".profile-popover-card").boundingBox();
    expect(bounds.x).toBeGreaterThanOrEqual(0);
    expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
    await profileMenu(page);
  }
});
