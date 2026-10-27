const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// This local-only mock uses synthetic fixtures, never a real student account.
const fixtures = path.resolve(__dirname, '../../../app/src/test/resources/bsi');
const loginHtml = fs.readFileSync(path.join(fixtures, 'login.html'), 'utf8');
const scheduleHtml = fs.readFileSync(path.join(fixtures, 'schedule.html'), 'utf8');
const sessions = new Map();
const dashboard = `<html><body><span class="user-display-name">TEST STUDENT</span><span class="user-identifier">NIM TEST</span><div class="dropdown-user-card"><small>fixture@example.invalid</small></div><form action="/logout"><input name="_token" value="TEST_CSRF"></form><div class="sidebar-menu"><a href="/sch">Jadwal Kuliah</a></div></body></html>`;
function reply(res, status, body = '', headers = {}) {
  res.writeHead(status, {'Content-Type': 'text/html; charset=utf-8', ...headers});
  res.end(body);
}
function redirect(res, route) { reply(res, 302, '', {Location: '/bsi' + route}); }
function escapeHtml(value) { return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function resultHtml(value) { return `<pre id="received">${escapeHtml(JSON.stringify(value))}</pre>`; }
function nativeFixture(name, extra = '') {
  return fs.readFileSync(path.join(fixtures, 'native', name + '.html'), 'utf8') + extra;
}
async function requestBody(req) {
  if (req.body != null) return req.body;
  return new Promise(resolve => {
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks)));
  });
}
function formPairs(body) {
  if (Buffer.isBuffer(body)) return [...new URLSearchParams(body.toString('utf8'))];
  if (typeof body === 'string') return [...new URLSearchParams(body)];
  return Object.entries(body || {}).flatMap(([name, value]) => (Array.isArray(value) ? value : [value]).map(v => [name, String(v)]));
}
function multipartParts(req, body) {
  const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(req.headers['content-type'] || '');
  if (!boundary) return [];
  const raw = Buffer.isBuffer(body) ? body : Buffer.from(typeof body === 'string' ? body : '', 'utf8');
  return raw.toString('latin1').split('--' + (boundary[1] || boundary[2])).slice(1, -1).map(part => {
    const split = part.indexOf('\r\n\r\n');
    const headers = part.slice(0, split);
    const bytes = Buffer.from(part.slice(split + 4).replace(/\r\n$/, ''), 'latin1');
    return {
      name: /name="([^"]*)"/i.exec(headers)?.[1],
      filename: /filename="([^"]*)"/i.exec(headers)?.[1],
      contentType: /Content-Type:\s*([^\r\n]+)/i.exec(headers)?.[1],
      bytes
    };
  });
}

async function handle(req, res) {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  let sid = /(?:^|;\s*)test_session=([^;]+)/.exec(req.headers.cookie || '')?.[1];
  if (!sid || !sessions.has(sid)) {
    sid = crypto.randomBytes(12).toString('hex');
    sessions.set(sid, {authenticated: false});
    res.setHeader('Set-Cookie', `test_session=${sid}; Path=/; HttpOnly; SameSite=Lax`);
  }
  const session = sessions.get(sid);
  if (pathname === '/login' && req.method === 'GET') {
    if (session.authenticated) return redirect(res, '/user/dashboard');
    return reply(res, 200, loginHtml);
  }
  if (pathname === '/login' && req.method === 'POST') {
    const raw = req.body == null ? await new Promise(resolve => { let body=''; req.on('data', c => body += c); req.on('end', () => resolve(body)); }) : '';
    const fields = req.body && typeof req.body === 'object' ? req.body : Object.fromEntries(new URLSearchParams(typeof req.body === 'string' ? req.body : raw));
    if (fields._token !== 'TEST_CSRF') return reply(res, 419, 'Expired CSRF');
    if (fields.username !== 'test-student' || fields.password !== 'test-password' || fields.captcha_answer !== 'AB3') {
      return reply(res, 200, loginHtml.replace('<form', '<div class="text-red-600">Kode keamanan atau akun tidak cocok.</div><form'));
    }
    const next = crypto.randomBytes(12).toString('hex');
    sessions.delete(sid); sessions.set(next, {authenticated:true});
    res.setHeader('Set-Cookie', `test_session=${next}; Path=/; HttpOnly; SameSite=Lax`);
    return redirect(res, '/dashboard');
  }
  if (pathname === '/dashboard') return redirect(res, '/login');
  if (pathname === '/server-error') return reply(res, 503, '<h1>Test upstream unavailable</h1>');
  if (pathname === '/outside') return reply(res, 302, '', {Location:'https://example.invalid/authenticate?token=synthetic'});
  if (pathname === '/__expire' && req.method === 'POST') { session.authenticated=false; return reply(res, 200, 'Expired fixture session'); }
  if (!session.authenticated) return redirect(res, '/login');
  if (pathname === '/user/dashboard') return reply(res, 200, dashboard);
  if (pathname === '/sch') return reply(res, 200, scheduleHtml);
  if (pathname === '/learning/fixture-id') return reply(res, 200, fs.readFileSync(path.join(fixtures, 'learning.html'), 'utf8'));
  if (pathname === '/assignment/fixture-id') return reply(res, 200, fs.readFileSync(path.join(fixtures, 'assignments.html'), 'utf8'));
  if (pathname === '/absen-mhs/fixture-id') return reply(res, 200, nativeFixture('class'));
  if (pathname === '/logout' && req.method === 'POST') { sessions.delete(sid); return redirect(res, '/login'); }
  if (pathname === '/profil') return reply(res, 200, nativeFixture('profile', session.lastResult ? resultHtml(session.lastResult) : ''));
  if (pathname === '/assignment/send/fixture-id') return reply(res, 200, nativeFixture('submission', session.lastResult ? resultHtml(session.lastResult) : ''));
  if (pathname === '/form-diskusimhs/fixture-id') return reply(res, 200, nativeFixture('discussion', session.lastResult ? resultHtml(session.lastResult) : ''));
  if (pathname === '/__forms/stats') return reply(res, 200, resultHtml({writes: session.writes || 0}));
  if (pathname === '/__forms/download' && req.method === 'GET') {
    const query = [...new URL(req.url, 'http://localhost').searchParams];
    if (query.filter(([k]) => k === 'choice').length !== 2) return reply(res, 422, 'Missing duplicate choices');
    return reply(res, 200, Buffer.from('%PDF-1.4\nfixture\n'), {'Content-Type':'application/pdf', 'Content-Disposition':"attachment; filename*=UTF-8''Materi%20A%2BB.pdf"});
  }
  if (req.method === 'POST' && ['/foto-profil/update', '/profil/update', '/assignment', '/tambah-diskusimhs', '/download-file-tugas', '/__forms/replay', '/__forms/outside', '/__forms/server-error'].includes(pathname)) {
    session.writes = (session.writes || 0) + 1;
    const raw = await requestBody(req);
    const multipart = multipartParts(req, raw);
    const pairs = multipart.length ? multipart.filter(p => !p.filename).map(p => [p.name, p.bytes.toString('utf8')]) : formPairs(raw);
    const field = name => pairs.find(([key]) => key === name)?.[1];
    if (field('_token') !== 'TEST_CSRF') return reply(res, 419, 'Expired CSRF');
    if (pathname === '/__forms/replay') return reply(res, 307, '', {Location:'/bsi/__forms/replayed'});
    if (pathname === '/__forms/outside') return reply(res, 307, '', {Location:'https://example.invalid/mutation'});
    if (pathname === '/__forms/server-error') return reply(res, 503, '<h1>Fixture write outcome unknown</h1>');
    if (pathname === '/profil/update' || (pathname === '/foto-profil/update' && !field('email')?.includes('@'))) {
      return reply(res, 422, nativeFixture('profile', '<div class="invalid-feedback" data-field="email">Email tidak valid.</div>'));
    }
    if (pathname === '/download-file-tugas') {
      if (field('file') === 'login') return reply(res, 200, loginHtml);
      if (field('file') === 'disguised-login') return reply(res, 200, loginHtml, {'Content-Type':'application/octet-stream'});
      if (field('id') !== 'fixture-id') return reply(res, 422, '<div class="invalid-feedback">ID fixture salah.</div>');
      return reply(res, 200, Buffer.concat([Buffer.from('%PDF-1.4\nfixture\n'), Buffer.from([0, 255, 1, 128])]), {'Content-Type':'application/pdf', 'Content-Disposition':'attachment; filename="../../nilai.pdf"'});
    }
    session.lastResult = {
      pairs,
      referer:req.headers.referer,
      origin:req.headers.origin,
      multipart: Boolean(multipart.length),
      files:multipart.filter(p => p.filename).map(p => ({name:p.name,filename:p.filename,contentType:p.contentType,bytes:p.bytes.length,sha256:crypto.createHash('sha256').update(p.bytes).digest('hex')}))
    };
    const rotated = crypto.randomBytes(12).toString('hex');
    sessions.delete(sid); sessions.set(rotated, session);
    res.setHeader('Set-Cookie', `test_session=${rotated}; Path=/; HttpOnly; SameSite=Lax`);
    if (pathname === '/foto-profil/update') return redirect(res, '/profil');
    if (pathname === '/assignment') return redirect(res, '/assignment/send/fixture-id');
    return redirect(res, '/form-diskusimhs/fixture-id');
  }
  reply(res, 501, 'Unmodelled test route');
}
http.createServer((req, res) => {
  handle(req, res).catch(() => {
    if (!res.headersSent) reply(res, 500, 'Local fixture handler failed');
    else res.destroy();
  });
}).listen(process.env.PORT || 4500, '127.0.0.1');
