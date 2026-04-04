import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const RAW_BASE_URL = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/$/, '');
const API_BASE_URL = RAW_BASE_URL.endsWith('/api') ? RAW_BASE_URL : `${RAW_BASE_URL}/api`;
const BASE_URL = API_BASE_URL;
const PUBLIC_URL = `${API_BASE_URL}/public`;
const CUSTOMERS_URL = `${API_BASE_URL}/pos/public`;

let authToken = '';
let tenantId = '';
let userId = '';
let productId = 0;
let categoryId = 1;
let sessionId = 0;
let tableId = '';
let accessKeyId = '';
let customerSessionId = 0;

const errorCounter = new Counter('errors');
const successCounter = new Counter('success');

export const options = {
  stages: [
    { duration: '1m', target: 200 },
    { duration: '2m', target: 500 },
    { duration: '2m', target: 1000 },
    { duration: '2m', target: 500 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.1'],
  },
};

function generateUsername() {
  return `user_${Math.random().toString(36).substring(7)}`;
}

function generatePassword() {
  return `Pass@${Math.random().toString(36).substring(7)}123`;
}

function generateTenantName() {
  const names = ['Restaurant', 'Cafe', 'Bistro', 'Diner', 'Grill'];
  return names[Math.floor(Math.random() * names.length)] + `_${Math.random().toString(36).substring(7)}`;
}

function generateProductName() {
  const products = ['Burger', 'Pizza', 'Salad', 'Coffee', 'Steak', 'Pasta', 'Noodles', 'Rice Bowl'];
  return products[Math.floor(Math.random() * products.length)];
}

function checkResponse(res, name, expectedStatus = 200) {
  let success = false;
  
  if (res.status === expectedStatus) {
    success = true;
    successCounter.add(1);
  } else {
    errorCounter.add(1);
    console.log(`[ERROR-${name}] Expected ${expectedStatus} but got ${res.status}: ${res.body}`);
  }
  
  check(res, {
    [`${name} status ${expectedStatus}`]: (r) => r.status === expectedStatus,
    [`${name} response time < 1000ms`]: (r) => r.timings.duration < 1000,
  });
  
  return success;
}

function authFlow() {
  console.log('[AUTH] Starting auth flow');
  
  if (authToken) {
    return { token: authToken };
  }
  
  const username = generateUsername();
  const password = generatePassword();
  
  const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    username: username,
    email: `${username}@test.com`,
    password: password,
    fullName: 'Test User',
  }), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  });
  
  if (!checkResponse(registerRes, 'Register', 200)) {
    console.log('[WARN] Register failed, waiting before retry');
    sleep(1);
    return null;
  }
  
  sleep(0.3);
  
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: username,
    password: password,
  }), {
    headers: { 'Content-Type': 'application/json' },
  });
  
  if (!checkResponse(loginRes, 'Login', 200)) {
    return null;
  }
  
  const body = loginRes.json();
  const data = body.data || {};
  authToken = data.accessToken || '';
  
  if (!authToken) {
    console.log('[ERROR] No token in login response');
    return null;
  }
  
  return { username, password, token: authToken };
}

function tenantFlow() {
  console.log('[TENANT] Starting tenant flow');
  
  if (tenantId) {
    return { tenantId };
  }
  
  if (!authToken) {
    console.log('[WARN] No auth token for tenant flow');
    return null;
  }
  
  const tenantName = generateTenantName();
  
  const createPayload = {
    name: tenantName,
    address: '123 Test Street, Test City',
    logo: http.file('dummy', 'logo.txt', 'text/plain'),
  };

  const createRes = http.post(`${BASE_URL}/tenants`, createPayload, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
    },
  });
  
  if (!checkResponse(createRes, 'CreateTenant', 200)) {
    return null;
  }
  
  const body = createRes.json();
  const data = body.data || {};
  tenantId = data.id || '';
  
  if (!tenantId) {
    console.log('[ERROR] No tenant ID returned');
    return null;
  }
  
  sleep(0.5);
  
  const getRes = http.get(`${BASE_URL}/tenants/${tenantId}`, {
    headers: { 'Authorization': `Bearer ${authToken}` },
  });
  
  checkResponse(getRes, 'GetTenant', 200);
  
  return { tenantId, name: tenantName };
}

function categoryFlow() {
  console.log('[CATEGORY] Starting category flow');
  
  if (!authToken) return null;
  
  const getRes = http.get(`${BASE_URL}/categories`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  if (!checkResponse(getRes, 'GetCategories', 200)) {
    return null;
  }
  
  let currentCategoryId = null;

  const listBody = getRes.json();
  const listData = listBody.data || [];
  if (Array.isArray(listData) && listData.length > 0) {
    currentCategoryId = listData[0].id;
  }
  
  if (!currentCategoryId) {
    console.log('[CATEGORY] Not found, creating one...');
    const categoryName = `Category_${Math.random().toString(36).substring(7)}`;
    const createRes = http.post(
      `${BASE_URL}/categories?name=${encodeURIComponent(categoryName)}&order=1`,
      null,
      {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'X-Tenant-ID': tenantId,
        },
      }
    );
    
    if (!checkResponse(createRes, 'CreateCategory', 201) && !checkResponse(createRes, 'CreateCategory', 200)) {
      return null;
    }
    const body = createRes.json();
    const data = body.data || {};
    currentCategoryId = data.id || null;
  }
  
  if (!currentCategoryId) {
    console.log('[ERROR] No categoryId available after retrieving/creating categories');
    return null;
  }
  
  categoryId = currentCategoryId;
  return { categoryId };
}

function productFlow() {
  console.log('[PRODUCT] Starting product flow');
  
  if (!authToken || !tenantId) return null;
  
  const productPayload = {
    categoryId: `${categoryId || 1}`,
    name: generateProductName(),
    price: `${Math.floor(Math.random() * 500000) + 10000}`,
    description: 'Test product',
    images: http.file('dummy product image', 'product.txt', 'text/plain'),
  };

  const createRes = http.post(
    `${BASE_URL}/products`,
    productPayload,
    {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Tenant-ID': tenantId,
      },
    }
  );
  
  if (!checkResponse(createRes, 'CreateProduct', 200)) {
    return null;
  }
  
  const body = createRes.json();
  const data = body.data || {};
  productId = data.id || 0;
  
  sleep(0.3);
  
  const getRes = http.get(`${BASE_URL}/products`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { page: 0, size: 20 },
  });
  
  checkResponse(getRes, 'GetProducts', 200);
  
  sleep(0.1);
  
  if (productId) {
    const detailRes = http.get(`${BASE_URL}/products/${productId}`, {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Tenant-ID': tenantId,
      },
    });
    
    checkResponse(detailRes, 'GetProductDetail', 200);
  }
  
  return { productId, categoryId };
}

function tableFlow() {
  console.log('[TABLE] Starting table flow');
  
  if (!authToken || !tenantId) return null;
  
  const createRes = http.post(
    `${BASE_URL}/pos/tables?name=Table_${Math.floor(Math.random() * 100) + 1}`,
    null,
    {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Tenant-ID': tenantId,
      },
    }
  );
  
  if (!checkResponse(createRes, 'CreateTable', 200)) {
    return null;
  }
  
  const body = createRes.json();
  const data = body.data || {};
  tableId = data.id || '';
  
  sleep(0.3);
  
  const getRes = http.get(`${BASE_URL}/pos/tables`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  checkResponse(getRes, 'GetTables', 200);
  
  return { tableId };
}

function posSessionFlow() {
  console.log('[SESSION] Starting POS session flow');
  
  if (!authToken || !tableId) return null;
  
  const createRes = http.post(
    `${BASE_URL}/pos/sessions`,
    JSON.stringify({
      tableId: tableId,
      note: 'Test session from k6',
    }),
    {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Tenant-ID': tenantId,
        'Content-Type': 'application/json',
      },
    }
  );
  
  if (!checkResponse(createRes, 'CreateSession', 200)) {
    return null;
  }
  
  const body = createRes.json();
  const data = body.data || {};
  sessionId = data.sessionId || 0;
  
  sleep(0.1);
  
  const getPendingRes = http.get(`${BASE_URL}/pos/sessions/pending`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { page: 0, size: 20 },
  });
  
  checkResponse(getPendingRes, 'GetPendingSessions', 200);
  
  sleep(0.1);
  
  const getActiveRes = http.get(`${BASE_URL}/pos/sessions/active`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { page: 0, size: 20 },
  });
  
  checkResponse(getActiveRes, 'GetActiveSessions', 200);
  
  return { sessionId };
}

function posSessionItemsFlow() {
  console.log('[SESSION-ITEMS] Starting session items flow');
  
  if (!authToken || !sessionId || !productId) return null;
  
  const addRes = http.post(
    `${BASE_URL}/pos/sessions/${sessionId}/items`,
    JSON.stringify({
      items: [
        {
          productId: productId,
          quantity: Math.floor(Math.random() * 3) + 1,
          note: 'No spicy',
        },
      ],
    }),
    {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Tenant-ID': tenantId,
        'Content-Type': 'application/json',
      },
    }
  );
  
  checkResponse(addRes, 'AddSessionItems', 200);
  
  sleep(0.1);
  
  const getRes = http.get(`${BASE_URL}/pos/sessions/${sessionId}`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  checkResponse(getRes, 'GetSessionDetail', 200);
  
  return { sessionId };
}

function customerOrderFlow() {
  console.log('[CUSTOMER-ORDER] Starting customer order flow');
  
  if (!tenantId || !tableId || !productId) {
    console.log(`[WARN] Missing params for customer order: tenantId=${tenantId}, tableId=${tableId}, productId=${productId}`);
    return null;
  }
  
  const menuRes = http.get(`${CUSTOMERS_URL}/menu`, {
    params: { tenantId: tenantId },
  });
  
  checkResponse(menuRes, 'GetPublicMenu', 200);
  
  sleep(0.1);
  
  const infoRes = http.get(`${CUSTOMERS_URL}/info/${tableId}`);
  
  checkResponse(infoRes, 'GetTableInfo', 200);
  
  sleep(0.1);
  
  const orderRes = http.post(
    `${CUSTOMERS_URL}/sessions`,
    JSON.stringify({
      tableId: tableId,
      items: [
        {
          productId: productId,
          quantity: Math.floor(Math.random() * 2) + 1,
          note: 'Customer note',
        },
      ],
      customerNote: 'Please serve quickly',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );
  
  if (orderRes.status === 201 || orderRes.status === 200) {
    const body = orderRes.json();
    const data = body.data || {};
    customerSessionId = data.sessionId || 0;
    checkResponse(orderRes, 'CustomerOrder', orderRes.status);
  } else {
    checkResponse(orderRes, 'CustomerOrder', 201);
  }
  
  sleep(0.1);
  
  if (customerSessionId) {
    const getRes = http.get(`${CUSTOMERS_URL}/sessions/${customerSessionId}`);
    checkResponse(getRes, 'GetCustomerOrder', 200);
  }
  
  return { customerSessionId };
}

function paymentFlow() {
  console.log('[PAYMENT] Starting payment flow');
  
  if (!tenantId) return null;
  
  const methodRes = http.get(`${PUBLIC_URL}/payment/methods/${tenantId}`);
  checkResponse(methodRes, 'GetPaymentMethods', 200);
  
  return null;
}

function notificationFlow() {
  console.log('[NOTIFICATION] Starting notification flow');
  
  if (!authToken) return null;
  
  const getRes = http.get(`${BASE_URL}/notifications`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { page: 0, size: 20 },
  });
  
  checkResponse(getRes, 'GetNotifications', 200);
  
  sleep(0.1);
  
  const unreadRes = http.get(`${BASE_URL}/notifications/unread`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  checkResponse(unreadRes, 'GetUnreadNotifications', 200);
  
  sleep(0.1);
  
  const countRes = http.get(`${BASE_URL}/notifications/unread/count`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  checkResponse(countRes, 'GetNotificationCount', 200);
  
  return null;
}

function reportingFlow() {
  console.log('[REPORTING] Starting reporting flow');
  
  if (!authToken) return null;
  
  const revenueRes = http.get(`${BASE_URL}/reports/revenue`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { from: '2024-01-01', to: '2024-12-31' },
  });
  
  checkResponse(revenueRes, 'GetRevenueReport', 200);
  
  sleep(0.1);
  
  const topProductRes = http.get(`${BASE_URL}/reports/top-products`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
    params: { limit: 5 },
  });
  
  checkResponse(topProductRes, 'GetTopProducts', 200);
  
  sleep(0.1);
  
  const peakHoursRes = http.get(`${BASE_URL}/reports/peak-hours`, {
    headers: {
      'Authorization': `Bearer ${authToken}`,
      'X-Tenant-ID': tenantId,
    },
  });
  
  checkResponse(peakHoursRes, 'GetPeakHours', 200);
  
  return null;
}

export function integrationTest() {
  console.log('========== [INTEGRATION TEST] Complete User Journey ==============');
  
  authFlow();
  sleep(0.5);
  
  tenantFlow();
  sleep(0.5);
  
  categoryFlow();
  sleep(0.3);
  
  productFlow();
  sleep(0.5);
  
  tableFlow();
  sleep(0.3);
  
  posSessionFlow();
  sleep(0.3);
  
  posSessionItemsFlow();
  sleep(0.3);
  
  customerOrderFlow();
  sleep(0.3);
  
  paymentFlow();
  sleep(0.3);
}

export function functionalTest() {
  console.log('========== [FUNCTIONAL TEST] All Endpoints ======================');
  
  authFlow();
  sleep(0.3);
  
  if (authToken) {
    tenantFlow();
    sleep(0.3);
    
    categoryFlow();
    sleep(0.2);
    
    productFlow();
    sleep(0.3);
    
    tableFlow();
    sleep(0.2);
    
    posSessionFlow();
    sleep(0.2);
    
    posSessionItemsFlow();
    sleep(0.2);
    
    notificationFlow();
    sleep(0.2);
    
    reportingFlow();
    sleep(0.2);
  }
  
  customerOrderFlow();
  sleep(0.2);
  
  paymentFlow();
  sleep(0.2);
}

export function stressTest() {
  console.log('========== [STRESS TEST] Random API Calls ======================');
  
  const scenario = Math.random();
  
  if (scenario < 0.2) {
    authFlow();
  } else if (scenario < 0.4) {
    if (!authToken) authFlow();
    if (authToken) {
      tenantFlow();
      categoryFlow();
      productFlow();
    }
  } else if (scenario < 0.6) {
    if (!authToken) authFlow();
    if (authToken && !tenantId) tenantFlow();
    if (authToken && tenantId) {
      tableFlow();
      posSessionFlow();
    }
  } else if (scenario < 0.8) {
    customerOrderFlow();
  } else {
    paymentFlow();
  }
  
  sleep(Math.random() * 1.5);
}

export default function main() {
  const scenario = __ENV.SCENARIO || 'stress';
  
  if (scenario === 'integration') {
    integrationTest();
  } else if (scenario === 'functional') {
    functionalTest();
  } else {
    stressTest();
  }
}
