/**
 * Integration test for server endpoints.
 */

const http = require('http');

async function testServer() {
  process.env.PORT = '3050';
  // eslint-disable-next-line global-require
  require('./server.js');

  await new Promise((resolve) => setTimeout(resolve, 1000));

  // Test 1: GET /api/smtp-status
  const statusRes = await makeRequest({
    hostname: 'localhost',
    port: 3050,
    path: '/api/smtp-status',
    method: 'GET'
  });

  const statusJson = JSON.parse(statusRes.body);
  // eslint-disable-next-line no-console
  console.log('✓ /api/smtp-status responded:', statusJson);

  // Test 2: POST /api/preview-pdf
  const poPayload = JSON.stringify({
    toEmail: 'test@example.com',
    subject: 'Test Subject',
    poNumber: 'PO-TEST-1',
    customerName: 'Test Customer',
    currency: 'USD',
    items: [{ productCode: 'T1', productDescription: 'Item 1', quantity: 2, unitPrice: 50 }]
  });

  const previewRes = await makeRequest(
    {
      hostname: 'localhost',
      port: 3050,
      path: '/api/preview-pdf',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(poPayload)
      }
    },
    poPayload
  );

  // eslint-disable-next-line no-console
  console.log(
    `✓ /api/preview-pdf responded with HTTP ${previewRes.statusCode}, Content-Type: ${previewRes.headers['content-type']}, length: ${previewRes.bodyBuffer.length} bytes`
  );

  if (previewRes.statusCode === 200 && previewRes.headers['content-type'] === 'application/pdf') {
    // eslint-disable-next-line no-console
    console.log('✓ All HTTP endpoints verified successfully!');
    process.exit(0);
  } else {
    // eslint-disable-next-line no-console
    console.error('Endpoints check failed');
    process.exit(1);
  }
}

function makeRequest(options, postData) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const fullBuffer = Buffer.concat(chunks);
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          bodyBuffer: fullBuffer,
          body: fullBuffer.toString('utf8')
        });
      });
    });
    req.on('error', (err) => reject(err));
    if (postData) {
      req.write(postData);
    }
    req.end();
  });
}

testServer().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});
