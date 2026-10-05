"""Turn data/5324kor.txt (a copy of the kor web app's list) into the widget's word list.

Input lines look like ' 사람           \t Person '. A meaning that did not fit
spilled onto the next line without the leading space; those continuation lines
are joined back onto the entry above.

Two hand-written files are merged in:
  data/corrections.tsv  key <TAB> corrected meaning <TAB> why
  data/examples.tsv     key <TAB> example <TAB> translation <TAB> parts
A key is the word as spelled in the list; a later entry with the same spelling
(a homograph) is 'word#2', 'word#3', ... in list order. Parts split the example
into pieces: '안녕=peace | 하=do, be | 세요=polite ending'. The pieces must join
back into the example, and a verb or adjective must name its dictionary form in
a gloss, e.g. '가요=go (가다), polite'.

Output is UTF-8 TSV in the source's frequency order:
korean, romanization, meaning, example, example romanization, translation, parts
where parts is 'piece=gloss|piece=gloss'.
"""

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from romanize import romanize  # noqa: E402

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / 'data'
DEFAULT_SRC = DATA / '5324kor.txt'
DEFAULT_EXAMPLES = DATA / 'examples.tsv'
DEFAULT_CORRECTIONS = DATA / 'corrections.tsv'
DEFAULT_OUT = HERE.parent / 'app' / 'src' / 'main' / 'assets' / 'words.tsv'

HANGUL = re.compile('[가-힣]')
HOMOGRAPH_NUMBER = re.compile(r'\s+\d+$')  # '기 13' -> '기'
NOT_IN_PIECES = re.compile(r'[\s.,?!~…\'"“”‘’:;()\-]')
MAX_EXAMPLE_SYLLABLES = 20


def clean_meaning(text):
    text = text.replace("\\'", "'")
    text = re.sub(r'\s+', ' ', text).strip()
    text = re.sub(r'\s+([,;.)])', r'\1', text)
    text = re.sub(r'\(\s+', '(', text)
    text = text.rstrip(',;')
    if text[:1].isalpha() and text[:1].islower():
        text = text[0].upper() + text[1:]
    return text


def parse(lines):
    """Return [(korean, meaning)] in source order, plus a list of problems."""
    entries = []
    problems = []
    for lineno, raw in enumerate(lines, start=1):
        if lineno == 1 or not raw.strip():
            continue  # header or blank
        fields = [f.strip() for f in raw.split('\t')]
        if raw.startswith(' '):
            korean = HOMOGRAPH_NUMBER.sub('', fields[0])
            meaning = ' '.join(f for f in fields[1:] if f)
            if not HANGUL.search(korean):
                problems.append(f'line {lineno}: no Hangul in key {korean!r}')
                continue
            entries.append([korean, meaning, lineno])
        elif entries:
            entries[-1][1] += ' ' + ' '.join(f for f in fields if f)
        else:
            problems.append(f'line {lineno}: continuation before any entry')

    words = []
    seen = set()
    for korean, meaning, lineno in entries:
        meaning = clean_meaning(meaning)
        if not meaning:
            problems.append(f'line {lineno}: empty meaning for {korean!r}')
            continue
        key = (korean, meaning.lower())
        if key in seen:
            continue  # exact duplicate; homographs with other meanings are kept
        seen.add(key)
        words.append((korean, meaning))
    return words, problems


def entry_keys(words):
    """'word' for the first entry with that spelling, 'word#n' for the n-th."""
    seen = {}
    keys = []
    for korean, _ in words:
        n = seen.get(korean, 0) + 1
        seen[korean] = n
        keys.append(korean if n == 1 else f'{korean}#{n}')
    return keys


def read_rows(lines, columns, name):
    """Tab-separated rows, skipping blank and '#' lines. Returns [(lineno, fields)], problems."""
    rows, problems = [], []
    for lineno, raw in enumerate(lines, start=1):
        if not raw.strip() or raw.startswith('#'):
            continue
        fields = [f.strip() for f in raw.split('\t')]
        if len(fields) != columns or not all(fields):
            problems.append(f'{name} line {lineno}: expected {columns} non-empty columns')
            continue
        rows.append((lineno, fields))
    return rows, problems


def parse_parts(text):
    parts = []
    for chunk in text.split('|'):
        piece, sep, gloss = chunk.partition('=')
        parts.append((piece.strip(), gloss.strip() if sep else ''))
    return parts


def check_example(korean, example, translation, parts):
    """Problems with one example, as short strings (empty list if it is fine)."""
    problems = []
    if not HANGUL.search(example):
        problems.append('example has no Hangul')
    if len(HANGUL.findall(example)) > MAX_EXAMPLE_SYLLABLES:
        problems.append(f'example longer than {MAX_EXAMPLE_SYLLABLES} syllables')
    if not translation:
        problems.append('empty translation')
    for piece, gloss in parts:
        if not piece or not gloss:
            problems.append(f'part {piece!r} needs both a piece and a gloss')
        if '=' in gloss:
            problems.append(f'gloss for {piece!r} contains "="')
    joined = ''.join(piece for piece, _ in parts)
    if joined != NOT_IN_PIECES.sub('', example):
        problems.append(f'parts join to {joined!r}, example is {example!r}')
    glosses = ' '.join(gloss for _, gloss in parts)
    if korean.endswith('다') and len(korean) > 1:
        if f'({korean})' not in glosses:
            problems.append(f'no gloss names the dictionary form ({korean})')
    elif korean.replace(' ', '') not in example.replace(' ', '') and f'({korean})' not in glosses:
        problems.append(f'{korean!r} is not in the example and no gloss names it')
    return problems


def apply_corrections(words, rows):
    keys = entry_keys(words)
    index = {k: i for i, k in enumerate(keys)}
    words = list(words)
    problems = []
    for lineno, (key, meaning, _why) in rows:
        if key not in index:
            problems.append(f'corrections line {lineno}: unknown key {key!r}')
            continue
        korean, _ = words[index[key]]
        words[index[key]] = (korean, meaning)
    return words, problems


def collect_examples(words, rows):
    """{entry index: (example, translation, [(piece, gloss)])}, problems."""
    keys = entry_keys(words)
    index = {k: i for i, k in enumerate(keys)}
    found, problems = {}, []
    for lineno, (key, example, translation, parts_text) in rows:
        where = f'examples line {lineno} ({key})'
        if key not in index:
            problems.append(f'{where}: unknown key')
            continue
        if index[key] in found:
            problems.append(f'{where}: second example for the same word')
            continue
        parts = parse_parts(parts_text)
        bad = check_example(words[index[key]][0], example, translation, parts)
        problems.extend(f'{where}: {p}' for p in bad)
        if not bad:
            found[index[key]] = (example, translation, parts)
    return found, problems


def encode_parts(parts):
    # No per-piece romanization: a piece on its own reads wrong (있 -> "it",
    # though 있어요 sounds "isseoyo"). The whole example is romanized instead.
    return '|'.join(f'{piece}={gloss}' for piece, gloss in parts)


def build(src_lines, example_lines, correction_lines):
    """All the merging; returns (rows for words.tsv, problems)."""
    words, problems = parse(src_lines)
    corr_rows, p = read_rows(correction_lines, 3, 'corrections')
    problems += p
    words, p = apply_corrections(words, corr_rows)
    problems += p
    ex_rows, p = read_rows(example_lines, 4, 'examples')
    problems += p
    examples, p = collect_examples(words, ex_rows)
    problems += p

    out = []
    for i, (korean, meaning) in enumerate(words):
        if i in examples:
            example, translation, parts = examples[i]
            out.append((korean, romanize(korean), meaning,
                        example, romanize(example), translation, encode_parts(parts)))
        else:
            out.append((korean, romanize(korean), meaning, '', '', '', ''))
    return out, problems


def read_lines(path):
    return path.read_text(encoding='utf-8').splitlines() if path.exists() else []


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--src', type=Path, default=DEFAULT_SRC)
    ap.add_argument('--examples', type=Path, default=DEFAULT_EXAMPLES)
    ap.add_argument('--corrections', type=Path, default=DEFAULT_CORRECTIONS)
    ap.add_argument('--out', type=Path, default=DEFAULT_OUT)
    args = ap.parse_args(argv)

    rows, problems = build(read_lines(args.src), read_lines(args.examples),
                           read_lines(args.corrections))
    if problems:
        for p in problems:
            print('error:', p, file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open('w', encoding='utf-8', newline='\n') as fh:
        fh.write('# korean\troman\tmeaning\texample\texample roman\ttranslation\tparts\n')
        for row in rows:
            fh.write('\t'.join(row) + '\n')
    covered = sum(1 for r in rows if r[3])
    first_gap = next((i for i, r in enumerate(rows) if not r[3]), len(rows))
    print(f'wrote {len(rows)} words to {args.out}; {covered} have examples, '
          f'the first {first_gap} without a gap')
    return 0


if __name__ == '__main__':
    sys.exit(main())
