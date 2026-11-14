const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const ADMIN_UID = "admin_uid_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 1000);
}

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

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list users or usage_metrics", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("usage_metrics").get());
  await assertFails(unauthDb.collection("audit_logs").get());
});

test("Normal user can create their own profile with role=user and cannot self-promote to admin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  const ts = nowTs();
  // Self-promotion attempt must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "admin",
      status: "active",
      preferredLanguage: "en",
      preferredTheme: "white_crystal",
      dailyLimit: 100,
      monthlyBudgetUsd: 10,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid creation with role=user must succeed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      status: "active",
      preferredLanguage: "en",
      preferredTheme: "white_crystal",
      dailyLimit: 100,
      monthlyBudgetUsd: 10,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Subsequent self-promotion update must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: nowTs(),
    })
  );
});

test("Shadow update with ghost field fails on user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const ts = nowTs();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      status: "active",
      preferredLanguage: "en",
      preferredTheme: "white_crystal",
      dailyLimit: 100,
      monthlyBudgetUsd: 10,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isVerified: true,
      updatedAt: nowTs(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice profile, conversations, or memories", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      status: "active",
      preferredLanguage: "en",
      preferredTheme: "white_crystal",
      dailyLimit: 100,
      monthlyBudgetUsd: 10,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      title: "Pref",
      content: "Concise Kotlin",
      category: "coding",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Secret Chat",
      agentId: "titan_core",
      providerId: "gemini",
      modelId: "gemini-3.8-flash",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
});

test("Admin with trusted custom claim can list users, edit roles, write system_config and audit_logs, but CANNOT read user private conversations or memories", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      status: "active",
      preferredLanguage: "en",
      preferredTheme: "white_crystal",
      dailyLimit: 100,
      monthlyBudgetUsd: 10,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Private Chat",
      agentId: "titan_core",
      providerId: "gemini",
      modelId: "gemini-3.8-flash",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // Unverified email claim spoofing must fail
  const spoofAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "pranavji628@gmail.com",
    email_verified: false,
    admin: true,
  }).firestore();
  await assertFails(spoofAdminDb.collection("users").get());

  // Verified trusted admin claim succeeds on admin collections, fails on private conversations
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "pranavji628@gmail.com",
    email_verified: true,
    admin: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("system_config").doc("global").set({
      maintenanceMode: false,
      defaultProvider: "gemini",
      defaultModel: "gemini-3.8-flash",
      allowedProviders: ["gemini", "openai", "openrouter"],
      updatedBy: "pranavji628@gmail.com",
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    adminDb.collection("audit_logs").doc("log_1").set({
      actorUid: ADMIN_UID,
      actorEmail: "pranavji628@gmail.com",
      action: "UPDATE_CONFIG",
      targetUid: ALICE_UID,
      details: "Enabled global model routing",
      createdAt: nowTs(),
    })
  );
  // Admin cannot update or delete immutable audit logs
  await assertFails(adminDb.collection("audit_logs").doc("log_1").delete());
  // Admin cannot read private conversations
  await assertFails(adminDb.collection("users").doc(ALICE_UID).collection("conversations").get());
});

test("Usage metrics query alignment: user must filter by userId", async () => {
  const ts = nowTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("usage_metrics").doc("m_1").set({
      userId: ALICE_UID,
      providerId: "gemini",
      modelId: "gemini-3.8-flash",
      tokensUsed: 420,
      estimatedCostUsd: 0.0,
      latencyMs: 650,
      status: "success",
      createdAt: ts,
    })
  );

  // Unfiltered list fails for regular user
  await assertFails(aliceDb.collection("usage_metrics").get());
  // Scoped query passes
  await assertSucceeds(aliceDb.collection("usage_metrics").where("userId", "==", ALICE_UID).get());
});
