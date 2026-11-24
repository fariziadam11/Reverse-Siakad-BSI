"""Read-only session checks on two approved URLs; export metadata only."""
from datetime import datetime, timezone
import http.cookiejar
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = Path(__file__).resolve().parent
ORIGIN = 'https://students.bsi.ac.id'
spec = importlib.util.spec_from_file_location('student_observer', str(HERE / 'student_observer.py'))
previous = importlib.util.module_from_spec(spec)
spec.loader.exec_module(previous)


def safe_path(url):
    parsed = urllib.parse.urlsplit(urllib.parse.urljoin(ORIGIN, url))
    if parsed.scheme != 'https' or parsed.netloc != 'students.bsi.ac.id':
        return '[external omitted]'
    segments = parsed.path.split('/')
    return '/'.join(segment if re.fullmatch(r'[A-Za-z_-]{1,40}', segment or '') else ('{value}' if segment else '') for segment in segments)


class Page(previous.PageParser):
    def __init__(self, account=''):
        super().__init__()
        self.links = set()
        self.profile_text_present = False
        self.nim_context = False
        self.profile_context = False
        self.profile_account_match = None
        self.account = account

    def handle_starttag(self, tag, attrs):
        super().handle_starttag(tag, attrs)
        attrs = dict(attrs)
        classes = attrs.get('class', '').split()
        if 'student-sidebar-profile__nim' in classes:
            self.nim_context = True
        if 'student-sidebar-profile__name' in classes or 'student-sidebar-profile__nim' in classes:
            self.profile_context = True
        if tag == 'a' and attrs.get('href') and not attrs['href'].startswith(('#', 'javascript:')):
            self.links.add(safe_path(attrs['href']))

    def handle_endtag(self, tag):
        super().handle_endtag(tag)
        self.nim_context = False
        self.profile_context = False

    def handle_data(self, data):
        if self.profile_context and data.strip():
            self.profile_text_present = True
        if self.nim_context and self.account:
            identifiers = re.findall(r'\b\d{6,}\b', data)
            if identifiers:
                self.profile_account_match = self.account in identifiers


def run():
    private = Path(os.environ['LOCALAPPDATA']) / 'web-pentest-agent/private/bsi-login-current/credentials.json'
    scope = json.loads((HERE / 'session-scope.json').read_text(encoding='utf-8-sig'))
    output = HERE / 'session-observations.json'
    if output.exists():
        raise ValueError('Existing run; no duplicate requests')
    jar = http.cookiejar.CookieJar()
    anonymous = urllib.request.build_opener(urllib.request.ProxyHandler({}), previous.observer.NoRedirect())
    authenticated = urllib.request.build_opener(urllib.request.ProxyHandler({}), previous.observer.NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
    result = {'started_utc': datetime.now(timezone.utc).isoformat(), 'requests_sent': 0,
        'observations': [], 'session_cookie_value_change': 'not assessed', 'stopped': ''}
    started, last = time.monotonic(), 0.0

    def request(opener, url, context, fields=None, account=''):
        nonlocal last
        method = 'GET' if fields is None else 'POST'
        previous.observer.allowed(scope, url, method, result['requests_sent'])
        time.sleep(max(0, 1 - (time.monotonic() - last)))
        previous.observer.allowed(scope, url, method, result['requests_sent'])
        if time.monotonic() - started >= 110:
            raise ValueError('Runtime budget exhausted')
        last = time.monotonic()
        result['requests_sent'] += 1
        body = None if fields is None else urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(url, body, {'User-Agent': 'web-pentest-agent/1.0'}, method=method)
        try:
            response = opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if response.code == 429 or response.code >= 500:
                raise ValueError('Rate limit or server error')
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                raise ValueError('Response size limit')
            page = Page(account)
            page.feed(raw.decode('utf-8', errors='replace'))
            form = next((f for f in page.forms if {'username','password'} <= {i['name'] for i in f['inputs']}), None)
            destination = urllib.parse.urljoin(url, response.headers['Location']) if response.headers.get('Location') else ''
            markers = {'student-sidebar-profile__name','student-sidebar-profile__nim'} <= page.classes
            row = {'context': context, 'url': url, 'method': method, 'status': response.code,
                'redirect_path': safe_path(destination) if destination else '', 'login_form_present': form is not None,
                'authenticated_profile_markers': markers, 'profile_text_present': page.profile_text_present,
                'profile_matches_supplied_account': page.profile_account_match,
                'select2_target_selectors': {'.js-example-templating': 'js-example-templating' in page.classes,
                    '.select2-client-search': 'select2-client-search' in page.classes},
                'link_paths_not_requested': sorted(page.links),
                'forms': [{'method': f['method'], 'action_path': safe_path(f['action'] or url),
                    'csrf_hidden_present': any(i['name'] == '_token' and i.get('type','').lower() == 'hidden' for i in f['inputs'])} for f in page.forms],
                **previous.metadata(response.headers)}
            result['observations'].append(row)
            return page, form, destination

    try:
        addresses = {r[4][0] for r in socket.getaddrinfo('students.bsi.ac.id',443,type=socket.SOCK_STREAM)}
        if not addresses or not all(ipaddress.ip_address(a).is_global for a in addresses):
            raise ValueError('Unexpected DNS destination')
        page, _, _ = request(anonymous, ORIGIN + '/mahasiswa/beranda', 'anonymous')
        if page.profile_text_present:
            raise ValueError('Possible anonymous profile content; stop without further access')
        _, form, _ = request(authenticated, ORIGIN + '/login', 'pre-login')
        if not form or any('captcha' in i['name'].lower() for i in form['inputs']):
            raise ValueError('CAPTCHA or unsupported login form')
        if form['method'] != 'POST' or urllib.parse.urljoin(ORIGIN + '/login',form['action']) != ORIGIN + '/login':
            raise ValueError('Form outside scope')
        before = {(c.name,c.domain,c.path):c.value for c in jar if 'session' in c.name.lower()}
        credential = json.loads(private.read_text(encoding='utf-8'))
        fields = [(i['name'],i.get('value','')) for i in form['inputs'] if i.get('type','').lower() == 'hidden']
        fields.extend([('username',credential['username']),('password',credential['password'])])
        _, form, destination = request(authenticated, ORIGIN + '/login', 'login', fields)
        after = {(c.name,c.domain,c.path):c.value for c in jar if 'session' in c.name.lower()}
        if len(before) == len(after) == 1 and before.keys() == after.keys():
            result['session_cookie_value_change'] = 'changed' if before != after else 'unchanged'
        if form or urllib.parse.urlsplit(destination).path == '/login' or result['observations'][-1]['status'] in (401,403,422):
            raise ValueError('Login not established; no retry')
        page, _, _ = request(authenticated, ORIGIN + '/mahasiswa/beranda', 'authenticated', account=credential['username'])
        if page.profile_account_match is False:
            raise ValueError('Unexpected account profile; stop')
    except Exception as error:
        result['stopped'] = type(error).__name__ + '; no raw error exported'
    finally:
        private.unlink(missing_ok=True)
        output.write_text(json.dumps(result,indent=2),encoding='utf-8')
        print(json.dumps(result))


if __name__ == '__main__':
    if '--self-test' in __import__('sys').argv:
        assert safe_path('/mahasiswa/item/123456?token=private') == '/mahasiswa/item/{value}'
        assert safe_path('https://other.test/x?token=private') == '[external omitted]'
        page = Page('12345678')
        page.feed('<script>"select2-client-search"</script><span class="student-sidebar-profile__nim">NIM: 12345678</span><select class="js-example-templating"></select>')
        assert page.profile_account_match is True and 'js-example-templating' in page.classes
        assert 'select2-client-search' not in page.classes
        print('PASS: metadata redaction, actual DOM selectors, account-match boolean; no traffic.')
    else:
        run()
