const http = require('http');
const crypto = require('crypto');
const sessions = new Map();
const prefix = '/student';
const token = 'STUDENT_TEST_CSRF';
const esc = value => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const login = `<html><body><form method="POST" action="${prefix}/login"><input type="hidden" name="_token" value="${token}"><label for="username">Username</label><input id="username" name="username" required><label for="password">Password</label><input id="password" type="password" name="password" required><label><input type="checkbox" name="remember">Remember me</label><button type="submit">Sign In</button></form></body></html>`;
const shell = content => `<html><body><aside class="app-sidebar"><strong class="student-sidebar-profile__name">TEST STUDENT</strong><small class="student-sidebar-profile__nim">NIM: TEST-NIM</small><a class="side-menu__item" href="${prefix}/mahasiswa/krs">Kartu Rencana Studi</a><a class="side-menu__item" href="${prefix}/mahasiswa/khs-semester">KHS Semester</a></aside><form method="POST" action="${prefix}/logout"><input type="hidden" name="_token" value="${token}"><button type="submit">Sign Out</button></form><div class="main-content app-content"><div class="container-fluid">${content}</div></div></body></html>`;
const home = shell('<h5 class="page-title">Beranda</h5><p>Informasi akademik mahasiswa.</p>');
const semester = selected => shell(`<h5>KHS Semester</h5><form method="GET" action="${prefix}/mahasiswa/khs-semester"><label for="semester">Semester</label><select id="semester" name="semester"><option value="">Pilih semester</option><option value="1" ${selected === '1' ? 'selected' : ''}>Semester 1</option></select><button type="submit">Tampilkan</button></form>${selected ? '<p id="semester-result">Semester ' + esc(selected) + '</p>' : ''}`);
function reply(res, status, body = '', headers = {}) { res.writeHead(status, {'Content-Type':'text/html; charset=utf-8', ...headers}); res.end(body); }
function redirect(res, path, status = 302) { reply(res, status, '', {Location:prefix + path}); }
async function rawBody(req) { return new Promise(resolve => { const chunks = []; req.on('data', c => chunks.push(c)); req.on('end', () => resolve(Buffer.concat(chunks))); }); }
function multipart(req, raw) {
  const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(req.headers['content-type'] || '');
  if (!boundary) return [];
  return raw.toString('latin1').split('--' + (boundary[1] || boundary[2])).slice(1, -1).map(part => {
    const index = part.indexOf('\r\n\r\n'); const headers = part.slice(0, index); const bytes = Buffer.from(part.slice(index + 4).replace(/\r\n$/, ''), 'latin1');
    return {name:/name="([^"]*)"/.exec(headers)?.[1], filename:/filename="([^"]*)"/.exec(headers)?.[1], bytes};
  });
}
async function handle(req, res) {
  const url = new URL(req.url, 'http://localhost'); const path = url.pathname;
  let sid = /(?:^|;\s*)student_test_session=([^;]+)/.exec(req.headers.cookie || '')?.[1];
  if (!sid || !sessions.has(sid)) { sid = crypto.randomBytes(12).toString('hex'); sessions.set(sid,{authenticated:false,writes:0}); res.setHeader('Set-Cookie',`student_test_session=${sid}; Path=/; HttpOnly; SameSite=Lax`); }
  const session = sessions.get(sid);
  if (path === '/login' && req.method === 'GET') return session.authenticated ? redirect(res, '/mahasiswa/beranda') : reply(res,200,login);
  if (path === '/login' && req.method === 'POST') {
    const fields = new URLSearchParams((await rawBody(req)).toString());
    if (fields.get('_token') !== token) return reply(res,419,'Expired test token');
    if (fields.get('username') !== 'test-student' || fields.get('password') !== 'test-password') return reply(res,200,login.replace('<form','<div class="alert alert-danger">Akun tidak cocok.</div><form'));
    const next = crypto.randomBytes(12).toString('hex'); sessions.delete(sid); session.authenticated = true; sessions.set(next,session);
    res.setHeader('Set-Cookie',`student_test_session=${next}; Path=/; HttpOnly; SameSite=Lax`); return redirect(res,session.intendedSso?'/mahasiswa/myLMS':'/dashboard');
  }
  if (path === '/dashboard') return redirect(res,'/mahasiswa/beranda');
  if (path === '/__expire' && req.method === 'POST') {session.authenticated=false;return reply(res,200,'Expired synthetic session');}
  if (path === '/__login-as-200') {session.authenticated=false;return reply(res,200,login);}
  if (path === '/__server-error') return reply(res,503,'<h1>Synthetic upstream failure</h1>');
  if (!session.authenticated) {if(path === '/mahasiswa/myLMS')session.intendedSso=true;return redirect(res,'/login');}
  if (path === '/mahasiswa/get-mahasiswa-by-nim' && req.method === 'POST') {
    if (req.headers['x-csrf-token'] !== token || !req.headers['content-type']?.startsWith('application/json')) return reply(res,419,'Expired test token');
    const body = JSON.parse((await rawBody(req)).toString());
    return reply(res,200,JSON.stringify({success:body.nim==='TEST-NIM',data:body.nim==='TEST-NIM'?{nama:'TEST STUDENT',prodi:'TEST PROGRAM',hp:'TEST PHONE',jenjang:'TEST DEGREE'}:null}),{'Content-Type':'application/json'});
  }
  if (path === '/get-jadwal-dosen/TEST-LECTURER') return reply(res,200,JSON.stringify([{kd_mtk:'TEST',nm_mtk:'MATA KULIAH UJI',sks:3,hari:'Senin',jam:'08:00-10:00',ruang:'TEST-ROOM'}]),{'Content-Type':'application/json'});
  if (path === '/mahasiswa/beranda/info-pkl') return reply(res,200,JSON.stringify({data:{nim:'TEST-NIM',nm_mhs:'TEST STUDENT',kd_lokal:'TEST-CLASS',nm_dosen:'TEST LECTURER',kd_dosen:'TEST-LECTURER',info:'Bimbingan uji sintetis'}}),{'Content-Type':'application/json'});
  if (path === '/__outside-read') return reply(res,302,'',{Location:'https://example.invalid/student-read'});
  if (path === '/mahasiswa/myLMS') return reply(res,302,'',{Location:session.badSso ? 'https://example.invalid/authenticate-student?token=synthetic-sso' : 'https://elearning.bsi.ac.id/authenticate-student?token=synthetic-sso'});
  if (path === '/__bad-sso') {session.badSso=true;return reply(res,200,home);}
  if (path === '/mahasiswa/beranda') return reply(res,200,home);
  if (path === '/mahasiswa/krs') return reply(res,200,shell('<h5>Kartu Rencana Studi</h5><table><thead><tr><th>Kode</th><th>Mata Kuliah</th><th>SKS</th></tr></thead><tbody><tr><td>TEST</td><td>MATA KULIAH UJI</td><td>3</td></tr></tbody></table>'));
  if (path === '/mahasiswa/khs-semester') return reply(res,200,semester(url.searchParams.get('semester')));
  if (path === '/__stats') return reply(res,200,shell(`<pre id="received">${esc(JSON.stringify({writes:session.writes,last:session.last}))}</pre>`));
  if (path === '/__download') return reply(res,200,Buffer.from('%PDF-1.4\nstudent fixture\n'),{'Content-Type':'application/pdf','Content-Disposition':'attachment; filename="khs-test.pdf"'});
  if (path === '/logout' && req.method === 'POST') { const fields = new URLSearchParams((await rawBody(req)).toString()); if(fields.get('_token') !== token)return reply(res,419,'Expired test token'); sessions.delete(sid);return redirect(res,'/login'); }
  if (req.method === 'POST' && ['/mahasiswa/password','/mahasiswa/perubahan-biodata','/__replay','/__outside','/__stale'].includes(path)) {
    session.writes++; const raw = await rawBody(req); const parts = multipart(req,raw); const pairs = parts.length ? parts.filter(p=>!p.filename).map(p=>[p.name,p.bytes.toString('utf8')]) : [...new URLSearchParams(raw.toString())];
    session.last = {pairs,referer:req.headers.referer,files:parts.filter(p=>p.filename).map(p=>({name:p.name,filename:p.filename,size:p.bytes.length,sha256:crypto.createHash('sha256').update(p.bytes).digest('hex')}))};
    if (path === '/__stale' || !pairs.some(([k,v])=>k==='_token'&&v===token)) return reply(res,419,'Expired test token');
    if (path === '/__replay') return redirect(res,'/__replayed',307);
    if (path === '/__outside') return reply(res,307,'',{Location:'https://example.invalid/student-mutation'});
    if (path === '/mahasiswa/password') return reply(res,422,shell('<div class="invalid-feedback">Konfirmasi password belum cocok.</div>'));
    return reply(res,200,shell('<div class="alert alert-success">Berkas uji diterima.</div>'));
  }
  reply(res,501,'Unmodelled synthetic Student route');
}
http.createServer((req,res)=>{handle(req,res).catch(()=>{if(!res.headersSent)reply(res,500,'Local Student handler failed');else res.destroy();});}).listen(process.env.PORT || 4515,'127.0.0.1');
