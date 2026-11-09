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

test("Alice can create 10-minute pairing token and unauthenticated device can verify it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthedDb = testEnv.unauthenticatedContext().firestore();

  const code = "482915";
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

  // Unauthenticated counter staff device can check the code to see shop details
  await assertSucceeds(unauthedDb.collection("pairings").doc(code).get());

  // Non-existent code lookup succeeds with non-existent snapshot rather than permission denied
  await assertSucceeds(unauthedDb.collection("pairings").doc("999999").get());
});

test("Bob can claim active pairing code and then access Alice's store as staff", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  const code = "123456";
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

  // Bob claims the pairing code
  await assertSucceeds(
    bobDb.collection("pairings").doc(code).update({
      status: "CLAIMED",
      claimedByUid: BOB_UID,
      claimedByName: "Bob Counter",
      claimedAt: Date.now(),
    })
  );

  // Bob writes staff registration under Alice's store
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc(BOB_UID).set({
      staffUid: BOB_UID,
      name: "Bob Counter",
      role: "CASHIER",
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
});
