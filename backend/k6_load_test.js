import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/$/, '');
const DATA_FILE = __ENV.DATA_FILE || './k6_data.json';
const PROFILE = (__ENV.TEST_PROFILE || 'stress').toLowerCase();
const IS_SMOKE = PROFILE === 'smoke';
const STRESS_SCALE = Math.max(0.05, Number(__ENV.STRESS_SCALE || '1'));
const STRICT_MODE = (__ENV.STRICT_MODE || (IS_SMOKE ? 'true' : 'false')).toLowerCase() === 'true';
const GROUP_SIZE_TARGET = Math.max(3, Math.min(5, Number(__ENV.GROUP_SIZE_TARGET || '4')));
const CONCURRENT_TABLE_VUS = IS_SMOKE
  ? GROUP_SIZE_TARGET
  : Math.max(GROUP_SIZE_TARGET, scaleTarget(25));
const CONCURRENT_TABLE_ITERATIONS = IS_SMOKE
  ? 3
  : Math.max(3, scaleTarget(12));

const businessErrorRate = new Rate('business_error_rate');
const sessionOpenCount = new Counter('session_open_count');
const sessionCloseCount = new Counter('session_close_count');

const seed = new SharedArray('k6-seed', function () {
  const raw = open(DATA_FILE).replace(/^\uFEFF/, '');
  return [JSON.parse(raw)];
})[0];

const SEARCH_TERMS = Array.isArray(seed.searchTerms) && seed.searchTerms.length
  ? seed.searchTerms
  : ['pho', 'com', 'ga'];

const TABLE_NAMES = Array.isArray(seed.tableNames) && seed.tableNames.length
  ? seed.tableNames
  : ['K6-T1', 'K6-T2', 'K6-T3', 'K6-T4', 'K6-T5'];

const PRODUCT_SEEDS = Array.isArray(seed.products) && seed.products.length
  ? seed.products
  : [
      { name: 'K6 Pho Bo', price: 45000 },
      { name: 'K6 Com Ga', price: 39000 },
      { name: 'K6 Tra Dao', price: 29000 },
    ];

function scaleTarget(value) {
  return Math.max(1, Math.floor(value * STRESS_SCALE));
}

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    business_error_rate: ['rate<0.01'],
  },
  scenarios: IS_SMOKE
    ? {
        concurrent_table_ops: {
          executor: 'per-vu-iterations',
          exec: 'concurrentTableOperations',
          vus: CONCURRENT_TABLE_VUS,
          iterations: 3,
          maxDuration: '60s',
          startTime: '0s',
        },
      }
    : {
        group_order: {
          executor: 'ramping-vus',
          exec: 'groupOrderFlow',
          startVUs: 0,
          stages: [
            { duration: '30s', target: scaleTarget(20) },
            { duration: '60s', target: scaleTarget(40) },
            { duration: '90s', target: scaleTarget(60) },
            { duration: '60s', target: 0 },
          ],
        },
        concurrent_table_ops: {
          executor: 'per-vu-iterations',
          exec: 'concurrentTableOperations',
          vus: CONCURRENT_TABLE_VUS,
          iterations: CONCURRENT_TABLE_ITERATIONS,
          maxDuration: '240s',
          startTime: '30s',
        },
      },
};

function request(method, path, body, headers, parseBody) {
  const res = http.request(method, `${BASE_URL}${path}`, body, {
    headers: headers || {},
    responseType: parseBody ? 'text' : undefined,
    timeout: '15s',
  });

  let parsed = null;
  if (parseBody && res.body) {
    try {
      parsed = JSON.parse(res.body);
    } catch (_) {
      parsed = null;
    }
  }

  const ok = res.status >= 200 && res.status < 300 && (!parsed || parsed.code === 200);
  const metricPath = path
    .replace(/[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,36}/g, ':id')
    .replace(/\/\d+/g, '/:id');
  businessErrorRate.add(!ok, { path: metricPath, method });

  if (!ok && IS_SMOKE) {
    console.log(`[SMOKE-ERROR] ${method} ${path} status=${res.status} message=${parsed ? parsed.message : 'N/A'}`);
  }

  return {
    ok,
    res,
    data: parsed ? parsed.data : null,
    message: parsed ? parsed.message : '',
  };
}

function assertStep(result, checkName, detail) {
  const passed = check(result.res, {
    [checkName]: function () { return result.ok; },
  });

  if (!passed && STRICT_MODE) {
    fail(`${checkName} failed: ${detail || result.message || 'no detail'}`);
  }
  return passed;
}

function authHeaders(ctx) {
  return {
    Authorization: `Bearer ${ctx.token}`,
    'X-Tenant-ID': ctx.tenantId,
  };
}

function jsonHeaders(ctx) {
  return {
    ...authHeaders(ctx),
    'Content-Type': 'application/json',
  };
}

function publicHeaders(ctx) {
  return {
    'X-Tenant-ID': ctx.tenantId,
  };
}

function publicJsonHeaders(ctx) {
  return {
    ...publicHeaders(ctx),
    'Content-Type': 'application/json',
  };
}

function randomFrom(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function buildTableSeedNames() {
  // Keep table pool larger than max concurrent VUs so each VU can stick to a dedicated table.
  const desired = IS_SMOKE ? 8 : Math.max(60, scaleTarget(160));
  const names = [];
  for (let i = 0; i < desired; i += 1) {
    names.push(`${TABLE_NAMES[i % TABLE_NAMES.length]}-${String(i + 1).padStart(3, '0')}`);
  }
  return names;
}

function vuPick(list, offset) {
  return list[(exec.vu.idInTest + exec.scenario.iterationInTest + (offset || 0)) % list.length];
}

function dedicatedVuTableId(tableIds, offset) {
  if (!Array.isArray(tableIds) || !tableIds.length) return null;
  const vuIndex = Math.max(0, exec.vu.idInTest - 1);
  return String(tableIds[(vuIndex + (offset || 0)) % tableIds.length]);
}

function listTables(ctx) {
  return request('GET', '/api/pos/tables', null, authHeaders(ctx), true);
}

function pickAvailableTable(ctx, preferredTableIds) {
  const tablesRes = listTables(ctx);
  if (!tablesRes.ok || !Array.isArray(tablesRes.data)) return null;

  const preferred = Array.isArray(preferredTableIds) && preferredTableIds.length
    ? new Set(preferredTableIds.map(function (id) { return String(id); }))
    : null;

  const candidates = tablesRes.data.filter(function (t) {
    const id = String(t.id);
    const inPreferredPool = !preferred || preferred.has(id);
    return inPreferredPool
      && String(t.status || '').toUpperCase() === 'AVAILABLE'
      && (t.sessionId === null || t.sessionId === undefined);
  });
  if (!candidates.length) return null;
  return String(randomFrom(candidates).id);
}

function getSessionDetail(ctx, sessionId) {
  return request('GET', `/api/pos/sessions/${sessionId}`, null, authHeaders(ctx), true);
}

function isPendingSession(ctx, sessionId) {
  const pending = request('GET', '/api/pos/sessions/pending', null, authHeaders(ctx), true);
  if (!pending.ok || !Array.isArray(pending.data)) return false;
  return pending.data.some(function (s) {
    return Number(s.sessionId) === Number(sessionId);
  });
}

function findPendingItemId(sessionData) {
  if (!sessionData || !Array.isArray(sessionData.orders)) return null;

  for (const order of sessionData.orders) {
    if (!order || !Array.isArray(order.items)) continue;
    for (const item of order.items) {
      if (item && item.id !== undefined && String(item.status || '').toUpperCase() === 'PENDING') {
        return item.id;
      }
    }
  }
  return null;
}

export function setup() {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  const username = `k6_owner_${suffix}`;
  const password = seed.ownerPassword || 'Password@123';
  const email = `k6_${suffix}@example.com`;

  const register = request(
    'POST',
    '/api/auth/register',
    JSON.stringify({ username, password, email, fullName: 'K6 Owner' }),
    { 'Content-Type': 'application/json' },
    true
  );
  if (!register.ok) fail(`register failed: ${register.message}`);

  const login = request(
    'POST',
    '/api/auth/login',
    JSON.stringify({ username, password }),
    { 'Content-Type': 'application/json' },
    true
  );
  if (!login.ok || !login.data || !login.data.accessToken) fail('login failed');

  const token = login.data.accessToken;
  const tenantReq = {
    name: `K6 Tenant ${suffix}`,
    address: 'HCM',
    logo: http.file('k6', 'logo.txt', 'text/plain'),
  };
  const tenant = request('POST', '/api/tenants', tenantReq, { Authorization: `Bearer ${token}` }, true);
  if (!tenant.ok || !tenant.data || !tenant.data.id) fail('create tenant failed');

  const tenantId = tenant.data.id;
  const ctx = { token, tenantId };

  const categories = request('GET', '/api/categories', null, authHeaders(ctx), true);
  if (!categories.ok || !Array.isArray(categories.data) || categories.data.length === 0) {
    fail('cannot fetch default category');
  }
  const categoryId = categories.data[0].id;

  const tableIds = [];
  for (const tableName of buildTableSeedNames()) {
    const t = request('POST', `/api/pos/tables?name=${encodeURIComponent(`${tableName}-${suffix.slice(-4)}`)}`, null, authHeaders(ctx), true);
    if (t.ok && t.data && t.data.id) tableIds.push(String(t.data.id));
  }
  if (!tableIds.length) fail('cannot create any table');

  const qrPoolSize = Math.max(2, Math.floor(tableIds.length * 0.3));
  const qrTableIds = tableIds.slice(0, qrPoolSize);
  const opsTableIds = tableIds.slice(qrPoolSize);
  const effectiveOpsTableIds = opsTableIds.length ? opsTableIds : tableIds;

  const productIds = [];
  for (const p of PRODUCT_SEEDS) {
    const payload = {
      categoryId: String(categoryId),
      name: `${p.name}-${suffix.slice(-4)}`,
      price: String(p.price),
      description: 'k6 seeded product',
      images: http.file('k6-product', 'p.txt', 'text/plain'),
    };
    const pr = request('POST', '/api/products', payload, authHeaders(ctx), true);
    if (pr.ok && pr.data && pr.data.id) productIds.push(Number(pr.data.id));
  }
  if (!productIds.length) fail('cannot create any product');

  const stressMaxVus = scaleTarget(60);
  const desiredSessionGroups = IS_SMOKE ? 1 : Math.max(1, Math.floor(stressMaxVus / GROUP_SIZE_TARGET));
  const groupSessionCount = Math.min(desiredSessionGroups, tableIds.length);

  const sharedSessionIds = [];
  const sharedTableIds = [];
  for (let i = 0; i < groupSessionCount; i += 1) {
    const groupTableId = String(tableIds[i]);
    const groupSession = request(
      'POST',
      '/api/pos/sessions',
      JSON.stringify({ tableId: groupTableId, note: `k6 shared group session #${i + 1}` }),
      jsonHeaders({ token, tenantId }),
      true
    );
    if (groupSession.ok && groupSession.data && groupSession.data.sessionId) {
      sharedSessionIds.push(String(groupSession.data.sessionId));
      sharedTableIds.push(groupTableId);
    }
  }
  if (!sharedSessionIds.length) fail('cannot create shared group sessions');

  return {
    token,
    tenantId,
    tableIds,
    qrTableIds,
    opsTableIds: effectiveOpsTableIds,
    productIds,
    paymentMethod: seed.paymentMethod || 'CASH',
    sharedSessionIds,
    sharedTableIds,
  };
}

export function sessionManagement(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const opsTableIds = Array.isArray(data.opsTableIds) && data.opsTableIds.length ? data.opsTableIds : data.tableIds;
  const productId = Number(vuPick(data.productIds, 0));

  group('session open/close', function () {
    const tableId = pickAvailableTable(ctx, opsTableIds) || vuPick(opsTableIds, 0);

    const openRes = request(
      'POST',
      '/api/pos/sessions',
      JSON.stringify({ tableId, note: 'k6 open session' }),
      jsonHeaders(ctx),
      true
    );
    assertStep(openRes, 'open session success', 'POST /api/pos/sessions');
    if (!openRes.ok || !openRes.data || !openRes.data.sessionId) return;

    sessionOpenCount.add(1);
    const sessionId = openRes.data.sessionId;

    const addRes = request(
      'POST',
      `/api/pos/sessions/${sessionId}/items`,
      JSON.stringify({ items: [{ productId, quantity: 1 }] }),
      jsonHeaders(ctx),
      true
    );
    assertStep(addRes, 'session add item success', `POST /api/pos/sessions/${sessionId}/items`);
    if (!addRes.ok) return;

    const payRes = request(
      'POST',
      `/api/pos/sessions/${sessionId}/pay`,
      JSON.stringify({ method: data.paymentMethod }),
      jsonHeaders(ctx),
      true
    );
    assertStep(payRes, 'close session success', `POST /api/pos/sessions/${sessionId}/pay`);
    if (payRes.ok) sessionCloseCount.add(1);
  });
}

export function orderProcessing(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const qrTableIds = Array.isArray(data.qrTableIds) && data.qrTableIds.length ? data.qrTableIds : data.tableIds;
  const opsTableIds = Array.isArray(data.opsTableIds) && data.opsTableIds.length ? data.opsTableIds : data.tableIds;

  group('order processing', function () {
    const tableId = pickAvailableTable(ctx, qrTableIds) || vuPick(qrTableIds, 1);
    const p1 = Number(vuPick(data.productIds, 2));
    const p2 = Number(vuPick(data.productIds, 3));

    // Public customer side flow: create, check status, request payment, and close.
    const customer = request(
      'POST',
      '/api/pos/public/sessions',
      JSON.stringify({ tableId, items: [{ productId: p1, quantity: 1 }], customerNote: 'k6 customer order' }),
      publicJsonHeaders(ctx),
      true
    );
    assertStep(customer, 'customer order success', 'POST /api/pos/public/sessions');

    if (customer.ok && customer.data && customer.data.sessionId) {
      const confirmed = request(
        'POST',
        `/api/pos/sessions/${customer.data.sessionId}/confirm`,
        null,
        authHeaders(ctx),
        true
      );
      assertStep(confirmed, 'customer confirm session success', `POST /api/pos/sessions/${customer.data.sessionId}/confirm`);

      const customerStatus = request('GET', `/api/pos/public/sessions/${customer.data.sessionId}`, null, publicHeaders(ctx), true);
      assertStep(customerStatus, 'customer status success', `GET /api/pos/public/sessions/${customer.data.sessionId}`);

      const customerRequestPayment = request(
        'POST',
        `/api/pos/public/sessions/${customer.data.sessionId}/request-payment`,
        null,
        publicHeaders(ctx),
        true
      );
      assertStep(
        customerRequestPayment,
        'customer request payment success',
        `POST /api/pos/public/sessions/${customer.data.sessionId}/request-payment`
      );

      const customerPay = request(
        'POST',
        `/api/pos/sessions/${customer.data.sessionId}/pay`,
        JSON.stringify({ method: data.paymentMethod }),
        jsonHeaders(ctx),
        true
      );
      assertStep(customerPay, 'customer pay session success', `POST /api/pos/sessions/${customer.data.sessionId}/pay`);
    }

    // Staff internal flow: open session, add/update/delete items.
    const staffTable = pickAvailableTable(ctx, opsTableIds) || vuPick(opsTableIds, 2);
    const staffOpen = request(
      'POST',
      '/api/pos/sessions',
      JSON.stringify({ tableId: staffTable, note: 'k6 staff order flow' }),
      jsonHeaders(ctx),
      true
    );
    assertStep(staffOpen, 'staff open session success', 'POST /api/pos/sessions');
    if (!staffOpen.ok || !staffOpen.data || !staffOpen.data.sessionId) return;

    const sessionId = staffOpen.data.sessionId;
    const addStaff = request(
      'POST',
      `/api/pos/sessions/${sessionId}/items`,
      JSON.stringify({ items: [{ productId: p1, quantity: 1 }, { productId: p2, quantity: 1 }] }),
      jsonHeaders(ctx),
      true
    );
    assertStep(addStaff, 'staff add item success', `POST /api/pos/sessions/${sessionId}/items`);
    if (!addStaff.ok) return;

    const detail = getSessionDetail(ctx, sessionId);
    assertStep(detail, 'staff get session detail success', `GET /api/pos/sessions/${sessionId}`);
    if (!detail.ok) return;
    const itemId = findPendingItemId(detail.data);
    if (!itemId) return;

    const upd = request(
      'PATCH',
      `/api/pos/sessions/${sessionId}/items/${itemId}`,
      JSON.stringify({ quantity: 3 }),
      jsonHeaders(ctx),
      true
    );
    assertStep(upd, 'update quantity success', `PATCH /api/pos/sessions/${sessionId}/items/${itemId}`);

    const del = request('DELETE', `/api/pos/sessions/${sessionId}/items/${itemId}`, null, authHeaders(ctx), true);
    assertStep(del, 'remove item success', `DELETE /api/pos/sessions/${sessionId}/items/${itemId}`);

    const payStaff = request(
      'POST',
      `/api/pos/sessions/${sessionId}/pay`,
      JSON.stringify({ method: data.paymentMethod }),
      jsonHeaders(ctx),
      true
    );
    assertStep(payStaff, 'staff pay session success', `POST /api/pos/sessions/${sessionId}/pay`);
  });
}

export function menuAndProduct(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };

  group('menu and product search', function () {
    const term = vuPick(SEARCH_TERMS, 1);

    const m = request('GET', `/api/pos/public/menu?tenantId=${encodeURIComponent(data.tenantId)}`, null, publicHeaders(ctx), true);
    assertStep(m, 'public menu success', 'GET /api/pos/public/menu');

    const c = request('GET', '/api/categories', null, authHeaders(ctx), true);
    assertStep(c, 'categories success', 'GET /api/categories');

    const p = request('GET', `/api/products?page=0&size=50&name=${encodeURIComponent(term)}`, null, authHeaders(ctx), true);
    assertStep(p, 'products success', 'GET /api/products');
  });
}

export function transactionFlow(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const opsTableIds = Array.isArray(data.opsTableIds) && data.opsTableIds.length ? data.opsTableIds : data.tableIds;

  group('transaction flow', function () {
    const tableId = pickAvailableTable(ctx, opsTableIds) || vuPick(opsTableIds, 4);
    const productId = Number(vuPick(data.productIds, 0));

    const opened = request(
      'POST',
      '/api/pos/sessions',
      JSON.stringify({ tableId, note: 'k6 transaction flow' }),
      jsonHeaders(ctx),
      true
    );
    assertStep(opened, 'transaction open session success', 'POST /api/pos/sessions');
    if (!opened.ok || !opened.data || !opened.data.sessionId) return;

    const sessionId = opened.data.sessionId;

    const add = request(
      'POST',
      `/api/pos/sessions/${sessionId}/items`,
      JSON.stringify({ items: [{ productId, quantity: 2 }] }),
      jsonHeaders(ctx),
      true
    );
    assertStep(add, 'transaction add item success', `POST /api/pos/sessions/${sessionId}/items`);
    if (!add.ok) return;

    const requestBill = request('POST', `/api/pos/public/sessions/${sessionId}/request-payment`, null, publicHeaders(ctx), true);
    assertStep(requestBill, 'request payment success', `POST /api/pos/public/sessions/${sessionId}/request-payment`);

    const pay = request(
      'POST',
      `/api/pos/sessions/${sessionId}/pay`,
      JSON.stringify({ method: data.paymentMethod }),
      jsonHeaders(ctx),
      true
    );
    assertStep(pay, 'pay session success', `POST /api/pos/sessions/${sessionId}/pay`);
  });
}

export function groupOrderFlow(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const sharedSessionIds = Array.isArray(data.sharedSessionIds) && data.sharedSessionIds.length
    ? data.sharedSessionIds
    : [data.sharedSessionId];
  const sessionId = String(sharedSessionIds[(Math.max(1, exec.vu.idInTest) - 1) % sharedSessionIds.length]);
  const productId = Number(vuPick(data.productIds, exec.vu.idInTest));
  const quantity = 1 + (exec.vu.idInTest % 2);

  group('group order flow', function () {
    // Simulate customer decision time to avoid unrealistically synchronized writes.
    sleep(0.2 + Math.random() * 0.8);

    const addRes = request(
      'POST',
      `/api/pos/sessions/${sessionId}/items`,
      JSON.stringify({ items: [{ productId, quantity }] }),
      jsonHeaders(ctx),
      true
    );
    assertStep(addRes, 'group add items success', `POST /api/pos/sessions/${sessionId}/items`);

    const detail = getSessionDetail(ctx, sessionId);
    assertStep(detail, 'group session detail success', `GET /api/pos/sessions/${sessionId}`);
  });
}

export function concurrentTableOperations(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const sharedSessionIds = Array.isArray(data.sharedSessionIds) && data.sharedSessionIds.length
    ? data.sharedSessionIds
    : [data.sharedSessionId];

  // Group VUs by table-size so 4-5 users hit the same session concurrently.
  const vuIndex = Math.max(0, exec.vu.idInTest - 1);
  const groupIndex = Math.floor(vuIndex / GROUP_SIZE_TARGET);
  const concurrentSessionId = String(sharedSessionIds[groupIndex % sharedSessionIds.length]);
  const vuId = exec.vu.idInTest;

  group('concurrent table operations', function () {
    // Random stagger to simulate realistic timing (4-5 customers arriving at table)
    sleep(Math.random() * 0.3);

    // Get current session detail to find existing items
    const sessionDetail = getSessionDetail(ctx, concurrentSessionId);
    if (!sessionDetail.ok) {
      businessErrorRate.add(1, { path: 'concurrent_ops', reason: 'get_session_failed' });
      return;
    }

    // Find a pending item ID if any exist
    const existingItemId = findPendingItemId(sessionDetail.data);
    const operationRoll = Math.random();

    // Operation 1: One customer adds items (majority of customers perform this)
    if (!existingItemId || operationRoll < 0.6) {
      const productId = Number(vuPick(data.productIds, vuId));
      const addQty = 1 + (vuId % 3);

      const addRes = request(
        'POST',
        `/api/pos/sessions/${concurrentSessionId}/items`,
        JSON.stringify({ items: [{ productId, quantity: addQty }] }),
        jsonHeaders(ctx),
        true
      );
      assertStep(addRes, 'concurrent add items success', `POST /api/pos/sessions/${concurrentSessionId}/items`);

      // After adding, immediately get detail to see what was added
      sleep(0.05 + Math.random() * 0.1);
      const detailAfterAdd = getSessionDetail(ctx, concurrentSessionId);
      assertStep(detailAfterAdd, 'get session detail after add', `GET /api/pos/sessions/${concurrentSessionId}`);
    }
    // Operation 2: Another customer updates quantity of existing item
    else if (existingItemId && operationRoll < 0.85) {
      const newQty = 2 + (vuId % 5);

      const updateRes = request(
        'PATCH',
        `/api/pos/sessions/${concurrentSessionId}/items/${existingItemId}`,
        JSON.stringify({ quantity: newQty }),
        jsonHeaders(ctx),
        true
      );
      assertStep(updateRes, 'concurrent update quantity success', `PATCH /api/pos/sessions/${concurrentSessionId}/items/${existingItemId}`);

      sleep(0.05 + Math.random() * 0.1);
      const detailAfterUpdate = getSessionDetail(ctx, concurrentSessionId);
      assertStep(detailAfterUpdate, 'get session detail after update', `GET /api/pos/sessions/${concurrentSessionId}`);
    }
    // Operation 3: Another customer deletes item (less frequent)
    else if (existingItemId) {
      const deleteRes = request(
        'DELETE',
        `/api/pos/sessions/${concurrentSessionId}/items/${existingItemId}`,
        null,
        authHeaders(ctx),
        true
      );
      assertStep(deleteRes, 'concurrent delete item success', `DELETE /api/pos/sessions/${concurrentSessionId}/items/${existingItemId}`);

      sleep(0.05 + Math.random() * 0.1);
      const detailAfterDelete = getSessionDetail(ctx, concurrentSessionId);
      assertStep(detailAfterDelete, 'get session detail after delete', `GET /api/pos/sessions/${concurrentSessionId}`);
    }

    // Final check: verify session state after all concurrent operations
    const finalDetail = getSessionDetail(ctx, concurrentSessionId);
    assertStep(finalDetail, 'final session state check', `GET /api/pos/sessions/${concurrentSessionId}`);
  });
}


export function fullPosFlow(data) {
  const ctx = { token: data.token, tenantId: data.tenantId };
  const tableIds = Array.isArray(data.opsTableIds) && data.opsTableIds.length ? data.opsTableIds : data.tableIds;

  group('full pos flow', function () {
    const tableId = dedicatedVuTableId(tableIds, 0) || pickAvailableTable(ctx, tableIds) || vuPick(tableIds, 0);
    const p1 = Number(vuPick(data.productIds, 0));
    const p2 = Number(vuPick(data.productIds, 1));
    const term = vuPick(SEARCH_TERMS, 0);

    const menu = request('GET', `/api/pos/public/menu?tenantId=${encodeURIComponent(data.tenantId)}`, null, publicHeaders(ctx), true);
    assertStep(menu, 'full flow public menu success', 'GET /api/pos/public/menu');

    const products = request('GET', `/api/products?page=0&size=20&name=${encodeURIComponent(term)}`, null, authHeaders(ctx), true);
    assertStep(products, 'full flow search products success', 'GET /api/products');

    const openRes = request(
      'POST',
      '/api/pos/sessions',
      JSON.stringify({ tableId, note: `k6 full flow vu=${exec.vu.idInTest}` }),
      jsonHeaders(ctx),
      true
    );
    assertStep(openRes, 'full flow open session success', 'POST /api/pos/sessions');
    if (!openRes.ok || !openRes.data || !openRes.data.sessionId) return;

    sessionOpenCount.add(1);
    const sessionId = openRes.data.sessionId;

    const addRes = request(
      'POST',
      `/api/pos/sessions/${sessionId}/items`,
      JSON.stringify({ items: [{ productId: p1, quantity: 1 }, { productId: p2, quantity: 2 }] }),
      jsonHeaders(ctx),
      true
    );
    assertStep(addRes, 'full flow add items success', `POST /api/pos/sessions/${sessionId}/items`);
    if (!addRes.ok) return;

    const detail = getSessionDetail(ctx, sessionId);
    assertStep(detail, 'full flow get session detail success', `GET /api/pos/sessions/${sessionId}`);
    if (detail.ok) {
      const itemId = findPendingItemId(detail.data);
      if (itemId) {
        const upd = request(
          'PATCH',
          `/api/pos/sessions/${sessionId}/items/${itemId}`,
          JSON.stringify({ quantity: 3 }),
          jsonHeaders(ctx),
          true
        );
        assertStep(upd, 'full flow update quantity success', `PATCH /api/pos/sessions/${sessionId}/items/${itemId}`);
      }
    }

    const requestBill = request('POST', `/api/pos/public/sessions/${sessionId}/request-payment`, null, publicHeaders(ctx), true);
    assertStep(requestBill, 'full flow request payment success', `POST /api/pos/public/sessions/${sessionId}/request-payment`);

    const payRes = request(
      'POST',
      `/api/pos/sessions/${sessionId}/pay`,
      JSON.stringify({ method: data.paymentMethod }),
      jsonHeaders(ctx),
      true
    );
    assertStep(payRes, 'full flow pay session success', `POST /api/pos/sessions/${sessionId}/pay`);
    if (payRes.ok) sessionCloseCount.add(1);
  });
}

export default function () {
  fail('Use named scenarios only.');
}
