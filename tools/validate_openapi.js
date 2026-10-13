const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const jsonschema = require('jsonschema');

const specPath = path.resolve(__dirname, '../contracts/openapi.yaml');
const text = fs.readFileSync(specPath, 'utf8');
const spec = yaml.load(text);
const errors = [];

// 1. Check all $ref are resolvable
const refRegex = /\$ref:\s*['"]?#\/([^'"\s]+)['"]?/g;
let match;
const refs = new Set();
while ((match = refRegex.exec(text)) !== null) {
  refs.add(match[1]);
}

for (const ref of refs) {
  let node = spec;
  for (const part of ref.split('/')) {
    node = (node && typeof node === 'object') ? node[part] : undefined;
    if (node === undefined) {
      errors.push('ref tidak terselesaikan: #/' + ref);
      break;
    }
  }
}

// 2. Check Event schemas
const ev = spec.components.schemas.Event;
const types = ev.properties.type.enum;
const mapping = ev['x-payload-by-type'];
const oneof = ev.properties.payload.oneOf.map(o => o['$ref'].split('/').pop());

for (const t of types) {
  if (!mapping[t]) errors.push('event tanpa skema di x-payload-by-type: ' + t);
  else if (!spec.components.schemas[mapping[t]]) errors.push('skema hilang di components.schemas: ' + mapping[t]);
  else if (!oneof.includes(mapping[t])) errors.push('skema tidak ada di payload.oneOf: ' + mapping[t]);
}
for (const t in mapping) {
  if (!types.includes(t)) errors.push('mapping untuk tipe yang tidak ada: ' + t);
}
if (new Set(Object.values(mapping)).size !== Object.keys(mapping).length) {
  errors.push('dua tipe memakai skema yang sama');
}

// 3. Validate examples against event schemas
function conv(n) {
  if (n && typeof n === 'object') {
    if (Array.isArray(n)) return n.map(conv);
    const res = {};
    for (const [k, v] of Object.entries(n)) {
      res[k] = conv(v);
    }
    if (res.nullable && res.type) {
      res.type = [res.type, 'null'];
      delete res.nullable;
    }
    return res;
  }
  return n;
}

const comps = conv(spec.components);
const examplesPath = path.resolve(__dirname, '../contracts/examples/events.json');
const examples = JSON.parse(fs.readFileSync(examplesPath, 'utf8')).examples;

for (const t of types) {
  if (!examples[t]) {
    errors.push('contoh hilang: ' + t);
    continue;
  }
  const schema = { '$ref': '#/components/schemas/' + mapping[t], components: comps };
  const validator = new jsonschema.Validator();
  const vRes = validator.validate(examples[t], schema);
  if (!vRes.valid) {
    errors.push('contoh ' + t + ' tidak sah: ' + vRes.errors.map(e => e.stack).join(', '));
  }
  const others = Object.values(mapping).filter(m => m !== mapping[t]);
  for (const m of others) {
    const oRes = validator.validate(examples[t], { '$ref': '#/components/schemas/' + m, components: comps });
    if (oRes.valid) {
      errors.push('contoh ' + t + ' juga cocok dengan skema lain (oneOf ambigu): ' + m);
    }
  }
}

console.log('paths=' + Object.keys(spec.paths).length + ' skema=' + Object.keys(spec.components.schemas).length + ' event=' + types.length);
if (errors.length === 0) {
  console.log('OK');
  process.exit(0);
} else {
  console.error(errors.join('\n'));
  process.exit(1);
}
