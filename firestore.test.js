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
      pairedAt: Date.now(),
    })
  );

  // Bob writes staff registration under Alice's store referencing his claimed pairing code (N1)
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
      pairingCode: code,
      isActive: true,
      pairedAt: Date.now(),
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

  // Staff member Bob can update his own lastActiveAt / deviceInfo, but CANNOT escalate role to ADMINISTRATOR
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
});

