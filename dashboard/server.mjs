import http from 'node:http';
import { readFile, writeFile, mkdir, rename, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 8787);
const token = process.env.EVENT_ASSISTANT_TOKEN;
if (!token || token.length < 16) throw new Error('Set EVENT_ASSISTANT_TOKEN to a private token of at least 16 characters.');
const dataFile = join(here, 'data', 'events.json');
const publicDir = join(here, 'public');
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };

async function events() { try { return JSON.parse(await readFile(dataFile, 'utf8')); } catch { return []; } }
async function save(items) { await mkdir(dirname(dataFile), { recursive: true }); const tmp = `${dataFile}.tmp`; await writeFile(tmp, JSON.stringify(items, null, 2)); await rename(tmp, dataFile); }
function authorized(req) { return req.headers.authorization === `Bearer ${token}`; }
function json(res, code, body) { res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(body)); }
async function body(req) { let text = ''; for await (const part of req) { text += part; if (text.length > 65536) throw Error('Request too large'); } return JSON.parse(text); }
function normalize(input) {
  if (!input || typeof input.id !== 'string' || typeof input.name !== 'string' || !Number.isFinite(input.startMillis)) throw Error('Invalid event');
  const text = value => typeof value === 'string' ? value.slice(0, 500) : null;
  const endMillis = Number.isFinite(input.endMillis) && input.endMillis > input.startMillis ? input.endMillis : null;
  return { id: input.id.slice(0, 64), name: input.name.slice(0, 180), startMillis: input.startMillis, endMillis, allDay: Boolean(input.allDay), location: text(input.location), link: text(input.link), organizer: text(input.organizer), priority: input.priority === 2 ? 2 : 1, needsDateConfirmation: Boolean(input.needsDateConfirmation), updatedAt: Date.now() };
}
const app = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname === '/api/events' && req.method === 'GET') {
      if (!authorized(req)) return json(res, 401, { error: 'Unauthorized' });
      return json(res, 200, (await events()).sort((a,b) => a.startMillis - b.startMillis));
    }
    if (url.pathname === '/api/events' && req.method === 'POST') {
      if (!authorized(req)) return json(res, 401, { error: 'Unauthorized' });
      const incoming = normalize(await body(req)), current = await events(), index = current.findIndex(x => x.id === incoming.id);
      if (index < 0) current.push(incoming); else current[index] = { ...current[index], ...incoming };
      await save(current); return json(res, 201, incoming);
    }
    const requested = url.pathname === '/' ? '/index.html' : url.pathname;
    const path = join(publicDir, requested); if (!path.startsWith(publicDir) || !existsSync(path) || !(await stat(path)).isFile()) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream' }); res.end(await readFile(path));
  } catch (error) { json(res, 400, { error: error.message || 'Bad request' }); }
});
app.listen(port, '0.0.0.0', () => console.log(`Bat dashboard listening on http://0.0.0.0:${port}`));
