/**
 * Automated Verification Test for PO Email & PDF Generator.
 */

const assert = require('assert');
const { validatePoData } = require('./services/validation-service');
const { generatePoPdf } = require('./services/pdf-service');
const { buildPlainTextBody, buildHtmlBody } = require('./services/email-service');

async function runTests() {
  // eslint-disable-next-line no-console
  console.log('--- Running PO Generator Verification Tests ---');

  // Test 1: Validation with valid input
  const sampleInput = {
    toEmail: 'test-recipient@example.com',
    subject: 'Purchase Order PO-1001 – ABC Medical',
    senderName: 'ABC Medical Supplies',
    poNumber: 'PO-1001/Rev-A#01',
    poDate: '2026-11-08',
    customerName: 'ABC Medical Hospital',
    currency: 'USD',
    paymentTerms: 'Net 30',
    deliveryDate: '2026-11-20',
    taxPercent: 5.0,
    items: [
      { productCode: 'MED-101', productDescription: 'Titanium Screws', quantity: 10, unitPrice: 150.00 },
      { productCode: 'MED-202', productDescription: 'Sterile Spinal Rods', quantity: 2, unitPrice: 300.00 }
    ]
  };

  const validationResult = validatePoData(sampleInput);
  assert.strictEqual(validationResult.isValid, true, 'Validation should succeed for valid input');
  assert.strictEqual(validationResult.data.subtotal, 2100.00, 'Subtotal calculation mismatch');
  assert.strictEqual(validationResult.data.taxAmount, 105.00, 'Tax calculation mismatch');
  assert.strictEqual(validationResult.data.grandTotal, 2205.00, 'Grand total calculation mismatch');
  assert.strictEqual(
    validationResult.data.sanitizedPoNumber,
    'PO-1001_Rev-A_01',
    'PO number should be sanitized for safe filenames'
  );
  // eslint-disable-next-line no-console
  console.log('✓ Validation and calculation test passed');

  // Test 2: Validation rejection on missing required fields
  const invalidResult = validatePoData({ toEmail: 'not-an-email', items: [] });
  assert.strictEqual(invalidResult.isValid, false, 'Validation should fail on invalid input');
  assert.ok(invalidResult.errors.length >= 3, 'Multiple validation errors expected');
  // eslint-disable-next-line no-console
  console.log('✓ Validation error handling test passed');

  // Test 3: Email body generation
  const plainBody = buildPlainTextBody(validationResult.data);
  const htmlBody = buildHtmlBody(validationResult.data);
  assert.ok(plainBody.includes('PO-1001/Rev-A#01'), 'Plain body must contain PO number');
  assert.ok(plainBody.includes('USD 2,205.00'), 'Plain body must contain formatted grand total');
  assert.ok(htmlBody.includes('ABC Medical Hospital'), 'HTML body must contain customer name');
  // eslint-disable-next-line no-console
  console.log('✓ Email body generation test passed');

  // Test 4: PDF generation
  const pdfBuffer = await generatePoPdf(validationResult.data);
  assert.ok(Buffer.isBuffer(pdfBuffer), 'PDF result must be a Buffer');
  assert.ok(pdfBuffer.length > 1000, 'PDF buffer must contain substantial content');
  const pdfHeader = pdfBuffer.subarray(0, 5).toString('ascii');
  assert.strictEqual(pdfHeader, '%PDF-', 'Buffer must start with valid PDF magic bytes (%PDF-)');
  // eslint-disable-next-line no-console
  console.log(`✓ PDF generation test passed (Generated valid PDF: ${pdfBuffer.length} bytes)`);

  // eslint-disable-next-line no-console
  console.log('\nAll PO Generator verification tests passed successfully!');
}

runTests().catch((testError) => {
  // eslint-disable-next-line no-console
  console.error('Test execution failed:', testError);
  process.exit(1);
});
