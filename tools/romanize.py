"""Revised Romanization of Korean with the common sound-change rules.

Covers liaison, final-consonant neutralization, double finals, nasalization,
liquidization, aspiration with ㅎ, ㅎ-dropping and palatalization.

Known gaps (rule-based, no dictionary):
- ㄴ-insertion in compounds (솜이불 -> somibul, should be somnibul)
- boundary neutralization before content words (맛없다 -> maseopda, should be madeopda)
- ㄹ -> ㄴ after ㄴ in some Sino-Korean words (생산량 -> saengsallyang, should be saengsannyang)
Deliberate deviation from official RR: ㅎ-aspiration is applied to nouns too
(축하 -> chuka, official RR keeps chukha), because this is a pronunciation aid.
"""

CHO = ['ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
       'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']
JONG = ['', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ',
        'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ']
VOWEL_ROMAN = ['a', 'ae', 'ya', 'yae', 'eo', 'e', 'yeo', 'ye', 'o', 'wa', 'wae',
               'oe', 'yo', 'u', 'wo', 'we', 'wi', 'yu', 'eu', 'ui', 'i']
VOWEL_I = 20  # ㅣ

INITIAL_ROMAN = {
    'ㄱ': 'g', 'ㄲ': 'kk', 'ㄴ': 'n', 'ㄷ': 'd', 'ㄸ': 'tt', 'ㄹ': 'r', 'ㅁ': 'm',
    'ㅂ': 'b', 'ㅃ': 'pp', 'ㅅ': 's', 'ㅆ': 'ss', 'ㅇ': '', 'ㅈ': 'j', 'ㅉ': 'jj',
    'ㅊ': 'ch', 'ㅋ': 'k', 'ㅌ': 't', 'ㅍ': 'p', 'ㅎ': 'h',
}

# Seven representative final sounds.
NEUTRAL = {
    'ㄱ': 'ㄱ', 'ㄲ': 'ㄱ', 'ㅋ': 'ㄱ',
    'ㄷ': 'ㄷ', 'ㅅ': 'ㄷ', 'ㅆ': 'ㄷ', 'ㅈ': 'ㄷ', 'ㅊ': 'ㄷ', 'ㅌ': 'ㄷ', 'ㅎ': 'ㄷ',
    'ㅂ': 'ㅂ', 'ㅍ': 'ㅂ',
    'ㄴ': 'ㄴ', 'ㄹ': 'ㄹ', 'ㅁ': 'ㅁ', 'ㅇ': 'ㅇ',
}
FINAL_ROMAN = {'ㄱ': 'k', 'ㄴ': 'n', 'ㄷ': 't', 'ㄹ': 'l', 'ㅁ': 'm', 'ㅂ': 'p', 'ㅇ': 'ng', '': ''}

# Double final -> (stays, moves) when a vowel follows.
DOUBLE_SPLIT = {
    'ㄳ': ('ㄱ', 'ㅅ'), 'ㄵ': ('ㄴ', 'ㅈ'), 'ㄺ': ('ㄹ', 'ㄱ'), 'ㄻ': ('ㄹ', 'ㅁ'),
    'ㄼ': ('ㄹ', 'ㅂ'), 'ㄽ': ('ㄹ', 'ㅅ'), 'ㄾ': ('ㄹ', 'ㅌ'), 'ㄿ': ('ㄹ', 'ㅍ'),
    'ㅄ': ('ㅂ', 'ㅅ'),
}
# Double final before a consonant or at the end of a word.
DOUBLE_SIMPLE = {
    'ㄳ': 'ㄱ', 'ㄵ': 'ㄴ', 'ㄶ': 'ㄴ', 'ㄺ': 'ㄱ', 'ㄻ': 'ㅁ', 'ㄼ': 'ㄹ',
    'ㄽ': 'ㄹ', 'ㄾ': 'ㄹ', 'ㄿ': 'ㅂ', 'ㅀ': 'ㄹ', 'ㅄ': 'ㅂ',
}
ASPIRATE = {'ㄱ': 'ㅋ', 'ㄷ': 'ㅌ', 'ㅈ': 'ㅊ'}
NASAL = {'ㄱ': 'ㅇ', 'ㄷ': 'ㄴ', 'ㅂ': 'ㅁ'}
# Final + following ㅎ -> aspirated initial (final may keep a part).
H_AFTER = {
    'ㄱ': ('', 'ㅋ'), 'ㄲ': ('', 'ㅋ'), 'ㄳ': ('ㄱ', 'ㅆ'),
    'ㄷ': ('', 'ㅌ'), 'ㅅ': ('', 'ㅌ'), 'ㅆ': ('', 'ㅌ'), 'ㅌ': ('', 'ㅌ'),
    'ㅈ': ('', 'ㅊ'), 'ㅊ': ('', 'ㅊ'),
    'ㅂ': ('', 'ㅍ'), 'ㅍ': ('', 'ㅍ'),
    'ㄵ': ('ㄴ', 'ㅊ'), 'ㄺ': ('ㄹ', 'ㅋ'), 'ㄼ': ('ㄹ', 'ㅍ'),
}


def _is_syllable(ch):
    return '가' <= ch <= '힣'


def _decompose(ch):
    s = ord(ch) - 0xAC00
    return {'i': CHO[s // 588], 'v': (s % 588) // 28, 'f': JONG[s % 28], 'moved': False,
            'text': ch}


def _boundary(cur, nxt):
    """Rewrite cur['f'] and nxt['i'] in place for one syllable boundary."""
    f, i = cur['f'], nxt['i']

    # Exception: 밟- keeps ㅂ before a consonant (밟다 bapda).
    if cur['text'] == '밟' and i != 'ㅇ':
        f = 'ㅂ'

    if f == '':
        return

    if i == 'ㅇ':  # vowel follows: liaison
        if f == 'ㅇ':
            return
        if f == 'ㅎ':
            cur['f'] = ''
            return
        if f == 'ㄶ':
            cur['f'], nxt['i'] = '', 'ㄴ'
        elif f == 'ㅀ':
            cur['f'], nxt['i'] = '', 'ㄹ'
        elif f in DOUBLE_SPLIT:
            cur['f'], nxt['i'] = DOUBLE_SPLIT[f]
        else:
            cur['f'], nxt['i'] = '', f
        nxt['moved'] = True
        _palatalize(nxt)
        return

    if i == 'ㅎ' and f in H_AFTER:
        cur['f'], nxt['i'] = H_AFTER[f]
        nxt['moved'] = True
        _palatalize(nxt)
        return

    if f == 'ㅎ':
        if i in ASPIRATE:
            cur['f'], nxt['i'] = '', ASPIRATE[i]
        elif i == 'ㄴ':
            cur['f'] = 'ㄴ'
        else:
            cur['f'] = ''
        return

    if f in ('ㄶ', 'ㅀ'):
        keep = 'ㄴ' if f == 'ㄶ' else 'ㄹ'
        if i in ASPIRATE:
            cur['f'], nxt['i'] = keep, ASPIRATE[i]
        elif i == 'ㄴ':
            cur['f'], nxt['i'] = keep, keep
        else:
            cur['f'] = keep
        return

    # Double finals before a consonant.
    if f == 'ㄺ' and i == 'ㄱ':
        f = 'ㄹ'  # 읽고 ilgo, 맑게 malge
    f = DOUBLE_SIMPLE.get(f, f)
    f = NEUTRAL.get(f, f)

    if i == 'ㄹ':
        if f in ('ㄴ', 'ㄹ'):
            f, i = 'ㄹ', 'ㄹ'          # 신라 silla, 빨리 ppalli
        elif f in ('ㅁ', 'ㅇ'):
            i = 'ㄴ'                   # 심리 simni, 종로 jongno
        elif f in NASAL:
            f, i = NASAL[f], 'ㄴ'      # 독립 dongnip, 협력 hyeomnyeok
    elif i in ('ㄴ', 'ㅁ'):
        if f in NASAL:
            f = NASAL[f]               # 국민 gungmin, 입니다 imnida
        elif f == 'ㄹ' and i == 'ㄴ':
            i = 'ㄹ'                   # 설날 seollal

    cur['f'], nxt['i'] = f, i


def _palatalize(syl):
    if syl['moved'] and syl['v'] == VOWEL_I:
        if syl['i'] == 'ㄷ':
            syl['i'] = 'ㅈ'            # 굳이 guji
        elif syl['i'] == 'ㅌ':
            syl['i'] = 'ㅊ'            # 같이 gachi


def _romanize_run(text):
    syls = [_decompose(ch) for ch in text]
    for k in range(len(syls) - 1):
        _boundary(syls[k], syls[k + 1])
    out = []
    prev_final = ''
    for k, s in enumerate(syls):
        if s['i'] == 'ㄹ' and prev_final == 'ㄹ':
            out.append('l')
        else:
            out.append(INITIAL_ROMAN[s['i']])
        out.append(VOWEL_ROMAN[s['v']])
        f = s['f']
        if k == len(syls) - 1:
            f = DOUBLE_SIMPLE.get(f, f)
            if s['text'] == '밟':
                f = 'ㅂ'
        f = NEUTRAL.get(f, f)
        out.append(FINAL_ROMAN[f])
        prev_final = f
    return ''.join(out)


def romanize(text):
    """Romanize a string; non-Hangul characters pass through unchanged."""
    out = []
    run = []
    for ch in text:
        if _is_syllable(ch):
            run.append(ch)
            continue
        if run:
            out.append(_romanize_run(''.join(run)))
            run = []
        out.append(ch)
    if run:
        out.append(_romanize_run(''.join(run)))
    return ''.join(out)
