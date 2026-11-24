"""Acquire only the eight authorized static files; never execute target scripts."""
import hashlib
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('assessment', 'C:/Users/Farizi Adam/.codex/skills/web-pentest-agent/scripts/assessment.py')
assessment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assessment)


def destination(url, root):
    parsed = urllib.parse.urlsplit(url)
    name = parsed.path.rsplit('/', 1)[-1]
    if parsed.scheme != 'https' or parsed.netloc != 'students.bsi.ac.id' or parsed.query or parsed.fragment:
        raise ValueError('Unexpected destination')
    if parsed.path != '/assets/js/' + name or not name.endswith('.js') or '\\' in name:
        raise ValueError('Unexpected asset path')
    return root / name


def run():
    scope = json.loads((HERE / 'frontend-scope.json').read_text(encoding='utf-8-sig'))
    urls = scope['allowed_urls']
    assessment.check_scope(scope, urls)
    root = Path(os.environ['LOCALAPPDATA']) / 'web-pentest-agent/private/studentv2-frontend-20261030/sources'
    outputs = [destination(url, root) for url in urls]
    manifest_path = HERE / 'frontend-manifest.json'
    if manifest_path.exists() or any(p.exists() for p in outputs):
        raise ValueError('Existing acquisition; refusing duplicate requests')
    addresses = {r[4][0] for r in socket.getaddrinfo('students.bsi.ac.id', 443, type=socket.SOCK_STREAM)}
    if not addresses or not all(ipaddress.ip_address(a).is_global for a in addresses):
        raise ValueError('Unexpected DNS destination')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), assessment.NoRedirect())
    manifest = {'requests_sent': 0, 'dns_addresses': sorted(addresses), 'files': [], 'stopped': ''}
    started, previous = time.monotonic(), 0.0
    try:
        for url, output in zip(urls, outputs):
            time.sleep(max(0, 1 - (time.monotonic() - previous)))
            assessment.check_scope(scope, [url])
            if time.monotonic() - started >= 110 or manifest['requests_sent'] >= 8:
                raise ValueError('Budget exhausted')
            previous = time.monotonic()
            manifest['requests_sent'] += 1
            request = urllib.request.Request(url, headers={'User-Agent': 'web-pentest-agent/1.0'}, method='GET')
            try:
                response = opener.open(request, timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                if response.code != 200:
                    raise ValueError('Non-200 response; no redirect or retry')
                body = response.read(2_000_001)
                mime = response.headers.get_content_type()
                if len(body) > 2_000_000 or mime not in ('application/javascript', 'text/javascript', 'application/x-javascript', 'text/plain'):
                    raise ValueError('Unexpected asset size or type')
                if body.lstrip().lower().startswith((b'<!doctype html', b'<html')):
                    raise ValueError('Unexpected HTML')
                output.write_bytes(body)
                manifest['files'].append({'url': url, 'file': output.name, 'status': 200,
                    'bytes': len(body), 'sha256': hashlib.sha256(body).hexdigest(), 'content_type': mime})
    except Exception as error:
        manifest['stopped'] = type(error).__name__ + '; no retry or raw error exported'
    finally:
        manifest_path.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        print(json.dumps(manifest))


if __name__ == '__main__':
    if '--self-test' in __import__('sys').argv:
        assert destination('https://students.bsi.ac.id/assets/js/main.js', Path('sources')).name == 'main.js'
        for bad in ('http://students.bsi.ac.id/assets/js/main.js', 'https://other.test/assets/js/main.js',
                    'https://students.bsi.ac.id/login', 'https://students.bsi.ac.id/assets/js/main.js?token=x'):
            try:
                destination(bad, Path('sources'))
            except ValueError:
                pass
            else:
                raise AssertionError('Unexpected destination accepted')
        print('PASS: exact origin/path, no query; no traffic.')
    else:
        run()
