import { readdir } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

async function findTests(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return findTests(target);
    return entry.isFile() && entry.name.endsWith(".test.js") ? [target] : [];
  }));
  return nested.flat();
}

const tests = await findTests(path.resolve("src"));
if (tests.length === 0) throw new Error("No frontend test files found.");
await Promise.all(tests.sort().map((testFile) => import(pathToFileURL(testFile))));
