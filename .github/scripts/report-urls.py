#!/usr/bin/env python3
"""화면(JS·JSP)이 부르는 주소를 서버(Controller 등)의 주소와 대조해 표로 낸다. 서버에 없는 주소가 있어도 실패하지 않는다.

사용: report-urls.py [저장소 폴더] [--base <기준 커밋>]
--base를 주면 그 커밋과 비교해 새로 빠진 주소는 경고로 알리고, 해결된 주소를 따로 적는다.
GITHUB_STEP_SUMMARY가 있으면 표를 그곳에도 쓴다.

코드를 실행하지 않고 글자로 읽는다. 변수로 이어 붙인 주소는 끝까지 못 읽어 '못 읽은 호출'에 따로 적는다.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

CTX_NAMES = {'ctx', 'pageContext.request.contextPath'}
CTX = r'\$\{(?:ctx|pageContext\.request\.contextPath)\}'
ADDRESS = re.compile(r'/[a-z][\w.*-]*(?:/[\w.*-]+)*')
EL = re.compile(r'\$\{[^}]*\}')
MAPPING = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\b\s*(\((?:[^()"]|"(?:[^"\\]|\\.)*")*\))?')
MAPPING_NOT_PATH = re.compile(r'\b(?:produces|consumes|params|headers|name|method)\s*=\s*(?:\{[^}]*\}|"[^"]*"|[\w.]+)')
TERNARY = re.compile(r'''[^?'"`]+\?\s*(['"])([^'"]*)\1\s*:\s*(['"])([^'"]*)\3''')
REGEX_BEFORE = set('(,=:[!&|?{};+-*%<>~^')
REGEX_BEFORE_WORD = re.compile(r'\b(?:return|typeof|case|void|delete|in|of)\s*$')


class Expr(str):
    """템플릿 문자열의 ${…} 안쪽"""


def scan(src, js=True):
    """주석(JS는 정규식도)을 건너뛰며 문자열을 모은다.
    돌려주는 값: [(시작 위치, 조각들)], 주석을 지운 코드, 닫히지 않은 템플릿의 위치(문법 검사를 통과한 JS면 잘못 읽은 것이다)"""
    strings = []
    broken = []
    code = list(src)
    n = len(src)

    def erase(a, b):
        for k in range(a, b):
            if code[k] != '\n':
                code[k] = ' '

    def read_string(i):
        start = i
        quote = src[i]
        i += 1
        parts = ['']
        while i < n:
            c = src[i]
            if c == '\\':
                parts[-1] += src[i + 1:i + 2]
                i += 2
            elif c == quote or (c == '\n' and quote != '`'):
                return i + 1, parts
            elif quote == '`' and src.startswith('${', i):
                end = read_code(i + 2, True)
                parts += [Expr(src[i + 2:end - 1].strip()), '']
                i = end
            else:
                parts[-1] += c
                i += 1
        broken.append(start)
        return i, parts

    def skip_regex(start):
        i = start + 1
        in_class = False
        while i < n and src[i] != '\n':
            c = src[i]
            if c == '\\':
                i += 1
            elif c == '[':
                in_class = True
            elif c == ']':
                in_class = False
            elif c == '/' and not in_class:
                return i + 1
            i += 1
        return start + 1  # 줄이 끝나도 안 닫히면 정규식이 아니다

    def read_code(i, in_template):
        depth = 0
        prev = ''  # 바로 앞의 글자로 정규식과 나눗셈을 가른다
        while i < n:
            c = src[i]
            if src.startswith('//', i):
                end = src.find('\n', i)
                end = n if end < 0 else end
                erase(i, end)
                i = end
            elif src.startswith('/*', i):
                end = src.find('*/', i + 2)
                end = n if end < 0 else end + 2
                erase(i, end)
                i = end
            elif c in '"\'' or (js and c == '`'):
                start = i
                i, parts = read_string(i)
                strings.append((start, parts))
                prev = c
            elif js and c == '/' and (prev in REGEX_BEFORE or not prev
                                      or REGEX_BEFORE_WORD.search(src[max(0, i - 20):i])):
                i = skip_regex(i)
                prev = c
            else:
                if c == '{':
                    depth += 1
                elif c == '}':
                    if in_template and depth == 0:
                        return i + 1
                    depth -= 1
                if not c.isspace():
                    prev = c
                i += 1
        return i

    read_code(0, False)
    return strings, ''.join(code), broken[0] if broken else None


def normalize(text):
    """주소처럼 보이면 쿼리·해시·끝의 /를 뗀 주소를, 아니면 None을 돌려준다"""
    text = re.split(r'[?#]', text, maxsplit=1)[0].rstrip('/')
    if not ADDRESS.fullmatch(text) or text.startswith('/resources/'):
        return None
    return text


def read(path):
    return path.read_text(encoding='utf-8', errors='replace')


def line_of(src, pos):
    return src.count('\n', 0, pos) + 1


def first_arg(code, i):
    """i에서 시작하는 첫 번째 인자의 글자"""
    depth = 0
    for k in range(i, len(code)):
        c = code[k]
        if c in '([{':
            depth += 1
        elif c in ')]}' and depth:
            depth -= 1
        elif c in ',)' and not depth:
            return code[i:k].strip()
    return code[i:].strip()


def expand(parts, consts):
    """문자열 조각을 글자로 편다. ${조건 ? 'a' : 'b'}는 두 갈래로, 값을 모르는 ${…}는 *로"""
    texts = ['']
    for part in parts:
        options = [part]
        if isinstance(part, Expr):
            ternary = TERNARY.fullmatch(part)
            options = [ternary.group(2), ternary.group(4)] if ternary \
                else [consts.get(part, '' if part in CTX_NAMES else '*')]
        texts = [text + option for text in texts for option in options]
    return texts


def scan_js(src, name, calls, unread):
    strings, code, broken = scan(src)
    if broken is not None:
        unread.append((name, line_of(src, broken), '(여기부터 파일 끝까지 못 읽음)'))
    declared = defaultdict(set)
    for pos, parts in strings:
        m = re.search(r'\b(?:const|let|var)\s+(\w+)\s*=\s*$', src[max(0, pos - 60):pos])
        if m and len(parts) == 1:
            declared[m.group(1)].add(parts[0])
    # 같은 이름이 함수마다 다른 값이면 어느 것인지 모른다
    consts = {name_: next(iter(values)) for name_, values in declared.items() if len(values) == 1}

    for pos, parts in strings:
        api = re.search(r'\bapi\.(get|post)\(\s*$', src[max(0, pos - 30):pos])
        method = api.group(1).upper() if api else None
        # 'a/' + id 처럼 뒤에 이어 붙이면 그 자리와 뒤의 주소를 모른다
        joined_after = re.match(r'''(?:'[^'\n]*'|"[^"\n]*")\s*\+''', src[pos:pos + 300])
        joined_before = re.search(r'\+\s*$', src[max(0, pos - 40):pos])
        if joined_before and not re.search(r'[cC]tx[^;,(]*\+\s*$', src[max(0, pos - 40):pos]):
            continue
        texts = expand(parts, consts)
        if api and (joined_after or texts[0].startswith('*')):
            unread.append((name, line_of(src, pos), ''.join('${' + p + '}' if isinstance(p, Expr) else p for p in parts)))
        for text in texts:
            address = normalize(text + ('*' if joined_after else ''))
            if address:
                calls[(None if joined_after else method, address)].append((name, line_of(src, pos)))

    for m in re.finditer(r'''\bapi\.(get|post)\(\s*(?=[^\s`'"])''', code):
        arg = first_arg(code, m.end())
        address = normalize(consts[arg]) if arg in consts else None
        if address:
            calls[(m.group(1).upper(), address)].append((name, line_of(src, m.start())))
        else:
            unread.append((name, line_of(src, m.start()), arg))


def scan_jsp(src, name, calls, unread):
    def keep_lines(m):
        return re.sub(r'[^\n]', ' ', m.group(0))

    def erase_js_comments(m):
        return m.group(1) + scan(m.group(2))[1] + m.group(3)

    src = re.sub(r'<%--.*?--%>|<!--.*?-->', keep_lines, src, flags=re.S)
    src = re.sub(r'(<script\b[^>]*>)(.*?)(</script>)', erase_js_comments, src, flags=re.S)

    def add(text, pos):
        address = normalize(EL.sub('*', text))
        if address:
            calls[(None, address)].append((name, line_of(src, pos)))

    # ${ctx}/주소, ${ctx}${조건 ? '/가' : '/나'}
    for m in re.finditer(CTX + r'''((?:/|\$\{)(?:[^"'`\s<>$]|\$\{[^}]*\})*)''', src):
        # "${ctx}/a/" + id 처럼 뒤에 이어 붙이면 그 자리는 값을 모른다
        joined = re.match(r'''["'`]\s*\+''', src[m.end():m.end() + 20])
        add(m.group(1) + ('*' if joined else ''), m.start())
    for m in re.finditer(CTX + r'\$\{[^}]*\}', src):
        for quoted in re.findall(r"'(/[^']*)'", m.group(0)):
            add(quoted, m.start())
    # <ui:nav path="/주소">, <c:url value="/주소">, 컨텍스트 경로 없이 쓴 href="/주소"
    for m in re.finditer(r'''(?:<ui:nav\b[^>]*?\bpath|<c:url\b[^>]*?\bvalue|\b(?:href|action))\s*=\s*["'](/[^"']*)["']''', src):
        add(m.group(1), m.start(1))
    # 직접 쓴 <script>
    for m in re.finditer(r'<script\b[^>]*>(.*?)</script>', src, flags=re.S):
        pad = '\n' * (line_of(src, m.start(1)) - 1)
        scan_js(pad + EL.sub(lambda e: '0' + '\n' * e.group(0).count('\n'), m.group(1)), name, calls, unread)


def relative(path, root):
    return ' '.join(path.relative_to(root).as_posix().splitlines())


def collect_calls(root):
    """화면이 부르는 주소: {(방식 또는 None, 주소): [(파일, 줄)]}, 못 읽은 호출: [(파일, 줄, 인자)]"""
    calls, unread = defaultdict(list), []
    web = root / 'src/main/webapp'
    for path in sorted(web.rglob('*.js')):
        if '/lib/' not in path.as_posix() and '.min.' not in path.name:
            scan_js(read(path), relative(path, root), calls, unread)
    for pattern in ('*.jsp', '*.jspf', '*.tag'):
        for path in sorted(web.rglob(pattern)):
            scan_jsp(read(path), relative(path, root), calls, unread)
    # 같은 주소를 api.get으로 부르는 곳이 있으면, 변수에 담아 둔 쪽(방식 모름)은 합친다
    for (method, address) in [k for k in calls if k[0] is None]:
        known = [k for k in calls if k[0] and k[1] == address]
        if known:
            calls[known[0]].extend(calls.pop((None, address)))
    return calls, sorted(set(unread))


def mapping_paths(args):
    """매핑의 주소들. 주소를 안 적었으면 [''], 상수처럼 글자로 못 읽으면 []"""
    rest = MAPPING_NOT_PATH.sub('', args or '')
    return re.findall(r'"([^"]*)"', rest) or ([] if re.search(r'\w', rest) else [''])


def collect_routes(root):
    """서버가 받는 주소: {(방식 또는 None, 주소 패턴)}"""
    routes = set()
    for path in (root / 'src/main/java').rglob('*.java'):
        _, code, _ = scan(read(path), js=False)
        if not re.search(r'@(?:Rest)?Controller\b', code):
            continue
        parts = re.split(r'\bclass\s+\w+', code, maxsplit=1)
        if len(parts) < 2:
            continue
        head, body = parts
        class_mapping = MAPPING.search(head)
        bases = mapping_paths(class_mapping.group(2)) if class_mapping else ['']
        for m in MAPPING.finditer(body):
            kind, args = m.group(1), m.group(2) or ''
            methods = (re.findall(r'\b(GET|POST|PUT|PATCH|DELETE)\b', re.sub(r'"[^"]*"', '', args)) or [None]
                       if kind == 'Request' else [kind.upper()])
            for base in bases:
                for tail in mapping_paths(args):
                    joined = re.sub(r'/+', '/', f'/{base}/{tail}').rstrip('/') or '/'
                    routes.update((method, joined) for method in methods)

    spring = root / 'src/main/webapp/WEB-INF/spring'
    security = re.sub(r'<!--.*?-->', '', read(spring / 'security-context.xml'), flags=re.S)
    for found in re.findall(r'\b(?:login-processing-url|logout-url)="([^"]+)"', security):
        routes.add(('POST', found))
    servlet = re.sub(r'<!--.*?-->', '', read(spring / 'appServlet/servlet-context.xml'), flags=re.S)
    for found in re.findall(r'<resources\s+mapping="([^"]+)"', servlet):
        routes.add(('GET', found))
    return routes


def segment_matches(call, route):
    if '{' in route or call == '*':
        return True
    if '*' in call:
        return re.fullmatch(re.escape(call).replace(r'\*', '.*'), route) is not None
    return call == route


def path_matches(address, route, prefix=False):
    if route.endswith('/**'):
        return address.startswith(route[:-2])
    calls, routes = address.split('/'), route.split('/')
    same_length = len(calls) <= len(routes) if prefix else len(calls) == len(routes)
    return same_length and all(segment_matches(a, b) for a, b in zip(calls, routes))


def fixed_text_on_variable(address, route):
    return any('{' in b and '*' not in a for a, b in zip(address.split('/'), route.split('/')))


def compare(calls, routes):
    """서버에 없는 호출: {(방식, 주소): 비고}. 주소는 있는데 방식이 다르면 비고에 서버의 방식을 적는다"""
    missing = {}
    for (method, address), places in calls.items():
        same_path = [(m, route) for m, route in routes if path_matches(address, route)]
        if not same_path and method is None and all(f.endswith('.js') for f, _ in places):
            # JS 변수에 담아 둔 주소는 뒤에 더 이어 붙일 수 있어 앞부분만 맞아도 있는 것으로 본다
            same_path = [(m, route) for m, route in routes if path_matches(address, route, prefix=True)]
        same_method = [(m, route) for m, route in same_path if method is None or m in (method, None)]
        if not same_path:
            missing[(method, address)] = ''
        elif not same_method:
            missing[(method, address)] = '서버는 ' + '·'.join(sorted({m for m, _ in same_path})) + '만 받음'
        elif all(fixed_text_on_variable(address, route) for _, route in same_method):
            # /notices/pinned가 /notices/{id}에 걸리는 경우: 받는 메서드가 ID로 읽으려다 실패한다
            missing[(method, address)] = f'서버에는 `{same_method[0][1]}`뿐임'
    return missing


def not_in(keys, others):
    """others에 없는 것. 방식을 모르는 쪽(None)이 있으면 주소만 맞춰 본다"""
    return sorted((key for key in keys if not any(
        key[1] == other[1] and (key[0] == other[0] or None in (key[0], other[0])) for other in others)), key=sort_key)


def read_tree(root):
    calls, unread = collect_calls(root)
    routes = collect_routes(root)
    if not calls or not routes:
        sys.exit(f'화면 호출 {len(calls)}개, 서버 주소 {len(routes)}개입니다. 폴더 구조가 바뀌었는지 보세요.')
    return calls, unread, routes, compare(calls, routes)


def sort_key(item):
    method, address = item
    return address, method or ''


def table(rows, calls, notes):
    lines = ['| 방식 | 주소 | 부르는 곳 | 비고 |', '|---|---|---|---|']
    for key in sorted(rows, key=sort_key):
        method, address = key
        places = sorted(set(calls.get(key, [])))
        where = ', '.join(f'`{f.replace("src/main/webapp/", "")}:{n}`' for f, n in places[:2])
        if len(places) > 2:
            where += f' 외 {len(places) - 2}곳'
        lines.append(f'| {method or "-"} | `{address}` | {where} | {notes.get(key, "")} |')
    return lines


def report(calls, unread, routes, missing, base):
    out = ['## 화면-서버 주소 대조', '']
    summary = f'화면이 부르는 주소 {len(calls)}개 중 **{len(missing)}개가 서버에 없습니다.** 병합을 막지 않는 보고입니다.'
    if base is not None:
        base_calls, base_missing = base
        added = not_in(missing, base_missing)
        fixed = not_in(base_missing, missing)
        summary += f' 이 PR로 새로 빠진 주소 {len(added)}개, 해결된 주소 {len(fixed)}개입니다.'
    out += [summary, '']
    if base is not None:
        if added:
            out += [f'### 이 PR로 새로 빠진 주소 {len(added)}개', '']
            out += table(added, calls, missing) + ['']
        if fixed:
            out += [f'### 이 PR로 해결된 주소 {len(fixed)}개', '']
            out += table(fixed, base_calls, base_missing) + ['']

    by_module = defaultdict(list)
    for key in missing:
        by_module[key[1].split('/')[1]].append(key)
    if by_module:
        out += ['### 아직 서버에 없는 주소', '']
        for module in sorted(by_module, key=lambda m: (-len(by_module[m]), m)):
            keys = by_module[module]
            out += [f'<details><summary><code>/{module}</code> {len(keys)}개</summary>', '']
            out += table(keys, calls, missing) + ['', '</details>', '']

    if unread:
        out += [f'<details><summary>못 읽은 호출 {len(unread)}개 (변수로 만든 주소)</summary>', '',
                '| 부르는 곳 | 인자 |', '|---|---|']
        for name, line, arg in unread:
            shown = ' '.join(arg.split()).replace('|', '\\|').replace('`', "'")
            out.append(f'| `{name.replace("src/main/webapp/", "")}:{line}` | `{shown}` |')
        out += ['', '</details>', '']

    out += [f'서버 주소 {len(routes)}개와 코드를 글자로 읽어 맞춘 값입니다. 방식 `-`는 링크나 변수에 담긴 주소라 방식을 모르는 것이고, '
            'JS 변수에 담긴 주소는 뒤에 더 이어 붙일 수 있어 앞부분만 맞아도 있는 것으로 봅니다. '
            '서버가 응답에 담아 주는 주소(알림 링크)와 Controller의 `redirect:`는 보지 않습니다.']
    return '\n'.join(out) + '\n'


def command_value(text):
    """GitHub Actions 명령의 속성 값에 쓸 수 있게 고친다"""
    return text.replace('%', '%25').replace(':', '%3A').replace(',', '%2C')


def main():
    parser = argparse.ArgumentParser(description='화면-서버 주소 대조 보고')
    parser.add_argument('root', nargs='?', default='.')
    parser.add_argument('--base')
    args = parser.parse_args()
    root = Path(args.root).resolve()

    calls, unread, routes, missing = read_tree(root)
    base = None
    if args.base:
        with tempfile.TemporaryDirectory() as tmp:
            archive = subprocess.run(['git', '-C', str(root), 'archive', args.base, 'src/main'],
                                     check=True, stdout=subprocess.PIPE)
            subprocess.run(['tar', '-x', '-C', tmp], check=True, input=archive.stdout)
            base_calls, _, _, base_missing = read_tree(Path(tmp))
        base = (base_calls, base_missing)

    text = report(calls, unread, routes, missing, base)
    print(text)
    summary_file = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary_file:
        with open(summary_file, 'a', encoding='utf-8') as f:
            f.write(text)
    if base is not None:
        for key in not_in(missing, base[1]):
            file, line = sorted(set(calls[key]))[0]
            label = f'{key[0]} {key[1]}' if key[0] else key[1]
            note = f' ({missing[key]})' if missing[key] else ''
            print(f'::warning file={command_value(file)},line={line},title=서버에 없는 주소::{label}{note}')


if __name__ == '__main__':
    main()
