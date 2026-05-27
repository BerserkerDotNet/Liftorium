import fs from 'node:fs';
import { createHash } from 'node:crypto';

function isObject(v) { return typeof v === 'object' && v !== null && !Array.isArray(v); }
function canonicalize(r) {
  if (!isObject(r)) return r;
  const view = {};
  if (typeof r.schemaVersion !== 'undefined') view.schemaVersion = r.schemaVersion;
  if (isObject(r.metadata)) {
    const md = {};
    for (const [k, v] of Object.entries(r.metadata)) if (k !== 'contentHash') md[k] = v;
    view.metadata = md;
  }
  for (const k of ['programDefaults', 'exerciseCatalog', 'requiredReferences', 'programStructure', 'progressionRules']) {
    if (typeof r[k] !== 'undefined') view[k] = r[k];
  }
  return view;
}
function canonStringify(v) {
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canonStringify).join(',') + ']';
  const entries = Object.entries(v).filter(([, x]) => typeof x !== 'undefined');
  entries.sort(([a], [b]) => (a < b ? -1 : 1));
  return '{' + entries.map(([k, x]) => JSON.stringify(k) + ':' + canonStringify(x)).join(',') + '}';
}

const path = process.argv[2];
if (!path) { console.error('usage: refresh-asset-hash.mjs <file>'); process.exit(2); }
const r = JSON.parse(fs.readFileSync(path, 'utf8'));
const canonical = canonStringify(canonicalize(r));
const hash = createHash('sha256').update(canonical, 'utf-8').digest('hex');
console.log('previous:', r.metadata?.contentHash);
console.log('new:     ', hash);
r.metadata.contentHash = hash;
fs.writeFileSync(path, JSON.stringify(r, null, 2) + '\n');
console.log('written', path);
