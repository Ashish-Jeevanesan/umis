/**
 * PO Email & PDF Generator - Lightweight Test Utility Server.
 */

const path = require('path');
const dotenv = require('dotenv');
const express = require('express');

dotenv.config({ path: path.join(__dirname, '.env') });

const { validatePoData } = require('./services/validation-service');
const { generatePoPdf } = require('./services/pdf-service');
const { sendPoEmail } = require('./services/email-service');

const app = express();
const DEFAULT_PORT = 3050;
const configuredPort = parseInt(process.env.PO_GENERATOR_PORT || process.env.PORT || DEFAULT_PORT, 10);
const PORT = configuredPort === 8080 ? DEFAULT_PORT : configuredPort;

app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

/**
 * Endpoint to preview the generated PDF in-browser.
 */
app.post('/api/preview-pdf', async (request, response) => {
  const validationResult = validatePoData(request.body);
  if (!validationResult.isValid) {
    return response.status(400).json({ success: false, errors: validationResult.errors });
  }

  try {
    const poData = validationResult.data;
    const pdfBuffer = await generatePoPdf(poData);
    const filename = `PO_${poData.sanitizedPoNumber}.pdf`;

    response.setHeader('Content-Type', 'application/pdf');
    response.setHeader('Content-Disposition', `inline; filename="${filename}"`);
    return response.send(pdfBuffer);
  } catch (error) {
    return response.status(500).json({
      success: false,
      errors: ['Failed to generate PDF document: ' + error.message]
    });
  }
});

/**
 * Endpoint to validate, generate PDF, and send email via SMTP.
 */
app.post('/api/send-po-email', async (request, response) => {
  const validationResult = validatePoData(request.body);
  if (!validationResult.isValid) {
    return response.status(400).json({ success: false, errors: validationResult.errors });
  }

  try {
    const poData = validationResult.data;
    const pdfBuffer = await generatePoPdf(poData);
    const sendResult = await sendPoEmail(poData, pdfBuffer);

    return response.json({
      success: true,
      message: `Email successfully sent to ${poData.toEmail} with attachment ${sendResult.filename}`,
      messageId: sendResult.messageId,
      filename: sendResult.filename,
      accepted: sendResult.accepted
    });
  } catch (error) {
    return response.status(500).json({
      success: false,
      errors: ['Email delivery failed: ' + error.message]
    });
  }
});

/**
 * Endpoint to check SMTP configuration readiness (without leaking credentials).
 */
app.get('/api/smtp-status', (request, response) => {
  const host = process.env.SMTP_HOST || '';
  const username = process.env.SMTP_USERNAME || '';
  const port = process.env.SMTP_PORT || '587';
  const fromEmail = process.env.SMTP_FROM_EMAIL || username;
  const isConfigured = Boolean(host && username && process.env.SMTP_PASSWORD);

  return response.json({
    configured: isConfigured,
    host: host,
    port: port,
    fromEmail: fromEmail,
    fromName: process.env.SMTP_FROM_NAME || 'Default Sender'
  });
});

app.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`PO Email Generator utility running at http://localhost:${PORT}`);
});
