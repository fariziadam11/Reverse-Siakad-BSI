"""Bounded login observation; private state outside the repository, no raw logs."""
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRIVATE = Path(os.environ['LOCALAPPDATA']) / 'web-pentest-agent/private/bsi-20261030'
SCOPE = HERE / 'login-scope.json'
ORIGINS = {'elearning': 'https://elearning.bsi.ac.id', 'student': 'https://students.bsi.ac.id'}


class FormParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.forms = []
        self.current = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form':
            self.current = {'action': attrs.get('action', ''), 'method': attrs.get('method', 'GET').upper(), 'inputs': []}
            self.forms.append(self.current)
        elif tag == 'input' and self.current is not None and attrs.get('name'):
            self.current['inputs'].append(attrs)

    def handle_endtag(self, tag):
        if tag == 'form':
            self.current = None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def allowed(scope, url, method, count):
    assert scope['authorization']
    assert datetime.now(timezone.utc) < datetime.fromisoformat(scope['expires_utc'])
    assert count < scope['max_requests'] <= 20
    assert method in scope['allowed_requests'].get(url, [])


def run(command):
    scope = json.loads(SCOPE.read_text(encoding='utf-8-sig'))
    state_path = PRIVATE / 'state.json'
    state = json.loads(state_path.read_text()) if state_path.exists() else {'count': 0, 'last': 0, 'forms': {}, 'stopped': False}
    assert not state['stopped'], 'Assessment stopped'
    jar = http.cookiejar.LWPCookieJar(str(PRIVATE / 'cookies.txt'))
    if Path(jar.filename).exists():
        jar.load(ignore_discard=True, ignore_expires=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
    start = time.monotonic()
    observations = []

    def persist():
        state_path.write_text(json.dumps(state), encoding='utf-8')
        jar.save(ignore_discard=True, ignore_expires=True)

    def request(url, fields=None):
        method = 'GET' if fields is None else 'POST'
        allowed(scope, url, method, state['count'])
        assert time.monotonic() - start < 110
        time.sleep(max(0, 1 - (time.time() - state['last'])))
        state['count'] += 1
        state['last'] = time.time()
        persist()
        body = None if fields is None else urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(url, body, {'User-Agent': 'web-pentest-agent/1.0', 'Origin': urllib.parse.urlsplit(url).scheme + '://' + urllib.parse.urlsplit(url).netloc, 'Referer': url}, method=method)
        try:
            response = opener.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if response.code == 429 or response.code >= 500:
                state['stopped'] = True
                persist()
                raise ValueError('Rate limit or server error')
            text = response.read(2_000_001)
            assert len(text) <= 2_000_000
            parser = FormParser()
            parser.feed(text.decode('utf-8', errors='replace'))
            login = next((f for f in parser.forms if {'username', 'password'} <= {i['name'] for i in f['inputs']}), None)
            headers = response.headers
            location = urllib.parse.urljoin(url, headers.get('Location', '')) if headers.get('Location') else None
            row = {'url': url, 'method': method, 'status': response.code, 'login_form_present': login is not None,
                   'redirect_present': location is not None, 'redirect_in_scope': location in scope['allowed_requests'] if location else None,
                   'cache_control': headers.get('Cache-Control', ''), 'framing_header_present': bool(headers.get('X-Frame-Options')),
                   'csp_header_present': bool(headers.get('Content-Security-Policy')),
                   'cookie_attributes': [{'secure': 'secure' in c.lower().split('; '), 'httponly': 'httponly' in c.lower().split('; '), 'samesite_present': 'samesite=' in c.lower(), 'path_present': 'path=' in c.lower(), 'domain_present': 'domain=' in c.lower()} for c in headers.get_all('Set-Cookie', [])]}
            if login:
                login['action'] = urllib.parse.urljoin(url, login['action'])
                row['form_action_allowed'] = login['method'] in scope['allowed_requests'].get(login['action'], [])
                row['input_fields'] = [i['name'] for i in login['inputs']]
                row['captcha_field_present'] = any('captcha' in i['name'].lower() for i in login['inputs'])
                state['forms'][url] = login
                # Keep only a local CAPTCHA fragment; never retain the full login response.
                import re
                svg = re.search(r'<svg\b[^>]*>.*?</svg>', text.decode('utf-8', errors='replace'), re.S | re.I)
                if row['captcha_field_present'] and svg:
                    (PRIVATE / (urllib.parse.urlsplit(url).hostname + '-captcha.svg')).write_text(svg.group(), encoding='utf-8')
            observations.append(row)
        persist()
        return location

    if command['action'] == 'inspect':
        for origin in ORIGINS.values():
            request(origin + '/login')
    elif command['action'] == 'login':
        origin = ORIGINS[command['portal']]
        url = origin + '/login'
        form = state['forms'][url]
        assert form['method'] == 'POST'
        assert form['action'] == url
        fields = [(i['name'], i.get('value', '')) for i in form['inputs'] if i.get('type', '').lower() == 'hidden']
        fields += [('username', command['username']), ('password', command['password'])]
        captcha_names = [i['name'] for i in form['inputs'] if 'captcha' in i['name'].lower() and i.get('type', '').lower() != 'hidden']
        if captcha_names:
            assert len(captcha_names) == 1 and command.get('captcha'), 'Manual CAPTCHA required'
            fields.append((captcha_names[0], command['captcha']))
        destination = request(url, fields)
        home = origin + ('/user/dashboard' if command['portal'] == 'elearning' else '/mahasiswa/beranda')
        if destination in (None, home):
            request(home)
    else:
        raise ValueError('Unknown action')
    output = HERE / 'login-observations.json'
    prior = json.loads(output.read_text()) if output.exists() else []
    output.write_text(json.dumps(prior + observations, indent=2), encoding='utf-8')
    print(json.dumps({'requests_used': state['count'], 'observations': observations}))


if __name__ == '__main__':
    try:
        run(json.loads(sys.stdin.readline()))
    except Exception as error:
        print('Stopped safely: ' + type(error).__name__ + '; no raw response or exception exported.')
        sys.exit(1)
