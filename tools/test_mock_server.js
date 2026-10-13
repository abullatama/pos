const http = require('http');
const { server, mockResponses } = require('./mock_server');

const PORT = 8089;

server.listen(PORT, async () => {
  console.log(`Testing Mock Server on port ${PORT}...`);
  const endpoints = [
    { method: 'POST', path: '/v1/devices/register', body: { registration_code: '123456', device_id: 'dev-1', name: 'Tablet 1' }, expectStatus: 202 },
    { method: 'GET', path: '/v1/devices/me', expectStatus: 200 },
    { method: 'POST', path: '/v1/devices/heartbeat', body: { app_id: 'cafe', app_version: '0.1.0' }, expectStatus: 204 },
    { method: 'POST', path: '/v1/auth/pin-login', body: { user_id: 'u-1', pin: '123456' }, expectStatus: 200 },
    { method: 'POST', path: '/v1/auth/refresh', body: { refresh_token: 'tok' }, expectStatus: 200 },
    { method: 'GET', path: '/v1/bootstrap', expectStatus: 200 },
    { method: 'GET', path: '/v1/changes', expectStatus: 200 },
    { method: 'GET', path: '/v1/customers/search?q=budi', expectStatus: 200 },
    { method: 'POST', path: '/v1/sync/bundles', body: { bundles: [] }, expectStatus: 200 },
    { method: 'GET', path: '/v1/shifts/current', expectStatus: 200 },
    { method: 'POST', path: '/v1/shifts/00000000-0000-4000-8000-000000000001/force-close', body: { note: 'test' }, expectStatus: 200 },
    { method: 'GET', path: '/v1/orders/00000000-0000-4000-8000-000000000011/notification-status', expectStatus: 200 },
    { method: 'GET', path: '/v1/config', expectStatus: 200 },
    { method: 'GET', path: '/v1/time', expectStatus: 200 },
    { method: 'POST', path: '/v1/files', body: {}, expectStatus: 201 }
  ];

  let passed = 0;
  for (const ep of endpoints) {
    await new Promise((resolve) => {
      const payload = ep.body ? JSON.stringify(ep.body) : '';
      const req = http.request({
        hostname: '127.0.0.1',
        port: PORT,
        path: ep.path,
        method: ep.method,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload)
        }
      }, (res) => {
        let data = '';
        res.on('data', chunk => { data += chunk; });
        res.on('end', () => {
          if (res.statusCode === ep.expectStatus) {
            passed++;
            console.log(`[PASS] ${ep.method} ${ep.path} -> ${res.statusCode}`);
          } else {
            console.error(`[FAIL] ${ep.method} ${ep.path} -> expected ${ep.expectStatus}, got ${res.statusCode} (${data})`);
          }
          resolve();
        });
      });
      req.on('error', (err) => {
        console.error(`[ERR] ${ep.method} ${ep.path}:`, err.message);
        resolve();
      });
      if (payload) req.write(payload);
      req.end();
    });
  }

  server.close(() => {
    console.log(`\nMock Server Test Results: ${passed} / ${endpoints.length} endpoints passed.`);
    if (passed === endpoints.length) {
      console.log('F05 MOCK SERVER STATUS: SUCCESS');
      process.exit(0);
    } else {
      process.exit(1);
    }
  });
});
