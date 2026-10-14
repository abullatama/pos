#!/usr/bin/env node
/**
 * Mock server HTTP untuk contracts/openapi.yaml v0.2.0
 * Melayani seluruh 15 endpoint API A3 dengan contoh respon yang sesuai skema.
 */
const http = require('http');
const url = require('url');

const PORT = process.env.PORT || 8080;

const seenBundles = new Map();

const mockResponses = {
  // 1. POST /devices/register
  'POST /devices/register': (req, res, body) => {
    res.writeHead(202, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'PENDING' }));
  },

  // 2. GET /devices/me
  'GET /devices/me': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ACTIVE',
      business_unit_id: 'bu-cafe-01',
      device_credential: 'mock-device-cred-secret-token'
    }));
  },

  // 3. POST /devices/heartbeat
  'POST /devices/heartbeat': (req, res) => {
    res.writeHead(204);
    res.end();
  },

  // 4. POST /auth/pin-login
  'POST /auth/pin-login': (req, res, body) => {
    const now = new Date().toISOString();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      access_token: 'mock-jwt-access-token-12345',
      refresh_token: 'mock-jwt-refresh-token-67890',
      expires_in: 3600,
      user: {
        id: 'u-kasir-1',
        name: 'Budi Santoso',
        role: 'kasir'
      },
      business_unit_id: 'bu-cafe-01',
      server_time: now
    }));
  },

  // 5. POST /auth/refresh
  'POST /auth/refresh': (req, res) => {
    const now = new Date().toISOString();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      access_token: 'mock-jwt-new-access-token',
      refresh_token: 'mock-jwt-new-refresh-token',
      expires_in: 3600,
      user: {
        id: 'u-kasir-1',
        name: 'Budi Santoso',
        role: 'kasir'
      },
      business_unit_id: 'bu-cafe-01',
      server_time: now
    }));
  },

  // 6. GET /bootstrap
  'GET /bootstrap': (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const cursor = parsedUrl.query.cursor;

    if (cursor === 'page-2') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({
        section: 'laundry_services',
        records: [
          { id: 'ls-reguler', name: 'Cuci Setrika Reguler', unit: 'kg', rate: 7000, active: true }
        ],
        next_cursor: null,
        done: true,
        delta_cursor: 'delta-100'
      }));
    }

    if (cursor === 'page-1' || parsedUrl.query.multipage === 'true') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({
        section: 'menu_items',
        records: [
          { id: 'menu-latte', name: 'Latte', price: 34000, category: 'Coffee', active: true }
        ],
        next_cursor: 'page-2',
        done: false,
        delta_cursor: null
      }));
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      section: 'menu_items',
      records: [
        { id: 'menu-latte', name: 'Latte', price: 34000, category: 'Coffee', active: true },
        { id: 'menu-croissant', name: 'Croissant', price: 28000, category: 'Pastry', active: true }
      ],
      next_cursor: null,
      done: true,
      delta_cursor: 'seq-100'
    }));
  },

  // 7. GET /changes
  'GET /changes': (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const cursor = parsedUrl.query.cursor;

    if (cursor === 'expired-cursor') {
      res.writeHead(410, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({
        code: 'CURSOR_EXPIRED',
        message: 'Cursor kedaluwarsa, silakan lakukan bootstrap ulang'
      }));
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      changes: [
        {
          table: 'menu_items',
          op: 'upsert',
          record: { id: 'menu-espresso', name: 'Espresso Single', price: 18000, category: 'Coffee', active: true }
        }
      ],
      next_cursor: 'delta-101',
      has_more: false
    }));
  },

  // 8. GET /customers/search
  'GET /customers/search': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      customers: [
        { id: '00000000-0000-4000-8000-000000000005', name: 'Budi Santoso', phone: '081234567890' }
      ]
    }));
  },

  // 9. POST /sync/bundles
  'POST /sync/bundles': (req, res, body) => {
    const now = new Date().toISOString();
    let bundles = [];
    try {
      const parsed = JSON.parse(body || '{}');
      bundles = parsed.bundles || [];
    } catch (_) {}

    const results = bundles.length > 0
      ? bundles.map(b => {
          if (seenBundles.has(b.bundle_id)) {
            return {
              bundle_id: b.bundle_id,
              status: 'DUPLICATE',
              server_received_at: seenBundles.get(b.bundle_id)
            };
          }
          seenBundles.set(b.bundle_id, now);
          return {
            bundle_id: b.bundle_id,
            status: 'ACCEPTED',
            server_received_at: now
          };
        })
      : [{
          bundle_id: '00000000-0000-4000-8000-000000000001',
          status: 'ACCEPTED',
          server_received_at: now
        }];

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      results,
      server_time: now
    }));
  },

  // 10. GET /shifts/current
  'GET /shifts/current': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      shift: {
        id: '00000000-0000-4000-8000-000000000001',
        status: 'open',
        opened_at: '2026-10-12T01:00:00Z',
        closed_at: null,
        opening_cash: 200000
      }
    }));
  },

  // 11. POST /shifts/{id}/force-close
  'POST /shifts/force-close': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      id: '00000000-0000-4000-8000-000000000001',
      status: 'force_closed',
      opened_at: '2026-10-12T01:00:00Z',
      closed_at: new Date().toISOString(),
      opening_cash: 200000
    }));
  },

  // 12. GET /orders/{id}/notification-status
  'GET /orders/notification-status': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'sent',
      last_error: null,
      sent_at: '2026-10-12T02:00:00Z'
    }));
  },

  // 13. GET /config
  'GET /config': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      settings: {
        store_name: 'AA Coffeeshop & Laundry',
        tax_rate: 10,
        service_charge_rate: 0
      },
      feature_flags: {},
      min_app_version: '0.1.0',
      labels: {
        qris_name: 'QRIS AA POS'
      }
    }));
  },

  // 14. GET /time
  'GET /time': (req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      server_time: new Date().toISOString()
    }));
  },

  // 15. POST /files
  'POST /files': (req, res) => {
    res.writeHead(201, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      file_id: '00000000-0000-4000-8000-000000000099'
    }));
  }
};

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  let pathname = parsedUrl.pathname;

  // Normalisasi prefix /v1 jika klien mengirim dengan /v1
  if (pathname.startsWith('/v1')) {
    pathname = pathname.substring(3);
  }

  // Handle path parameter regex
  let handlerKey = `${req.method} ${pathname}`;
  if (/^\/shifts\/[^/]+\/force-close$/.test(pathname)) {
    handlerKey = 'POST /shifts/force-close';
  } else if (/^\/orders\/[^/]+\/notification-status$/.test(pathname)) {
    handlerKey = 'GET /orders/notification-status';
  }

  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    const handler = mockResponses[handlerKey];
    if (handler) {
      handler(req, res, body);
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        code: 'NOT_FOUND',
        message: `Endpoint ${req.method} ${pathname} tidak ditemukan di mock server`,
        request_id: 'mock-req-404'
      }));
    }
  });
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`A3 OpenAPI v0 Mock Server running on http://127.0.0.1:${PORT}`);
    console.log(`Endpoint terdaftar: ${Object.keys(mockResponses).length}`);
  });
}

module.exports = { server, mockResponses };
