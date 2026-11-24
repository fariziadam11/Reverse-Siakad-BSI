"""Scoped Student login observation; no redirects, exploit payloads, or raw bodies."""
import http.cookiejar
from http.cookies import SimpleCookie
import importlib.util
import json
import os
from pathlib import Path
import socket
import ipaddress
import time
from datetime import datetime, timezone
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
SCOPE = Path('C:/reversebsi/security-reports/2026-10-30-bsi-login-passive/login-scope.json')
ORIGIN = 'https://students.bsi.ac.id'
spec = importlib.util.spec_from_file_location('observer', 'C:/reversebsi/security-reports/2026-10-30-bsi/login_observer.py')
observer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observer)


class PageParser(observer.FormParser):
    def __init__(self):
        super().__init__()
        self.classes = set()
        self.scripts = []

    def handle_starttag(self, tag, attrs):
        super().handle_starttag(tag, attrs)
        attrs = dict(attrs)
        self.classes.update(attrs.get('class', '').split())
        if tag == 'script' and attrs.get('src'):
            url = urllib.parse.urlsplit(urllib.parse.urljoin(ORIGIN, attrs['src']))
            self.scripts.append(urllib.parse.urlunsplit((url.scheme, url.netloc, url.path, '', '')))


def metadata(headers):
    cookies = []
    for header in headers.get_all('Set-Cookie', []):
        parsed = SimpleCookie()
        parsed.load(header)
        for name, cookie in parsed.items():
            cookies.append({'secure': bool(cookie['secure']), 'httponly': bool(cookie['httponly']),
                'samesite': cookie['samesite'], 'session_name_hint': 'session' in name.lower()})
    csp = headers.get('Content-Security-Policy', '')
    return {'cookies': cookies, 'cache_control': headers.get('Cache-Control', ''),
        'csp_present': bool(csp), 'frame_ancestors_present': 'frame-ancestors' in csp.lower(),
        'x_frame_options': headers.get('X-Frame-Options', ''),
        'hsts_present': bool(headers.get('Strict-Transport-Security')),
        'nosniff': headers.get('X-Content-Type-Options', '').lower() == 'nosniff'}


def run():
    private = Path(os.environ['LOCALAPPDATA']) / 'web-pentest-agent/private/bsi-login-current/credentials.json'
    rows, entries = [], []
    scope = json.loads(SCOPE.read_text(encoding='utf-8-sig'))
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), observer.NoRedirect(),
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    count, last = 3, 0.0  # Three requests were already used in the shared login assessment.
    started = time.monotonic()

    def request(url, fields=None):
        nonlocal count, last
        method = 'GET' if fields is None else 'POST'
        observer.allowed(scope, url, method, count)
        if time.monotonic() - started >= 110:
            raise ValueError('Runtime limit')
        time.sleep(max(0, 1 - (time.monotonic() - last)))
        observer.allowed(scope, url, method, count)
        count += 1
        last = time.monotonic()
        timestamp = datetime.now(timezone.utc).isoformat()
        body = urllib.parse.urlencode(fields).encode() if fields is not None else None
        req = urllib.request.Request(url, body, {'User-Agent': 'web-pentest-agent/1.0'}, method=method)
        try:
            response = opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if response.code == 429 or response.code >= 500:
                raise ValueError('Target rate limit or error; no retry')
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise ValueError('Response size limit')
            page = PageParser()
            page.feed(raw.decode('utf-8', errors='replace'))
            form = next((f for f in page.forms if {'username', 'password'} <= {i['name'] for i in f['inputs']}), None)
            destination = urllib.parse.urljoin(url, response.headers.get('Location', '')) if response.headers.get('Location') else ''
            safe_destination = urllib.parse.urlunsplit((*urllib.parse.urlsplit(destination)[:3], '', '')) if destination else ''
            authenticated_markers = {'student-sidebar-profile__name', 'student-sidebar-profile__nim'} <= page.classes
            row = dict(url=url, method=method, status=response.code, **metadata(response.headers),
                login_form_present=form is not None, authenticated_profile_markers=authenticated_markers,
                redirect_destination_without_query=safe_destination,
                scripts_without_query=sorted(set(page.scripts)), response_body_retained=False)
            rows.append(row)
            elapsed = round((time.monotonic() - last) * 1000, 3)
            safe_headers = [{'name': k, 'value': v} for k, v in response.headers.items()
                if k.lower() in ('content-type', 'cache-control', 'x-content-type-options', 'x-frame-options', 'strict-transport-security')]
            entries.append({'startedDateTime': timestamp, 'time': elapsed,
                'request': {'method': method, 'url': url, 'httpVersion': 'unknown', 'headers': [], 'queryString': [], 'cookies': [], 'headersSize': -1, 'bodySize': -1},
                'response': {'status': response.code, 'statusText': '', 'httpVersion': 'unknown', 'headers': safe_headers,
                    'cookies': [], 'content': {'size': len(raw), 'mimeType': response.headers.get_content_type()},
                    'redirectURL': safe_destination, 'headersSize': -1, 'bodySize': len(raw)},
                'cache': {}, 'timings': {'send': -1, 'wait': -1, 'receive': -1},
                '_limitations': ['Custom observer, not browser HAR; timing phases and HTTP version unknown.',
                    'Request headers/body, cookies, response body, redirect query and fragment omitted intentionally.']})
            return page, form, destination

    try:
        addresses = {r[4][0] for r in socket.getaddrinfo('students.bsi.ac.id', 443, type=socket.SOCK_STREAM)}
        if not addresses or not all(ipaddress.ip_address(a).is_global for a in addresses):
            raise ValueError('Non-public destination')
        page, form, _ = request(ORIGIN + '/login')
        if not form or any('captcha' in i['name'].lower() for i in form['inputs']):
            raise ValueError('No supported login form or CAPTCHA requires user')
        if form['method'] != 'POST' or urllib.parse.urljoin(ORIGIN + '/login', form['action']) != ORIGIN + '/login':
            raise ValueError('Login form outside scope')
        credential = json.loads(private.read_text(encoding='utf-8'))
        fields = [(i['name'], i.get('value', '')) for i in form['inputs'] if i.get('type', '').lower() == 'hidden']
        fields.extend([('username', credential['username']), ('password', credential['password'])])
        page, form, destination = request(ORIGIN + '/login', fields)
        if form or rows[-1]['status'] in (401, 403, 422) or urllib.parse.urlsplit(destination).path == '/login':
            raise ValueError('Login not established; no credential retry')
        request(ORIGIN + '/mahasiswa/beranda')
    except Exception as error:
        print('Stopped safely:', type(error).__name__, '; no raw error exported.')
    finally:
        private.unlink(missing_ok=True)
        (ROOT / 'observations.json').write_text(json.dumps({'shared_requests_used': count, 'observations': rows}, indent=2), encoding='utf-8')
        (ROOT / 'capture.har').write_text(json.dumps({'log': {'version': '1.2', 'creator': {'name': 'Scoped stdlib observer', 'version': '1'}, 'entries': entries}}, indent=2), encoding='utf-8')
        print(json.dumps({'shared_requests_used': count, 'observations': rows}))


if __name__ == '__main__':
    if '--self-test' in __import__('sys').argv:
        from email.message import Message
        headers = Message()
        headers['Set-Cookie'] = 'example_session=x;Secure;HttpOnly;SameSite=Lax'
        assert metadata(headers)['cookies'][0] == {'secure': True, 'httponly': True, 'samesite': 'Lax', 'session_name_hint': True}
        page = PageParser()
        page.feed('<p class="student-sidebar-profile__name">private</p><script src="/app.js?token=private"></script>')
        assert 'student-sidebar-profile__name' in page.classes and page.scripts == [ORIGIN + '/app.js']
        print('PASS: cookie parsing and script URL secret omission; no target traffic.')
    else:
        run()
