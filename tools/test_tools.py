"""Tests for romanize.py and build_words.py. Run: python tools/test_tools.py"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_words import (DEFAULT_CORRECTIONS, DEFAULT_EXAMPLES, DEFAULT_SRC,  # noqa: E402
                         build, check_example, clean_meaning, entry_keys, parse,
                         parse_parts, read_lines)
from romanize import romanize  # noqa: E402

# Expected forms follow Revised Romanization of the standard pronunciation,
# except ㅎ-aspiration, which this project applies to nouns as well.
CASES = {
    # plain
    '사람': 'saram', '우리': 'uri', '하다': 'hada', '서울': 'seoul', '김치': 'gimchi',
    '의사': 'uisa', '희망': 'huimang', '한글': 'hangeul', '신문': 'sinmun',
    # finals and neutralization
    '것': 'geot', '있다': 'itda', '같다': 'gatda', '꽃': 'kkot', '밖': 'bak',
    '부엌': 'bueok', '앞': 'ap', '끝': 'kkeut', '몇': 'myeot', '학교': 'hakgyo',
    '받다': 'batda', '씻다': 'ssitda', '문법': 'munbeop',
    # liaison
    '한국어': 'hangugeo', '영어': 'yeongeo', '일요일': 'iryoil', '음악': 'eumak',
    '어린이': 'eorini', '밖에': 'bakke', '부엌에': 'bueoke', '꽃이': 'kkochi',
    '받아': 'bada', '밭에': 'bate', '맛있다': 'masitda',
    # double finals
    '없다': 'eopda', '없어': 'eopseo', '값': 'gap', '값이': 'gapsi', '닭': 'dak',
    '읽다': 'ikda', '읽고': 'ilgo', '맑게': 'malge', '밝다': 'bakda', '젊다': 'jeomda',
    '넓다': 'neolda', '밟다': 'bapda', '여덟': 'yeodeol', '앉다': 'anda', '앉아': 'anja',
    '몫': 'mok', '몫이': 'moksi', '흙': 'heuk', '삶': 'sam', '넋': 'neok',
    '읊다': 'eupda', '핥다': 'halda',
    # ㅎ
    '좋다': 'jota', '좋고': 'joko', '좋아': 'joa', '좋네': 'jonne', '놓다': 'nota',
    '놓는': 'nonneun', '넣다': 'neota', '많다': 'manta', '많이': 'mani', '않다': 'anta',
    '않는': 'anneun', '괜찮다': 'gwaenchanta', '괜찮아': 'gwaenchana', '싫다': 'silta',
    '싫어': 'sireo', '잃어': 'ireo', '닳다': 'dalta', '뚫다': 'ttulta', '뚫는': 'ttulleun',
    '이렇다': 'ireota', '어떻다': 'eotteota',
    '못하다': 'motada', '생각하다': 'saenggakada', '축하': 'chuka', '입학': 'ipak',
    '급히': 'geupi', '맞히다': 'machida', '꽂히다': 'kkochida', '넓히다': 'neolpida',
    '앉히다': 'anchida', '밝히다': 'balkida',
    '전화': 'jeonhwa', '결혼': 'gyeolhon', '은행': 'eunhaeng',
    # palatalization
    '같이': 'gachi', '굳이': 'guji', '해돋이': 'haedoji', '밭이': 'bachi',
    '닫히다': 'dachida',
    # nasalization
    '국민': 'gungmin', '입니다': 'imnida', '감사합니다': 'gamsahamnida',
    '먹는': 'meongneun', '옛날': 'yennal', '끝나다': 'kkeunnada', '십만': 'simman',
    '종로': 'jongno', '심리': 'simni', '대통령': 'daetongnyeong', '항로': 'hangno',
    '강릉': 'gangneung', '독립': 'dongnip', '협력': 'hyeomnyeok', '백로': 'baengno',
    '법률': 'beomnyul', '왕십리': 'wangsimni',
    # liquidization
    '신라': 'silla', '설날': 'seollal', '빨리': 'ppalli', '선릉': 'seolleung',
    '별내': 'byeollae', '훌륭하다': 'hullyunghada',
    # verbs from the top of the list
    '알다': 'alda', '만들다': 'mandeulda', '쉽다': 'swipda', '고맙다': 'gomapda',
    # spaces and punctuation pass through
    '그 동안': 'geu dongan', '도쿄(동경)': 'dokyo(donggyeong)',
}


class RomanizeTest(unittest.TestCase):
    def test_cases(self):
        wrong = {k: (romanize(k), v) for k, v in CASES.items() if romanize(k) != v}
        self.assertEqual(wrong, {}, 'got vs expected')

    def test_non_hangul_untouched(self):
        self.assertEqual(romanize('TV 3'), 'TV 3')
        self.assertEqual(romanize(''), '')


class ParseTest(unittest.TestCase):
    def test_continuation_and_cleanup(self):
        lines = [
            'Korean Word \tEnglish Meaning ',
            ' 벌이다         \t (1) to plan to start a ',
            'job/project (2) to play a table game ',
            ' 권위           \t1)\tAuthority or  power  ',
            '2)\tdignity or  prestige ',
            '',
            ' 기 13           \t 1) energy  2) breath or  wind ',
            ' 삼촌           \t An uncle (usually on the father\\\'s side) ',
            ' 하다           \t To do ',
            ' 하다           \t to do ',
            ' 말             \t words, speaking ',
            ' 말             \t Horse ',
        ]
        words, problems = parse(lines)
        self.assertEqual(problems, [])
        self.assertEqual(words, [
            ('벌이다', '(1) to plan to start a job/project (2) to play a table game'),
            ('권위', '1) Authority or power 2) dignity or prestige'),
            ('기', '1) energy 2) breath or wind'),
            ('삼촌', "An uncle (usually on the father's side)"),
            ('하다', 'To do'),
            ('말', 'Words, speaking'),
            ('말', 'Horse'),
        ])

    def test_clean_meaning(self):
        self.assertEqual(clean_meaning(' way , method , '), 'Way, method')
        self.assertEqual(clean_meaning('( composition, structure )'), '(composition, structure)')


SOURCE = [
    'Korean Word \tEnglish Meaning ',
    ' 안녕           \t Hello/goodbye ',
    ' 가다           \t To go ',
    ' 말             \t words, speaking ',
    ' 말             \t Horse ',
]


class ExampleTest(unittest.TestCase):
    def test_entry_keys_number_homographs(self):
        words, _ = parse(SOURCE)
        self.assertEqual(entry_keys(words), ['안녕', '가다', '말', '말#2'])

    def test_good_example(self):
        parts = parse_parts('안녕=peace | 하=be, do (하다) | 세요=polite ending')
        self.assertEqual(check_example('안녕', '안녕하세요!', 'Hello.', parts), [])

    def test_parts_must_join_to_example(self):
        parts = parse_parts('안녕=peace | 세요=polite ending')
        problems = check_example('안녕', '안녕하세요!', 'Hello.', parts)
        self.assertTrue(any('join' in p for p in problems), problems)

    def test_verb_must_name_dictionary_form(self):
        parts = parse_parts('학교=school | 에=to | 가요=go, polite')
        problems = check_example('가다', '학교에 가요.', 'I go to school.', parts)
        self.assertTrue(any('(가다)' in p for p in problems), problems)

    def test_noun_must_appear(self):
        parts = parse_parts('물=water | 주세요=please give (주다)')
        problems = check_example('안녕', '물 주세요.', 'Water, please.', parts)
        self.assertTrue(any('not in the example' in p for p in problems), problems)

    def test_missing_gloss_and_bad_rows(self):
        examples = [
            '말#2\t말을 타요.\tI ride a horse.\t말=horse | 을=object marker | 타요=ride (타다), polite',
            '말#2\t말이에요.\tIt is a horse.\t말=horse | 이에요=is',
            '없음\t없어요.\tNone.\t없어요=there is none (없다)',
            '가다\t가요.\tGo.\t가 | 요=polite ending',
        ]
        rows, problems = build(SOURCE, examples, [])
        text = '\n'.join(problems)
        self.assertIn('second example', text)
        self.assertIn('unknown key', text)
        self.assertIn('needs both a piece and a gloss', text)
        self.assertEqual(rows[3][3], '말을 타요.')   # 말#2 (horse) got the first example
        self.assertEqual(rows[2][3], '')             # 말 (words) has none

    def test_corrections_apply_and_unknown_key_fails(self):
        rows, problems = build(SOURCE, [], ['가다\tTo go (somewhere)\tclearer', '오다\tTo come\tx'])
        self.assertEqual(rows[1][2], 'To go (somewhere)')
        self.assertEqual(len(problems), 1)
        self.assertIn('unknown key', problems[0])


class RealListTest(unittest.TestCase):
    """Invariants on the actual kor/5324kor.txt."""

    @classmethod
    def setUpClass(cls):
        lines = DEFAULT_SRC.read_text(encoding='utf-8').splitlines()
        cls.words, cls.problems = parse(lines)

    def test_no_problems(self):
        self.assertEqual(self.problems, [])

    def test_size(self):
        self.assertGreater(len(self.words), 5300)
        self.assertLess(len(self.words), 5700)

    def test_fields_clean(self):
        for korean, meaning in self.words:
            self.assertTrue(korean and meaning, (korean, meaning))
            for field in (korean, meaning):
                self.assertNotIn('\t', field)
                self.assertNotIn('  ', field)
                self.assertEqual(field, field.strip())

    def test_no_continuation_fragments_as_keys(self):
        keys = {k for k, _ in self.words}
        for bad in ('2)', 'ANDONG', 'job/project (2) to play a table game'):
            self.assertNotIn(bad, keys)

    def test_frequency_order_kept(self):
        self.assertEqual([k for k, _ in self.words[:6]], ['것', '하다', '있다', '수', '나', '없다'])

    def test_continuations_joined(self):
        meanings = dict((k, m) for k, m in reversed(self.words))
        self.assertIn('table game', meanings['벌이다'])
        self.assertIn('ANDONG', meanings['안동 간 고등어'])

    def test_examples_and_corrections_files_are_clean(self):
        rows, problems = build(read_lines(DEFAULT_SRC), read_lines(DEFAULT_EXAMPLES),
                               read_lines(DEFAULT_CORRECTIONS))
        self.assertEqual(problems, [])
        self.assertTrue(all(r[3] for r in rows[:100]), 'first 100 words all have examples')
        by_key = dict(zip(entry_keys([(r[0], r[2]) for r in rows]), rows))
        self.assertEqual(by_key['여기'][2], 'Here')
        self.assertIn('try', by_key['보다'][2])
        self.assertEqual(by_key['보다#2'][3], '영화를 봐요.')
        for r in rows:
            for field in r:
                self.assertNotIn('\t', field)
                self.assertNotIn('\n', field)


if __name__ == '__main__':
    unittest.main(verbosity=1)
