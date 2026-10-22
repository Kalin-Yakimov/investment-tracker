import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, rm, symlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { describeFile, createVerificationManifest } from "../verification-manifest.mjs";

test("file inventory contains bytes and SHA-256, not file contents", async t => {
  const folder = await mkdtemp(path.join(os.tmpdir(), "investment-inventory-"));
  t.after(() => rm(folder, { recursive: true, force: true }));
  await writeFile(path.join(folder, "synthetic.txt"), "synthetic fixture\n");
  const result = await describeFile(folder, "synthetic.txt");
  assert.equal(result.sha256, createHash("sha256").update("synthetic fixture\n").digest("hex"));
  assert.equal(result.bytes, 18);
  assert.deepEqual(Object.keys(result), ["path", "bytes", "sha256"]);
});

test("inventory refuses files outside its explicit root", async () => {
  await assert.rejects(describeFile(os.tmpdir(), "../not-allowed"), /within the verification root/);
  await assert.rejects(createVerificationManifest(os.tmpdir()), /fixed repository root/);
});

test("inventory refuses a symlinked parent", async t => {
  const folder = await mkdtemp(path.join(os.tmpdir(), "investment-inventory-link-"));
  t.after(() => rm(folder, { recursive: true, force: true }));
  const actual = path.join(folder, "actual");
  await mkdir(actual); await writeFile(path.join(actual, "fixture.txt"), "fixture");
  await symlink(actual, path.join(folder, "link"), os.platform() === "win32" ? "junction" : "dir");
  await assert.rejects(describeFile(folder, "link/fixture.txt"), /Symlink/);
});
