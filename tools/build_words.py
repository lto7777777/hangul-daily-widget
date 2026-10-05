"""Turn data/5324kor.txt (a copy of the kor web app's list) into the widget's word list.

Input lines look like ' 사람           \t Person '. A meaning that did not fit
spilled onto the next line without the leading space; those continuation lines
are joined back onto the entry above. Output is UTF-8 TSV, one word per line,
in the source's frequency order: korean <TAB> romanization <TAB> meaning.
"""

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from romanize import romanize  # noqa: E402

HERE = Path(__file__).resolve().parent
DEFAULT_SRC = HERE.parent / 'data' / '5324kor.txt'
DEFAULT_OUT = HERE.parent / 'app' / 'src' / 'main' / 'assets' / 'words.tsv'

HANGUL = re.compile('[가-힣]')
HOMOGRAPH_NUMBER = re.compile(r'\s+\d+$')  # '기 13' -> '기'


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


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--src', type=Path, default=DEFAULT_SRC)
    ap.add_argument('--out', type=Path, default=DEFAULT_OUT)
    args = ap.parse_args(argv)

    lines = args.src.read_text(encoding='utf-8').splitlines()
    words, problems = parse(lines)
    for p in problems:
        print('warning:', p, file=sys.stderr)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open('w', encoding='utf-8', newline='\n') as fh:
        fh.write('# korean\tromanization\tmeaning (frequency order, from 5324kor.txt)\n')
        for korean, meaning in words:
            fh.write(f'{korean}\t{romanize(korean)}\t{meaning}\n')
    print(f'wrote {len(words)} words to {args.out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
