const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_staff_456";
const EVE_UID = "eve_hacker_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

test("Unauthenticated user cannot read any user store profile", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("users").doc(ALICE_UID).get());
});

test("Store owner Alice can write and read her store profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      storeName: "KhataWise Cattle Feed Store",
      ownerName: "Alice Sharma",
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Eve cannot read or write Alice's store profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb.collection("users").doc(ALICE_UID).set({
    storeName: "Alice's Store",
  });

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("users").doc(ALICE_UID).get());
  await assertFails(eveDb.collection("users").doc(ALICE_UID).update({ storeName: "Hacked" }));
});

test("Alice can create 10-minute 8-digit pairing token and only authenticated devices can verify it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthedDb = testEnv.unauthenticatedContext().firestore();

  const code = "48291507";
  const now = Date.now();
  const expiresAt = now + 10 * 60 * 1000;

  // Alice creates pairing code
  await assertSucceeds(
    aliceDb.collection("pairings").doc(code).set({
      code,
      adminUid: ALICE_UID,
      storeName: "Alice Shop",
      assignedRole: "CASHIER",
      status: "ACTIVE",
      createdAt: now,
      expiresAt: expiresAt,
    })
  );

  // Unauthenticated lookup is blocked (N3)
  await assertFails(unauthedDb.collection("pairings").doc(code).get());

  // Authenticated counter staff device can check the code to see shop details
  await assertSucceeds(bobDb.collection("pairings").doc(code).get());
  await assertSucceeds(bobDb.collection("pairings").doc("99999999").get());
});

test("Bob can claim active pairing code and then access Alice's store as staff; Eve cannot self-add without claimed pairing; deactivated staff lose access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  const code = "12345678";
  const now = Date.now();
  const expiresAt = now + 10 * 60 * 1000;

  await aliceDb.collection("users").doc(ALICE_UID).set({
    storeName: "Alice Feed Mart",
  });

  await aliceDb.collection("pairings").doc(code).set({
    code,
    adminUid: ALICE_UID,
    storeName: "Alice Feed Mart",
    assignedRole: "CASHIER",
    status: "ACTIVE",
    createdAt: now,
    expiresAt: expiresAt,
  });

  // Eve cannot add herself as staff without claiming a pairing token (N1)
  await assertFails(
    eveDb.collection("users").doc(ALICE_UID).collection("staff").doc(EVE_UID).set({
      staffUid: EVE_UID,
      name: "Eve Attacker",
      role: "CASHIER",
      isActive: true,
      pairedAt: Date.now(),
    })
  );

  // Bob claims the pairing code
  await assertSucceeds(
    bobDb.collection("pairings").doc(code).update({
      status: "CLAIMED",
      claimedByUid: BOB_UID,
      claimedByName: "Bob Counter",
      claimedAt: Date.now(),
    })
  );

  // Eve still cannot use Bob's claimed pairing code to register herself (N1)
  await assertFails(
    eveDb.collection("users").doc(ALICE_UID).collection("staff").doc(EVE_UID).set({
      staffUid: EVE_UID,
      name: "Eve Attacker",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );

  // Bob cannot self-approve when creating his staff registration (N19)
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      isApproved: true,
      status: "APPROVED",
      pairedAt: Date.now(),
    })
  );

  // Bob cannot omit isApproved or status to masquerade as a legacy staff document (N28)
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      pairedAt: Date.now(),
    })
  );

  // Bob writes PENDING staff registration under Alice's store referencing his claimed pairing code (N1 & N19 & N28)
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );

  // Bob consumes the pairing token so it cannot be reused later (N18)
  await assertSucceeds(
    bobDb.collection("pairings").doc(code).update({
      status: "USED",
      usedAt: Date.now(),
    })
  );

  // While Bob is still PENDING approval, he can read his own staff doc, but CANNOT read or write Alice's store data (N19)
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).get()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_bob").set({
      name: "Suresh Farmer",
      phone: "9876543210",
      creditLimit: 15000,
      currentBalance: 0,
    })
  );

  // Alice (store admin) approves Bob (N19)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      isApproved: true,
      status: "APPROVED",
    })
  );

  // Bob can now read Alice's store profile
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());

  // Bob can add a customer to Alice's store
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_bob").set({
      name: "Suresh Farmer",
      phone: "9876543210",
      creditLimit: 15000,
      currentBalance: 0,
    })
  );

  // Bob can record a transaction
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bob_1").set({
      customerId: "cust_bob",
      type: "SALE_CASH",
      amount: 800,
    })
  );

  // Eve (unauthorized) cannot read or write to Alice's store
  await assertFails(
    eveDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_bob").get()
  );
  await assertFails(
    eveDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_eve").set({
      customerId: "cust_bob",
      type: "SALE_CASH",
      amount: 100,
    })
  );

  // Staff member Bob can update his own lastActiveAt / deviceInfo, but CANNOT escalate role or approval
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      lastActiveAt: Date.now(),
      deviceInfo: "Redmi Note 12",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      role: "ADMINISTRATOR",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      isActive: true,
      role: "ADMINISTRATOR",
    })
  );

  // When Alice deactivates Bob (isActive = false), Bob immediately loses read/write access (N2)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      isActive: false,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_bob").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bob_2").set({
      customerId: "cust_bob",
      type: "SALE_CASH",
      amount: 500,
    })
  );

  // Deactivated Bob CANNOT undo his deactivation by re-submitting his old pairing code (N18)
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );

  // When Alice removes Bob (deletes his staff doc), Bob CANNOT re-add himself using his old pairing code (N18)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );

  // If Alice issues a NEW pairing code for Bob (e.g. as MANAGER), Bob can claim it, re-pair as PENDING, and Alice approves (N14 & N18)
  const newCode = "87654321";
  await aliceDb.collection("pairings").doc(newCode).set({
    code: newCode,
    adminUid: ALICE_UID,
    storeName: "Alice Feed Mart",
    assignedRole: "MANAGER",
    status: "ACTIVE",
    createdAt: now,
    expiresAt: expiresAt,
  });
  await assertSucceeds(
    bobDb.collection("pairings").doc(newCode).update({
      status: "CLAIMED",
      claimedByUid: BOB_UID,
      claimedByName: "Bob Counter",
      claimedAt: Date.now(),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "MANAGER",
      pairingCode: newCode,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );
  await assertSucceeds(
    bobDb.collection("pairings").doc(newCode).update({
      status: "USED",
      usedAt: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      isApproved: true,
      status: "APPROVED",
    })
  );

  // If Alice later demotes Bob from MANAGER to STAFF, Bob CANNOT restore MANAGER using newCode (N18)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).update({
      role: "STAFF",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "MANAGER",
      pairingCode: newCode,
      isActive: true,
      isApproved: false,
      status: "PENDING",
      pairedAt: Date.now(),
    })
  );

  // Staff document without approval fields is denied store access until approved (S1)
  const UNAPPROVED_UID = "unapproved_staff_01";
  const unapprovedDb = testEnv.authenticatedContext(UNAPPROVED_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc(UNAPPROVED_UID).set({
      staffUid: UNAPPROVED_UID,
      name: "Unapproved Cashier",
      role: "STAFF",
      isActive: true,
    })
  );
  await assertFails(unapprovedDb.collection("users").doc(ALICE_UID).get());

  // SEC-1: Cashier (STAFF/CASHIER) cannot raise customer creditLimit or alter inventory selling/cost prices
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_sec1").set({
      name: "Kisan Bhai",
      creditLimit: 5000,
      totalUdhari: 1000,
    })
  );
  // Bob (role: STAFF, approved) CAN update customer totalUdhari after a sale
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_sec1").update({
      totalUdhari: 1500,
    })
  );
  // Bob CANNOT increase customer creditLimit
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_sec1").update({
      creditLimit: 50000,
    })
  );

  // Alice creates an inventory item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory").doc("prod_sec1").set({
      name: "Kapila 50kg",
      currentStockKg: 500,
      sellingPricePerKg: 30,
      averagePurchaseCostPerKg: 25,
    })
  );
  // Bob CAN update stock after a sale
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("inventory").doc("prod_sec1").update({
      currentStockKg: 450,
    })
  );
  // Bob CANNOT lower sellingPricePerKg or change averagePurchaseCostPerKg
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("inventory").doc("prod_sec1").update({
      sellingPricePerKg: 10,
    })
  );

  // F-1: Bob (STAFF/CASHIER) can create a new transaction, but CANNOT overwrite an existing transaction or bill
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_immutable_1").set({
      customerId: "cust_sec1",
      type: "SALE_CREDIT",
      amount: 1200,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_immutable_1").update({
      amount: 10,
    })
  );

  // F-10: Bob can append an audit_logs entry, and neither Bob nor anyone can mutate/delete an audit log
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("audit_1").set({
      saleId: "tx_immutable_1",
      billNumber: "1001",
      actionType: "CREATED",
      performedByUserId: BOB_UID,
      performedByUserName: "Bob Counter",
      summary: "Created bill #1001",
      timestamp: Date.now(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("audit_1").delete()
  );

  // Bob CANNOT delete another staff member's document, but CAN delete his own staff document when unpairing his device
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(UNAPPROVED_UID).delete()
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).delete()
  );
});

