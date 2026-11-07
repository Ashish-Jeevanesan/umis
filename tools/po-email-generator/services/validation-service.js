/**
 * Validation and normalization service for Purchase Order data.
 */

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Validates and normalizes PO request data.
 * @param {object} inputPayload - Raw input payload from client.
 * @returns {{ isValid: boolean, errors: string[], data?: object }}
 */
function validatePoData(inputPayload) {
  const validationErrors = [];

  if (!inputPayload || typeof inputPayload !== 'object') {
    return { isValid: false, errors: ['Request body must be a valid JSON object.'] };
  }

  const toEmail = String(inputPayload.toEmail || '').trim();
  if (!toEmail || !EMAIL_REGEX.test(toEmail)) {
    validationErrors.push('A valid recipient email address (toEmail) is required.');
  }

  const subject = String(inputPayload.subject || '').trim();
  if (!subject) {
    validationErrors.push('An email subject is required.');
  }

  const poNumber = String(inputPayload.poNumber || '').trim();
  if (!poNumber) {
    validationErrors.push('A Purchase Order number (poNumber) is required.');
  }

  const customerName = String(inputPayload.customerName || '').trim();
  if (!customerName) {
    validationErrors.push('Customer name is required.');
  }

  const poDate = String(inputPayload.poDate || '').trim() || new Date().toISOString().split('T')[0];
  const items = Array.isArray(inputPayload.items) ? inputPayload.items : [];
  if (items.length === 0) {
    validationErrors.push('At least one line item is required.');
  }

  const validatedItems = validateAndNormalizeItems(items, validationErrors);
  const taxRate = parseTaxRate(inputPayload.taxPercent);
  const totals = calculateTotals(validatedItems, taxRate);

  if (validationErrors.length > 0) {
    return { isValid: false, errors: validationErrors };
  }

  const sanitizedPoNumber = poNumber.replace(/[^a-zA-Z0-9_\-]/g, '_');

  return {
    isValid: true,
    errors: [],
    data: {
      toEmail,
      subject,
      senderName: String(inputPayload.senderName || customerName).trim(),
      poNumber,
      sanitizedPoNumber,
      poDate,
      customerName,
      customerEmail: String(inputPayload.customerEmail || '').trim(),
      billingAddress: String(inputPayload.billingAddress || '').trim(),
      shippingAddress: String(inputPayload.shippingAddress || '').trim(),
      currency: String(inputPayload.currency || 'USD').trim().toUpperCase(),
      paymentTerms: String(inputPayload.paymentTerms || 'Net 30').trim(),
      deliveryDate: String(inputPayload.deliveryDate || '').trim(),
      additionalNotes: String(inputPayload.additionalNotes || '').trim(),
      items: validatedItems,
      taxPercent: taxRate,
      subtotal: totals.subtotal,
      taxAmount: totals.taxAmount,
      grandTotal: totals.grandTotal
    }
  };
}

/**
 * Validates individual line items.
 * @param {Array} rawItems
 * @param {string[]} validationErrors
 * @returns {Array}
 */
function validateAndNormalizeItems(rawItems, validationErrors) {
  const normalizedItems = [];

  rawItems.forEach((rawItem, itemIndex) => {
    const rowNumber = itemIndex + 1;
    const productCode = String(rawItem.productCode || '').trim();
    const productDescription = String(rawItem.productDescription || '').trim();
    const quantity = parseFloat(rawItem.quantity);
    const unitPrice = parseFloat(rawItem.unitPrice);

    if (!productCode && !productDescription) {
      validationErrors.push(`Row ${rowNumber}: Product code or description is required.`);
    }
    if (isNaN(quantity) || quantity <= 0) {
      validationErrors.push(`Row ${rowNumber}: Quantity must be a positive number.`);
    }
    if (isNaN(unitPrice) || unitPrice < 0) {
      validationErrors.push(`Row ${rowNumber}: Unit price must be zero or a positive number.`);
    }

    const safeQty = isNaN(quantity) || quantity < 0 ? 0 : quantity;
    const safePrice = isNaN(unitPrice) || unitPrice < 0 ? 0 : unitPrice;
    const totalPrice = Math.round(safeQty * safePrice * 100) / 100;

    normalizedItems.push({
      productCode: productCode || `ITEM-${rowNumber}`,
      productDescription: productDescription || 'N/A',
      quantity: safeQty,
      unitPrice: safePrice,
      totalPrice: totalPrice
    });
  });

  return normalizedItems;
}

/**
 * Parses and bounds the tax percentage.
 * @param {any} rawTax
 * @returns {number}
 */
function parseTaxRate(rawTax) {
  const parsedTax = parseFloat(rawTax);
  if (isNaN(parsedTax) || parsedTax < 0) {
    return 0;
  }
  return parsedTax > 100 ? 100 : parsedTax;
}

/**
 * Calculates subtotal, tax amount, and grand total.
 * @param {Array} normalizedItems
 * @param {number} taxRate
 * @returns {{ subtotal: number, taxAmount: number, grandTotal: number }}
 */
function calculateTotals(normalizedItems, taxRate) {
  const subtotal = normalizedItems.reduce((runningSum, currentItem) => {
    return runningSum + currentItem.totalPrice;
  }, 0);

  const roundedSubtotal = Math.round(subtotal * 100) / 100;
  const taxAmount = Math.round(roundedSubtotal * (taxRate / 100) * 100) / 100;
  const grandTotal = Math.round((roundedSubtotal + taxAmount) * 100) / 100;

  return {
    subtotal: roundedSubtotal,
    taxAmount: taxAmount,
    grandTotal: grandTotal
  };
}

module.exports = {
  validatePoData
};
