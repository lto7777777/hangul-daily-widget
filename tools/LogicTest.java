package app.hanguldaily;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Plain-JDK checks for DailyPlan, Sizing and the generated word list; no Android needed.
 * Run by tools/build_apk.py, or by hand:
 *   javac -d build/test app/src/main/java/app/hanguldaily/{WordList,DailyPlan,Sizing}.java tools/LogicTest.java
 *   java -cp build/test app.hanguldaily.LogicTest app/src/main/assets/words.tsv
 */
public final class LogicTest {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        List<WordList.Word> words;
        try (InputStream in = new FileInputStream(args[0])) {
            words = WordList.parse(in);
        }
        int total = words.size();
        check(total > 5300, "word count " + total);

        // Everyday words come first; day 1 starts with 안녕
        check(words.get(0).korean.equals("안녕") && words.get(0).hasExample(), "first word is 안녕 with an example");
        WordList.Word hello = words.get(5);
        check(hello.korean.equals("안녕하다") && hello.example.equals("안녕하세요?"), "word 6 is 안녕하다");
        check(hello.parts.size() == 3 && hello.parts.get(2).piece.equals("세요"), "안녕하세요 has three parts");

        // Examples came through the TSV intact
        WordList.Word thing = null;
        for (WordList.Word w : words) {
            if (w.korean.equals("것")) {
                thing = w;
                break;
            }
        }
        check(thing != null && thing.roman.equals("geot"), "것 is in the list");
        check(thing != null && thing.example.equals("이것은 뭐예요?") && thing.translation.equals("What is this?"),
                "것 example");
        check(thing != null && thing.parts.size() == 5 && thing.parts.get(1).piece.equals("것")
                && thing.parts.get(1).gloss.equals("thing"), "것 example parts");
        StringBuilder joined = new StringBuilder();
        if (thing != null) {
            for (WordList.Part p : thing.parts) {
                joined.append(p.piece);
            }
        }
        check(joined.toString().equals("이것은뭐예요"), "parts join back to the sentence");
        int withExample = 0;
        int leadingRun = -1;
        for (int i = 0; i < total; i++) {
            WordList.Word w = words.get(i);
            withExample += w.hasExample() ? 1 : 0;
            if (leadingRun < 0 && !w.hasExample()) {
                leadingRun = i;
            }
            check(!w.hasExample() || (!w.translation.isEmpty() && !w.parts.isEmpty()),
                    "example without translation or parts: " + w.korean);
        }
        check(withExample >= 300, "at least 300 examples, got " + withExample);
        check(leadingRun >= 300, "the first 300 words all have examples, gap at " + leadingRun);
        check(!words.get(total - 1).hasExample(), "last word has no example yet");

        // Day-by-day progression
        check(Arrays.equals(DailyPlan.indices(100, 0, 5, 100, total), new int[] {0, 1, 2, 3, 4}), "day 1");
        check(DailyPlan.indices(100, 0, 5, 101, total)[0] == 5, "day 2 starts at word 6");
        check(DailyPlan.firstIndex(100, 0, 5, 90, total) == 0, "clock set back shows anchor day");
        check(Arrays.equals(DailyPlan.indices(0, total - 2, 5, 0, total),
                new int[] {total - 2, total - 1, 0, 1, 2}), "wraps at end of list");
        int far = DailyPlan.firstIndex(0, 0, 5, 1_000_000_000L, total);
        check(far >= 0 && far < total, "far future stays in range");

        // Changing words per day keeps today's first word, then steps by the new count
        int firstIdx = DailyPlan.firstIndex(100, 0, 5, 110, total);
        check(firstIdx == 50, "day 11 starts at index 50");
        int[] afterSwitch = DailyPlan.indices(110, firstIdx, 3, 110, total);
        check(afterSwitch.length == 3 && afterSwitch[0] == 50, "switch to 3 keeps today's first word");
        check(DailyPlan.firstIndex(110, firstIdx, 3, 111, total) == 53, "next day steps by 3");
        check(DailyPlan.clampPerDay(9) == 5 && DailyPlan.clampPerDay(1) == 3, "clamp 3..5");
        check(DailyPlan.dayNumber(100, 100) == 1 && DailyPlan.dayNumber(100, 104) == 5, "day number");
        check(DailyPlan.dayNumber(100, 90) == 1, "day number with clock set back");

        // Card faces: word -> example -> parts for words with an example, word only otherwise
        boolean[] all = {true, true, true, true, true};
        check(Arrays.equals(DailyPlan.cardFace(0, 0, all), new int[] {0, DailyPlan.FACE_WORD}), "midnight: word 1");
        check(Arrays.equals(DailyPlan.cardFace(0, 1, all), new int[] {0, DailyPlan.FACE_EXAMPLE}), "1 tap: example 1");
        check(Arrays.equals(DailyPlan.cardFace(0, 2, all), new int[] {0, DailyPlan.FACE_PARTS}), "2 taps: parts 1");
        check(Arrays.equals(DailyPlan.cardFace(0, 3, all), new int[] {1, DailyPlan.FACE_WORD}), "3 taps: word 2");
        check(Arrays.equals(DailyPlan.cardFace(14, 0, all), new int[] {4, DailyPlan.FACE_PARTS}), "14h: last face");
        check(Arrays.equals(DailyPlan.cardFace(15, 0, all), new int[] {0, DailyPlan.FACE_WORD}), "15h wraps to word 1");
        boolean[] mixed = {true, false, true};
        check(Arrays.equals(DailyPlan.cardFace(3, 0, mixed), new int[] {1, DailyPlan.FACE_WORD}), "no example: one face");
        check(Arrays.equals(DailyPlan.cardFace(4, 0, mixed), new int[] {2, DailyPlan.FACE_WORD}), "then next word");
        boolean[] none = {false, false, false};
        check(Arrays.equals(DailyPlan.cardFace(4, 0, none), new int[] {1, DailyPlan.FACE_WORD}), "no examples at all");

        // Layout choice: Duolingo's slot is about 160 x 97 dp on the P30 Pro
        check(!Sizing.fitsList(160, 97, 3, 0), "2x1 slot shows a card");
        check(Sizing.fitsList(160, 200, 3, 0), "2x2 slot with 3 plain words shows a list");
        check(!Sizing.fitsList(160, 200, 3, 3), "2x2 slot with 3 examples shows a card");
        check(Sizing.fitsList(330, 360, 5, 5), "tall slot shows a list with examples");
        check(!Sizing.fitsList(330, 330, 5, 5), "5 examples need 343dp");
        check(!Sizing.fitsList(0, 0, 3, 0), "unknown size shows a card");

        float longWord = Sizing.cardKoreanSp("남대문시장", 160, false);
        check(longWord < 26f && longWord >= 14f, "long word shrinks: " + longWord);
        check(Sizing.cardKoreanSp("것", 160, false) == 26f, "short word capped at 26sp");
        check(Sizing.cardKoreanSp("것", 0, true) == 34f, "unknown width uses cap");
        check(Sizing.cardMeaningLines(97, 26f, true) == 1, "2x1 with romanization: 1 meaning line");
        check(Sizing.cardMeaningLines(97, 26f, false) == 2, "2x1 without romanization: 2 lines");
        check(Sizing.cardMeaningLines(180, 34f, true) == 3, "tall card: 3 lines");

        float shortSentence = Sizing.exampleSp("돈이 없어요.", 160, false);
        check(shortSentence >= 15f && Sizing.exampleLines("돈이 없어요.", 160, shortSentence) == 1,
                "short example on one line: " + shortSentence);
        String longSentence = "밖에서 이상한 소리가 나요.";
        float longSp = Sizing.exampleSp(longSentence, 160, false);
        check(longSp >= 13f && Sizing.exampleLines(longSentence, 160, longSp) == 2,
                "long example wraps to two lines: " + longSp);
        check(Sizing.translationLines(97, 18f, 1, true) >= 1, "translation gets a line");
        check(Sizing.partsLines(97) == 3, "2x1 parts face has 3 lines, got " + Sizing.partsLines(97));

        check(Sizing.shortGloss("have, there is (있다)").equals("have (있다)"), "short gloss keeps dictionary form");
        check(Sizing.shortGloss("do (하다), polite").equals("do (하다)"), "short gloss cuts after bracket");
        check(Sizing.shortGloss("topic marker").equals("topic marker"), "short gloss unchanged");
        List<String> five = Arrays.asList("a", "b", "c", "d", "e");
        check(Sizing.packLines(five, 5).equals("a\nb\nc\nd\ne"), "one item per line when they fit");
        check(Sizing.packLines(five, 3).equals("a   b\nc   d\ne"), "two per line when they do not");
        check(Sizing.packLines(five, 1).equals("a   b   c   d   e"), "all on one line");

        // Every word must render something in each field the widget shows
        String longestWord = "";
        String longestExample = "";
        for (WordList.Word w : words) {
            check(!w.korean.isEmpty() && !w.meaning.isEmpty(), "empty field near " + w.korean);
            if (w.korean.length() > longestWord.length()) {
                longestWord = w.korean;
            }
            if (w.example.length() > longestExample.length()) {
                longestExample = w.example;
            }
        }
        System.out.println("longest word: " + longestWord + " -> "
                + Sizing.cardKoreanSp(longestWord, 160, false) + "sp in a 2-column slot");
        float exSp = Sizing.exampleSp(longestExample, 160, false);
        System.out.println("longest example: " + longestExample + " -> " + exSp + "sp, "
                + Sizing.exampleLines(longestExample, 160, exSp) + " line(s)");

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("LogicTest OK: " + total + " words, " + withExample + " with examples");
    }
}
