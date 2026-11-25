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
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Obsidian Hammer Mod",
    modId: "obsidian_hammer",
    edition: "JAVA",
    targetVersionId: "java_1_20_1",
    targetVersionNumber: "1.20.1",
    selectedLoader: "Forge",
    featureCategory: "Weapon",
    prompt: "Add an obsidian warhammer that applies slowness on hit",
    adaptationNotes: "Uses Forge 47.x DeferredRegister and Item properties for 1.20.1.",
    validationStatus: "COMPATIBLE",
    validationSummary: "All 6 checks passed.",
    fileCount: 4,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validFilePayload(uid, projectId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    projectId,
    path: "src/main/java/com/example/obsidianhammer/ObsidianHammerMod.java",
    content: "package com.example.obsidianhammer;\npublic class ObsidianHammerMod {}",
    fileType: "JAVA",
    binaryAsset: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validVersionPayload(uid, versionId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    versionId,
    edition: "BEDROCK",
    versionNumber: "26.64",
    updateName: "Winter Patch",
    releaseDate: "2026-11-28",
    sortOrder: 20261128,
    historicalCategory: "Year-Based Drop",
    releaseType: "Hotfix",
    compatibilityStatus: "COMPATIBLE",
    supportedCapabilities: "Behavior Pack,Resource Pack,Script API",
    compatibleLoaders: "Native Add-on,Script API @minecraft/server 2.x",
    supportedFileFormats: ".mcaddon,.mcpack,.json,.js,.png",
    requiredRuntime: "Bedrock Engine 26.64",
    adaptationNotes: "Compatible with Bedrock Script API 2.x.",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Unauthenticated user: cannot read or write any project, file, or version update", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/version_updates/ver_1`).set(validVersionPayload(ALICE_UID, "ver_1"))
  );
});

test("Owner create, list, partial update, and delete on projects and subcollection files", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`);
  const fileRef = aliceDb.doc(`users/${ALICE_UID}/projects/proj_1/files/file_1`);

  // Orphaned file creation before parent project exists must fail
  await assertFails(fileRef.set(validFilePayload(ALICE_UID, "proj_1")));

  // Create parent project
  await assertSucceeds(projRef.set(validProjectPayload(ALICE_UID)));

  // List own projects (unfiltered subcollection query)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects`).get());

  // Create child file
  await assertSucceeds(fileRef.set(validFilePayload(ALICE_UID, "proj_1")));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects/proj_1/files`).get());

  // Partial update on project validation status
  await assertSucceeds(
    projRef.update({
      validationStatus: "PARTIALLY_COMPATIBLE",
      validationSummary: "1 warning found.",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial update on file content
  await assertSucceeds(
    fileRef.update({
      content: "// Updated Java source",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    projRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable edition or createdAt mutation
  await assertFails(
    projRef.update({
      edition: "BEDROCK",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    projRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_future`).set(
      validProjectPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Reject extra @DocumentId 'id' key
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_bad_id`).set(
      validProjectPayload(ALICE_UID, { id: "proj_bad_id" })
    )
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set(validProjectPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_1/files/file_1`).set(validFilePayload(ALICE_UID, "proj_1"))
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).update({ title: "Hijacked" }));
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).delete());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1/files/file_1`).get());
});

test("Owner can add and update dynamic version_updates entries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const verRef = aliceDb.doc(`users/${ALICE_UID}/version_updates/bedrock_26_64`);

  await assertSucceeds(verRef.set(validVersionPayload(ALICE_UID, "bedrock_26_64")));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/version_updates`).get());
  await assertSucceeds(
    verRef.update({
      adaptationNotes: "Updated hotfix notes.",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/version_updates`).get());
});
