package com.example.sync

object GoogleAppsScriptTemplate {
    val SCRIPT_CODE = """
/**
 * PASHU AAHAR - Google Apps Script Cloud Sync Backend
 * Deploy as Web App: Execute as "Me", Access "Anyone".
 * Connects Google Sheets + Drive for zero-cost multi-device sync.
 */

function doGet(e) {
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    initSheets(ss);
    
    const customers = getSheetData(ss, "Customers");
    const products = getSheetData(ss, "Products");
    const sales = getSheetData(ss, "Sales");
    const payments = getSheetData(ss, "Payments");
    const purchases = getSheetData(ss, "Purchases");
    const users = getSheetData(ss, "Users");
    
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      timestamp: new Date().getTime(),
      data: {
        customers: customers,
        products: products,
        sales: sales,
        payments: payments,
        purchases: purchases,
        users: users
      }
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doPost(e) {
  try {
    const raw = e.postData.contents;
    const payload = JSON.parse(raw);
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    initSheets(ss);
    
    // 1. Handle Customer Photo Upload if provided
    let photoDriveUrl = null;
    if (payload.photoBase64 && payload.photoFileName) {
      photoDriveUrl = savePhotoToDrive(payload.photoBase64, payload.photoFileName);
    }
    
    // 2. Upsert Customers
    if (payload.customers && payload.customers.length > 0) {
      upsertRows(ss, "Customers", payload.customers);
    }
    
    // 3. Upsert Users
    if (payload.users && payload.users.length > 0) {
      upsertRows(ss, "Users", payload.users);
    }
    
    // 4. Add Purchases (Additive Ledger)
    if (payload.purchases && payload.purchases.length > 0) {
      upsertRows(ss, "Purchases", payload.purchases);
    }
    
    // 5. Add Sales (Additive Ledger)
    if (payload.sales && payload.sales.length > 0) {
      upsertRows(ss, "Sales", payload.sales);
    }
    
    // 6. Add Payments
    if (payload.payments && payload.payments.length > 0) {
      upsertRows(ss, "Payments", payload.payments);
    }
    
    // 7. CRITICAL: Recalculate Product Stock and Average Cost from full Ledger
    recalculateProducts(ss);
    
    // 8. Return fresh authoritative products and updated data
    const updatedProducts = getSheetData(ss, "Products");
    const updatedCustomers = getSheetData(ss, "Customers");
    
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      timestamp: new Date().getTime(),
      photoDriveUrl: photoDriveUrl,
      updatedProducts: updatedProducts,
      updatedCustomers: updatedCustomers
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function initSheets(ss) {
  const schemas = {
    "Users": ["id", "name", "role", "pinHash", "isActive", "createdAt", "updatedAt", "isDeleted"],
    "Customers": ["id", "name", "phone", "village", "photoDriveUrl", "totalUdhari", "promiseDate", "createdByUserId", "createdAt", "updatedAt", "isDeleted"],
    "Products": ["id", "feedName", "baseUnit", "kgPerBag", "currentStockKg", "averagePurchaseCostPerKg", "sellingPricePerKg", "createdAt", "updatedAt", "isDeleted"],
    "Sales": ["id", "date", "customerId", "productId", "quantityKg", "pricePerKgAtSale", "costPerKgAtSale", "totalSalePrice", "paidAmount", "udhariAmount", "paymentStatus", "soldByUserId", "createdAt", "updatedAt", "isDeleted"],
    "Payments": ["id", "date", "customerId", "amount", "linkedSaleId", "note", "collectedByUserId", "createdAt", "updatedAt", "isDeleted"],
    "Purchases": ["id", "date", "wholesalerName", "productId", "quantityKg", "totalPurchaseCost", "paymentStatus", "recordedByUserId", "createdAt", "updatedAt", "isDeleted"]
  };
  
  for (const name in schemas) {
    let sheet = ss.getSheetByName(name);
    if (!sheet) {
      sheet = ss.insertSheet(name);
      sheet.appendRow(schemas[name]);
      sheet.setFrozenRows(1);
    }
  }
}

function getSheetData(ss, sheetName) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) return [];
  const rows = sheet.getDataRange().getValues();
  if (rows.length <= 1) return [];
  
  const headers = rows[0];
  const list = [];
  for (let i = 1; i < rows.length; i++) {
    const row = rows[i];
    const item = {};
    for (let j = 0; j < headers.length; j++) {
      item[headers[j]] = row[j];
    }
    list.push(item);
  }
  return list;
}

function upsertRows(ss, sheetName, items) {
  const sheet = ss.getSheetByName(sheetName);
  const rows = sheet.getDataRange().getValues();
  const headers = rows[0];
  const idIndex = headers.indexOf("id");
  if (idIndex === -1) return;
  
  const idToRowIndex = {};
  for (let i = 1; i < rows.length; i++) {
    idToRowIndex[rows[i][idIndex]] = i + 1;
  }
  
  items.forEach(function(item) {
    const id = item.id;
    const rowData = headers.map(function(h) { return item[h] !== undefined ? item[h] : ""; });
    if (idToRowIndex[id]) {
      sheet.getRange(idToRowIndex[id], 1, 1, rowData.length).setValues([rowData]);
    } else {
      sheet.appendRow(rowData);
      idToRowIndex[id] = sheet.getLastRow();
    }
  });
}

function recalculateProducts(ss) {
  const sales = getSheetData(ss, "Sales");
  const purchases = getSheetData(ss, "Purchases");
  const productSheet = ss.getSheetByName("Products");
  const rows = productSheet.getDataRange().getValues();
  if (rows.length <= 1) return;
  
  const headers = rows[0];
  const idIdx = headers.indexOf("id");
  const stockIdx = headers.indexOf("currentStockKg");
  const avgCostIdx = headers.indexOf("averagePurchaseCostPerKg");
  
  // Group ledger by productId
  const totalPurchasedKg = {};
  const totalPurchaseCost = {};
  purchases.forEach(function(p) {
    if (p.isDeleted) return;
    const pid = p.productId;
    const qty = parseFloat(p.quantityKg) || 0;
    const cost = parseFloat(p.totalPurchaseCost) || 0;
    totalPurchasedKg[pid] = (totalPurchasedKg[pid] || 0) + qty;
    totalPurchaseCost[pid] = (totalPurchaseCost[pid] || 0) + cost;
  });
  
  const totalSoldKg = {};
  sales.forEach(function(s) {
    if (s.isDeleted) return;
    const pid = s.productId;
    const qty = parseFloat(s.quantityKg) || 0;
    totalSoldKg[pid] = (totalSoldKg[pid] || 0) + qty;
  });
  
  for (let r = 1; r < rows.length; r++) {
    const pid = rows[r][idIdx];
    const purchased = totalPurchasedKg[pid] || 0;
    const sold = totalSoldKg[pid] || 0;
    const netStock = purchased - sold;
    
    // Moving average cost: totalPurchaseCost / totalPurchasedKg
    const avgCost = purchased > 0 ? ((totalPurchaseCost[pid] || 0) / purchased).toFixed(2) : rows[r][avgCostIdx];
    
    if (stockIdx !== -1) {
      productSheet.getRange(r + 1, stockIdx + 1).setValue(netStock);
    }
    if (avgCostIdx !== -1 && purchased > 0) {
      productSheet.getRange(r + 1, avgCostIdx + 1).setValue(avgCost);
    }
  }
}

function savePhotoToDrive(base64Data, fileName) {
  const folderName = "PashuAahar_Customer_Photos";
  let folder;
  const folders = DriveApp.getFoldersByName(folderName);
  if (folders.hasNext()) {
    folder = folders.next();
  } else {
    folder = DriveApp.createFolder(folderName);
    folder.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  }
  
  const decoded = Utilities.base64Decode(base64Data);
  const blob = Utilities.newBlob(decoded, "image/jpeg", fileName);
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return file.getUrl();
}
""".trimIndent()
}
