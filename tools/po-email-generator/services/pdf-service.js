/**
 * Professional Purchase Order PDF Generation Service using PDFKit.
 */

const PDFDocument = require('pdfkit');

/**
 * Generates a clean, professional Purchase Order PDF buffer.
 * @param {object} poData - Validated PO data.
 * @returns {Promise<Buffer>}
 */
function generatePoPdf(poData) {
  return new Promise((resolve, reject) => {
    try {
      const doc = new PDFDocument({
        size: 'A4',
        margin: 50,
        info: {
          Title: `Purchase Order ${poData.poNumber}`,
          Author: poData.customerName,
          Subject: `Purchase Order ${poData.poNumber}`
        }
      });

      const bufferChunks = [];
      doc.on('data', (chunk) => bufferChunks.push(chunk));
      doc.on('end', () => resolve(Buffer.concat(bufferChunks)));
      doc.on('error', (streamError) => reject(streamError));

      renderHeader(doc, poData);
      renderAddressSection(doc, poData);
      renderTermsSection(doc, poData);
      renderTable(doc, poData);
      renderTotals(doc, poData);
      renderNotes(doc, poData);

      doc.end();
    } catch (generationError) {
      reject(generationError);
    }
  });
}

/**
 * Renders the top header with PO title and key metadata.
 */
function renderHeader(doc, poData) {
  const startY = 50;
  doc.fillColor('#1A202C').fontSize(20).font('Helvetica-Bold')
     .text('PURCHASE ORDER', 50, startY);

  doc.fontSize(9).font('Helvetica').fillColor('#4A5568');
  doc.text(`PO Number: ${poData.poNumber}`, 360, startY, { align: 'right', width: 185 });
  doc.text(`PO Date: ${poData.poDate}`, 360, startY + 15, { align: 'right', width: 185 });
  doc.text(`Currency: ${poData.currency}`, 360, startY + 30, { align: 'right', width: 185 });

  doc.strokeColor('#CBD5E0').lineWidth(1)
     .moveTo(50, startY + 52).lineTo(545, startY + 52).stroke();
}

/**
 * Renders Bill-To and Ship-To address blocks.
 */
function renderAddressSection(doc, poData) {
  const sectionY = 115;

  doc.fontSize(10).font('Helvetica-Bold').fillColor('#2D3748');
  doc.text('BUYER / BILL TO:', 50, sectionY);
  doc.text('SHIP TO:', 310, sectionY);

  doc.fontSize(9).font('Helvetica').fillColor('#4A5568');
  const buyerDetails = [
    poData.customerName,
    poData.customerEmail,
    poData.billingAddress || 'Same as shipping'
  ].filter(Boolean).join('\n');
  doc.text(buyerDetails, 50, sectionY + 16, { width: 240 });

  const shipDetails = poData.shippingAddress || poData.billingAddress || 'Not specified';
  doc.text(shipDetails, 310, sectionY + 16, { width: 235 });
}

/**
 * Renders payment terms and delivery date info.
 */
function renderTermsSection(doc, poData) {
  const termsY = 185;
  doc.fillColor('#F7FAFC').rect(50, termsY, 495, 24).fill();
  doc.strokeColor('#E2E8F0').lineWidth(0.5).rect(50, termsY, 495, 24).stroke();

  doc.fontSize(8.5).font('Helvetica-Bold').fillColor('#4A5568');
  doc.text('PAYMENT TERMS:', 60, termsY + 7);
  doc.font('Helvetica').text(poData.paymentTerms || 'Standard', 160, termsY + 7);

  doc.font('Helvetica-Bold').text('DELIVERY DATE:', 320, termsY + 7);
  doc.font('Helvetica').text(poData.deliveryDate || 'Prompt / Standard', 415, termsY + 7);
}

/**
 * Renders the tabular items section.
 */
function renderTable(doc, poData) {
  let tableY = 225;

  doc.fillColor('#EDF2F7').rect(50, tableY, 495, 20).fill();
  doc.fontSize(8.5).font('Helvetica-Bold').fillColor('#2D3748');

  doc.text('Code', 55, tableY + 5, { width: 85 });
  doc.text('Description', 145, tableY + 5, { width: 195 });
  doc.text('Qty', 345, tableY + 5, { width: 40, align: 'right' });
  doc.text('Unit Price', 390, tableY + 5, { width: 70, align: 'right' });
  doc.text('Total', 465, tableY + 5, { width: 75, align: 'right' });

  tableY += 22;
  doc.font('Helvetica').fontSize(8.5).fillColor('#2D3748');

  poData.items.forEach((item, index) => {
    if (tableY > 700) {
      doc.addPage();
      tableY = 50;
    }

    if (index % 2 === 1) {
      doc.fillColor('#F7FAFC').rect(50, tableY - 2, 495, 18).fill();
      doc.fillColor('#2D3748');
    }

    doc.text(item.productCode, 55, tableY, { width: 85 });
    doc.text(item.productDescription, 145, tableY, { width: 195 });
    doc.text(String(item.quantity), 345, tableY, { width: 40, align: 'right' });
    doc.text(formatAmount(item.unitPrice, poData.currency), 390, tableY, { width: 70, align: 'right' });
    doc.text(formatAmount(item.totalPrice, poData.currency), 465, tableY, { width: 75, align: 'right' });

    tableY += 18;
  });

  doc.strokeColor('#E2E8F0').lineWidth(0.5).moveTo(50, tableY).lineTo(545, tableY).stroke();
  doc.y = tableY + 10;
}

/**
 * Renders the subtotal, tax and grand total summary box.
 */
function renderTotals(doc, poData) {
  const summaryY = Math.max(doc.y, 450);
  const labelX = 330;
  const valueX = 430;
  const valueWidth = 110;

  doc.fontSize(9).font('Helvetica').fillColor('#4A5568');
  doc.text('Subtotal:', labelX, summaryY, { width: 95, align: 'right' });
  doc.text(formatAmount(poData.subtotal, poData.currency), valueX, summaryY, { width: valueWidth, align: 'right' });

  if (poData.taxPercent > 0) {
    const taxY = summaryY + 16;
    doc.text(`Tax (${poData.taxPercent}%):`, labelX, taxY, { width: 95, align: 'right' });
    doc.text(formatAmount(poData.taxAmount, poData.currency), valueX, taxY, { width: valueWidth, align: 'right' });
  }

  const grandTotalY = summaryY + (poData.taxPercent > 0 ? 34 : 20);
  doc.strokeColor('#CBD5E0').lineWidth(1)
     .moveTo(330, grandTotalY - 4).lineTo(545, grandTotalY - 4).stroke();

  doc.fontSize(10).font('Helvetica-Bold').fillColor('#1A202C');
  doc.text('Grand Total:', labelX, grandTotalY, { width: 95, align: 'right' });
  doc.text(formatAmount(poData.grandTotal, poData.currency), valueX, grandTotalY,
    { width: valueWidth, align: 'right' });

  doc.y = grandTotalY + 25;
}

/**
 * Renders additional notes at the bottom.
 */
function renderNotes(doc, poData) {
  if (!poData.additionalNotes) {
    return;
  }
  const notesY = Math.max(doc.y, 560);
  doc.fontSize(8.5).font('Helvetica-Bold').fillColor('#2D3748').text('Notes & Instructions:', 50, notesY);
  doc.font('Helvetica').fillColor('#718096')
     .text(poData.additionalNotes, 50, notesY + 14, { width: 495, lineGap: 2 });
}

/**
 * Helper to format monetary values.
 */
function formatAmount(numericValue, currencyCode) {
  const formatted = Number(numericValue).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  return `${currencyCode} ${formatted}`;
}

module.exports = {
  generatePoPdf
};
