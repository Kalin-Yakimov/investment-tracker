import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { lstat, mkdir, readdir, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repository = fileURLToPath(new URL("../", import.meta.url));

export async function describeFile(root, relative) {
  const target = path.resolve(root, relative);
  if (!target.startsWith(path.resolve(root) + path.sep)) throw new Error("File must remain within the verification root");
  // Reject symlinks in every component, not just the leaf.
  let current = path.resolve(root);
  for (const segment of path.relative(root, target).split(path.sep)) {
    current = path.join(current, segment);
    if ((await lstat(current)).isSymbolicLink()) throw new Error("Symlink is not allowed in a verification inventory");
  }
  const stat = await lstat(target);
  if (!stat.isFile()) throw new Error("Expected a regular verification file");
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(target)) hash.update(chunk);
  return { path: relative.replaceAll("\\", "/"), bytes: stat.size, sha256: hash.digest("hex") };
}

async function artifactFiles(relative) {
  const result = [];
  for (const entry of await readdir(path.join(repository, relative), { withFileTypes: true })) {
    if (entry.isSymbolicLink()) throw new Error("Artifact symlinks are not allowed");
    const nested = path.join(relative, entry.name);
    if (entry.isDirectory()) result.push(...await artifactFiles(nested));
    else if (entry.isFile()) result.push(nested);
  }
  return result;
}

export async function createVerificationManifest(root = repository) {
  if (path.resolve(root) !== path.resolve(repository)) throw new Error("Use the fixed repository root");
  const git = args => execFileSync("git", args, { cwd: root, encoding: "utf8" });
  const names = [...new Set(git(["ls-files", "--cached", "--others", "--exclude-standard", "-z"]).split("\0").filter(Boolean))].sort();
  const sources = [];
  for (const name of names) sources.push(await describeFile(root, name));
  const paths = (await artifactFiles("investment-tracker/build/libs")).filter(file => file.endsWith(".jar"));
  paths.push(...await artifactFiles("react/investment-tracker/build"));
  const artifacts = [];
  for (const file of paths.sort()) artifacts.push(await describeFile(root, file));
  if (artifacts.filter(file => file.path.endsWith(".jar")).length < 2) throw new Error("Build backend and migrations JARs first");
  return {
    purpose: "local-acceptance-verification-only", productionApproved: false,
    generatedAtUtc: new Date().toISOString(), baseCommit: git(["rev-parse", "HEAD"]).trim(),
    dirty: Boolean(git(["status", "--porcelain"]).trim()),
    sourceFingerprint: createHash("sha256").update(JSON.stringify(sources)).digest("hex"),
    sources, artifacts,
    limitations: "Hashes identify files, not proof of a trusted build or hosting acceptance. Repeat acceptance on the final clean approved release commit.",
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const output = path.join(repository, "investment-tracker/build/reports/acceptance/verification-manifest.json");
  const manifest = await createVerificationManifest();
  await mkdir(path.dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(manifest, null, 2) + "\n");
  console.log(`Verification-only manifest written; base ${manifest.baseCommit}, dirty=${manifest.dirty}, ${manifest.artifacts.length} artifacts. No deployment approval.`);
}
