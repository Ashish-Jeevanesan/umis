/**
 * SMTP Email Dispatch Service using Nodemailer.
 */

const nodemailer = require('nodemailer');

/**
 * Creates and configures the Nodemailer transporter from environment variables.
 * @returns {object} Transporter instance
 */
function createTransporter() {
  const host = process.env.SMTP_HOST || 'smtp.gmail.com';
  const port = parseInt(process.env.SMTP_PORT || '587', 10);
  const user = process.env.SMTP_USERNAME || '';
  const pass = process.env.SMTP_PASSWORD || '';
  const secure = process.env.SMTP_SECURE === 'true' || port === 465;

  const transportOptions = {
    host: host,
    port: port,
    secure: secure,
    auth: user ? { user: user, pass: pass } : undefined,
    connectionTimeout: 15000,
    greetingTimeout: 15000,
    socketTimeout: 20000
  };

  return nodemailer.createTransport(transportOptions);
}

/**
 * Builds dynamic plain text email body from PO data.
 * @param {object} poData
 * @returns {string}
 */
function buildPlainTextBody(poData) {
  const formattedGrandTotal = Number(poData.grandTotal).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  const lines = [
    'Hello,',
    '',
    `Please find attached our purchase order ${poData.poNumber}.`,
    '',
    `Customer: ${poData.customerName}`,
    `PO Number: ${poData.poNumber}`,
    `PO Date: ${poData.poDate}`,
    poData.deliveryDate ? `Delivery Date: ${poData.deliveryDate}` : null,
    `Currency: ${poData.currency}`,
    `Total Amount: ${poData.currency} ${formattedGrandTotal}`,
    '',
    'Please review the attached purchase order document.',
    '',
    'Regards,',
    poData.senderName || poData.customerName
  ];

  return lines.filter((line) => line !== null).join('\n');
}

/**
 * Builds clean HTML email body from PO data.
 * @param {object} poData
 * @returns {string}
 */
function buildHtmlBody(poData) {
  const formattedGrandTotal = Number(poData.grandTotal).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  const sender = escapeHtml(poData.senderName || poData.customerName);
  const customer = escapeHtml(poData.customerName);
  const poNum = escapeHtml(poData.poNumber);
  const poDate = escapeHtml(poData.poDate);
  const delivery = escapeHtml(poData.deliveryDate || 'Standard');

  return `
    <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333333; max-width: 600px;">
      <p>Hello,</p>
      <p>Please find attached our purchase order <strong>${poNum}</strong>.</p>
      <table style="border-collapse: collapse; margin: 16px 0; width: 100%; max-width: 450px;">
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">Customer:</td><td style="padding: 4px 8px;">${customer}</td></tr>
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">PO Number:</td><td style="padding: 4px 8px;">${poNum}</td></tr>
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">PO Date:</td><td style="padding: 4px 8px;">${poDate}</td></tr>
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">Delivery Date:</td><td style="padding: 4px 8px;">${delivery}</td></tr>
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">Currency:</td><td style="padding: 4px 8px;">${poData.currency}</td></tr>
        <tr><td style="padding: 4px 8px; font-weight: bold; color: #555;">Total Amount:</td><td style="padding: 4px 8px; font-weight: bold; color: #0b69a3;">${poData.currency} ${formattedGrandTotal}</td></tr>
      </table>
      <p>Please review the attached purchase order document.</p>
      <p>Regards,<br><strong>${sender}</strong></p>
    </div>
  `;
}

/**
 * Sends the PO email with the PDF attached via SMTP.
 * @param {object} poData
 * @param {Buffer} pdfBuffer
 * @returns {Promise<object>}
 */
async function sendPoEmail(poData, pdfBuffer) {
  const fromEmail = process.env.SMTP_FROM_EMAIL || process.env.SMTP_USERNAME || 'noreply@example.com';
  const fromName = process.env.SMTP_FROM_NAME || poData.senderName || 'Purchase Order Utility';
  const attachmentFilename = `PO_${poData.sanitizedPoNumber}.pdf`;

  const mailOptions = {
    from: `"${fromName}" <${fromEmail}>`,
    to: poData.toEmail,
    subject: poData.subject,
    text: buildPlainTextBody(poData),
    html: buildHtmlBody(poData),
    attachments: [
      {
        filename: attachmentFilename,
        content: pdfBuffer,
        contentType: 'application/pdf'
      }
    ]
  };

  const transporter = createTransporter();
  const sendResult = await transporter.sendMail(mailOptions);

  return {
    messageId: sendResult.messageId,
    accepted: sendResult.accepted,
    rejected: sendResult.rejected,
    filename: attachmentFilename
  };
}

/**
 * Escapes special HTML characters to prevent XSS in email clients.
 */
function escapeHtml(unsafeText) {
  return String(unsafeText || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

module.exports = {
  sendPoEmail,
  buildPlainTextBody,
  buildHtmlBody
};
