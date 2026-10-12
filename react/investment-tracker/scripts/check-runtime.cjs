const [major, minor, patch] = process.versions.node.split(".").map(Number);
const supported = (major === 22 && (minor > 22 || (minor === 22 && patch >= 2)))
  || (major === 24 && minor >= 15) || major >= 26;

if (!supported) {
  console.error(`Node.js ${process.versions.node} is not supported by this frontend toolchain.`);
  console.error("Install Node.js 24 LTS (24.15 or newer), reopen your terminal, then run npm ci.");
  process.exitCode = 1;
}
