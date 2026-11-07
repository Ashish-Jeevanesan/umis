/**
 * Client-side script for PO Email & PDF Generator.
 */

document.addEventListener('DOMContentLoaded', () => {
  initializeSmtpStatus();
  initializeEventListeners();
  addSampleLineItems();
  recalculateTotals();
});

/**
 * Initializes SMTP configuration status check.
 */
function initializeSmtpStatus() {
  const banner = document.getElementById('smtpStatusBanner');
  const bannerText = document.getElementById('smtpStatusText');

  fetch('/api/smtp-status')
    .then((res) => res.json())
    .then((status) => {
      if (!status.configured) {
        banner.style.display = 'block';
        banner.className = 'alert-banner error-banner';
        bannerText.textContent =
          'Notice: SMTP credentials are not configured in tools/po-email-generator/.env. ' +
          'PDF preview will work, but email delivery will fail until SMTP is configured.';
      }
    })
    .catch(() => {
      // Ignore background status check failure
    });
}

/**
 * Attaches event listeners to buttons and form.
 */
function initializeEventListeners() {
  document.getElementById('btnAddItem').addEventListener('click', () => {
    addLineItemRow();
    recalculateTotals();
  });

  document.getElementById('btnLoadSample').addEventListener('click', populateSampleData);
  document.getElementById('btnClearForm').addEventListener('click', resetForm);
  document.getElementById('taxPercent').addEventListener('input', recalculateTotals);
  document.getElementById('currency').addEventListener('change', recalculateTotals);
  document.getElementById('btnPreviewPdf').addEventListener('click', handlePreviewPdf);
  document.getElementById('poForm').addEventListener('submit', handleSendEmail);
}

/**
 * Adds an empty or populated line item row to the table.
 */
function addLineItemRow(itemData = {}) {
  const tbody = document.getElementById('itemsTableBody');
  const row = document.createElement('tr');
  row.className = 'item-row';

  const code = itemData.productCode || '';
  const desc = itemData.productDescription || '';
  const qty = itemData.quantity !== undefined ? itemData.quantity : 1;
  const price = itemData.unitPrice !== undefined ? itemData.unitPrice : 0;
  const total = (qty * price).toFixed(2);

  row.innerHTML = `
    <td><input type="text" class="item-code" placeholder="PROD-001" value="${escapeAttribute(code)}"></td>
    <td><input type="text" class="item-desc" placeholder="Item description" value="${escapeAttribute(desc)}"></td>
    <td><input type="number" class="item-qty text-right" min="1" step="1" value="${qty}"></td>
    <td><input type="number" class="item-price text-right" min="0" step="0.01" value="${price}"></td>
    <td><input type="text" class="item-total text-right" value="${total}" readonly tabindex="-1"></td>
    <td><button type="button" class="btn-remove-row" title="Remove line item">&times;</button></td>
  `;

  const qtyInput = row.querySelector('.item-qty');
  const priceInput = row.querySelector('.item-price');
  const removeBtn = row.querySelector('.btn-remove-row');

  qtyInput.addEventListener('input', () => updateRowTotal(row));
  priceInput.addEventListener('input', () => updateRowTotal(row));
  removeBtn.addEventListener('click', () => {
    row.remove();
    if (document.querySelectorAll('.item-row').length === 0) {
      addLineItemRow();
    }
    recalculateTotals();
  });

  tbody.appendChild(row);
}

/**
 * Updates a single row's calculated total.
 */
function updateRowTotal(rowElement) {
  const qty = parseFloat(rowElement.querySelector('.item-qty').value) || 0;
  const price = parseFloat(rowElement.querySelector('.item-price').value) || 0;
  const rowTotal = Math.round(qty * price * 100) / 100;
  rowElement.querySelector('.item-total').value = rowTotal.toFixed(2);
  recalculateTotals();
}

/**
 * Recalculates subtotal, tax amount, and grand total across all rows.
 */
function recalculateTotals() {
  const rows = document.querySelectorAll('.item-row');
  let subtotal = 0;

  rows.forEach((rowElement) => {
    const qty = parseFloat(rowElement.querySelector('.item-qty').value) || 0;
    const price = parseFloat(rowElement.querySelector('.item-price').value) || 0;
    subtotal += Math.round(qty * price * 100) / 100;
  });

  const taxPercent = parseFloat(document.getElementById('taxPercent').value) || 0;
  const taxAmount = Math.round(subtotal * (taxPercent / 100) * 100) / 100;
  const grandTotal = Math.round((subtotal + taxAmount) * 100) / 100;
  const currency = document.getElementById('currency').value || 'USD';

  document.getElementById('subtotalDisplay').textContent = `${currency} ${subtotal.toFixed(2)}`;
  document.getElementById('taxAmountDisplay').textContent = `${currency} ${taxAmount.toFixed(2)}`;
  document.getElementById('grandTotalDisplay').textContent = `${currency} ${grandTotal.toFixed(2)}`;
}

/**
 * Collects form data and normalized items into a payload object.
 */
function collectFormData() {
  const rows = document.querySelectorAll('.item-row');
  const items = [];

  rows.forEach((rowElement) => {
    const code = rowElement.querySelector('.item-code').value.trim();
    const desc = rowElement.querySelector('.item-desc').value.trim();
    const qty = parseFloat(rowElement.querySelector('.item-qty').value) || 0;
    const price = parseFloat(rowElement.querySelector('.item-price').value) || 0;

    if (code || desc || qty > 0) {
      items.push({
        productCode: code || 'ITEM',
        productDescription: desc || 'Item',
        quantity: qty,
        unitPrice: price
      });
    }
  });

  return {
    toEmail: document.getElementById('toEmail').value.trim(),
    subject: document.getElementById('subject').value.trim(),
    senderName: document.getElementById('senderName').value.trim(),
    customerEmail: document.getElementById('customerEmail').value.trim(),
    poNumber: document.getElementById('poNumber').value.trim(),
    poDate: document.getElementById('poDate').value,
    customerName: document.getElementById('customerName').value.trim(),
    currency: document.getElementById('currency').value,
    paymentTerms: document.getElementById('paymentTerms').value.trim(),
    deliveryDate: document.getElementById('deliveryDate').value,
    billingAddress: document.getElementById('billingAddress').value.trim(),
    shippingAddress: document.getElementById('shippingAddress').value.trim(),
    additionalNotes: document.getElementById('additionalNotes').value.trim(),
    taxPercent: parseFloat(document.getElementById('taxPercent').value) || 0,
    items: items
  };
}

/**
 * Handles PDF preview generation and in-browser display.
 */
async function handlePreviewPdf() {
  const payload = collectFormData();
  const previewBtn = document.getElementById('btnPreviewPdf');

  setButtonState(previewBtn, true, 'Generating PDF...');
  clearStatus();

  try {
    const response = await fetch('/api/preview-pdf', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const errorJson = await response.json();
      throw new Error((errorJson.errors || []).join(' '));
    }

    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    window.open(objectUrl, '_blank');
  } catch (error) {
    showStatus('error', 'Failed to preview PDF: ' + error.message);
  } finally {
    setButtonState(previewBtn, false, 'Preview PDF');
  }
}

/**
 * Handles email sending with PO PDF.
 */
async function handleSendEmail(event) {
  event.preventDefault();
  const payload = collectFormData();
  const sendBtn = document.getElementById('btnSendEmail');

  setButtonState(sendBtn, true, 'Sending Email...');
  clearStatus();

  try {
    const response = await fetch('/api/send-po-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      const errorMessages = (result.errors || []).join(', ') || 'Unknown delivery failure';
      throw new Error(errorMessages);
    }

    showStatus('success', `Success! ${result.message}`);
  } catch (error) {
    showStatus('error', 'Delivery Error: ' + error.message);
  } finally {
    setButtonState(sendBtn, false, 'Send Email with PO PDF');
  }
}

/**
 * Loads realistic sample data into the form.
 */
function populateSampleData() {
  const today = new Date().toISOString().split('T')[0];
  const delivery = new Date(Date.now() + 10 * 86400000).toISOString().split('T')[0];

  document.getElementById('subject').value = 'Purchase Order PO-1001 – ABC Medical';
  document.getElementById('senderName').value = 'ABC Medical Hospital';
  document.getElementById('customerEmail').value = 'purchasing@abcmedical.com';
  document.getElementById('poNumber').value = 'PO-1001';
  document.getElementById('poDate').value = today;
  document.getElementById('customerName').value = 'ABC Medical Hospital';
  document.getElementById('currency').value = 'USD';
  document.getElementById('paymentTerms').value = 'Net 30';
  document.getElementById('deliveryDate').value = delivery;
  document.getElementById('billingAddress').value = '123 Healthcare Blvd, Suite 400\nChicago, IL 60601';
  document.getElementById('shippingAddress').value = 'Central Receiving Dock B\n125 Healthcare Blvd\nChicago, IL 60601';
  document.getElementById('additionalNotes').value =
    'Standard sterile packaging required. Please notify receiving 24 hours prior to delivery.';
  document.getElementById('taxPercent').value = '5.0';

  const tbody = document.getElementById('itemsTableBody');
  tbody.innerHTML = '';

  const sampleItems = [
    { productCode: 'MED-4401', productDescription: 'Titanium Pedicle Screws 6.5x45mm', quantity: 20, unitPrice: 350.00 },
    { productCode: 'MED-5502', productDescription: 'Sterile Spinal Rods 5.5mm Cobalt', quantity: 10, unitPrice: 220.00 },
    { productCode: 'MED-1209', productDescription: 'Interbody PEEK Cage L4-L5', quantity: 5, unitPrice: 660.00 }
  ];

  sampleItems.forEach((item) => addLineItemRow(item));
  recalculateTotals();
  showStatus('info', 'Sample Purchase Order data loaded.');
}

/**
 * Resets the entire form.
 */
function resetForm() {
  document.getElementById('poForm').reset();
  document.getElementById('itemsTableBody').innerHTML = '';
  document.getElementById('poDate').value = new Date().toISOString().split('T')[0];
  addLineItemRow();
  recalculateTotals();
  clearStatus();
}

function addSampleLineItems() {
  document.getElementById('poDate').value = new Date().toISOString().split('T')[0];
  addLineItemRow({ productCode: 'MED-101', productDescription: 'Surgical Titanium Screws', quantity: 10, unitPrice: 150 });
  addLineItemRow({ productCode: 'MED-202', productDescription: 'Sterile Orthopedic Rods', quantity: 4, unitPrice: 250 });
}

function showStatus(type, message) {
  const statusDiv = document.getElementById('statusMessage');
  statusDiv.style.display = 'block';
  statusDiv.className = `alert-banner ${type}-banner`;
  statusDiv.textContent = message;
}

function clearStatus() {
  const statusDiv = document.getElementById('statusMessage');
  statusDiv.style.display = 'none';
  statusDiv.textContent = '';
}

function setButtonState(button, isLoading, loadingText) {
  button.disabled = isLoading;
  const span = button.querySelector('span') || button;
  span.textContent = loadingText;
}

function escapeAttribute(text) {
  return String(text || '').replace(/"/g, '&quot;');
}
