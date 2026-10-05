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
        check(words.get(0).korean.equals("것") && words.get(0).roman.equals("geot"), "first word");

        // Day-by-day progression
        check(Arrays.equals(DailyPlan.indices(100, 0, 5, 100, total), new int[] {0, 1, 2, 3, 4}), "day 1");
        check(DailyPlan.indices(100, 0, 5, 101, total)[0] == 5, "day 2 starts at word 6");
        check(DailyPlan.firstIndex(100, 0, 5, 90, total) == 0, "clock set back shows anchor day");
        check(Arrays.equals(DailyPlan.indices(0, total - 2, 5, 0, total),
                new int[] {total - 2, total - 1, 0, 1, 2}), "wraps at end of list");
        int far = DailyPlan.firstIndex(0, 0, 5, 1_000_000_000L, total);
        check(far >= 0 && far < total, "far future stays in range");

        // Changing words per day keeps today's first word, then steps by the new count
        int first = DailyPlan.firstIndex(100, 0, 5, 110, total);
        check(first == 50, "day 11 starts at index 50");
        int[] afterSwitch = DailyPlan.indices(110, first, 3, 110, total);
        check(afterSwitch.length == 3 && afterSwitch[0] == 50, "switch to 3 keeps today's first word");
        check(DailyPlan.firstIndex(110, first, 3, 111, total) == 53, "next day steps by 3");

        check(DailyPlan.clampPerDay(9) == 5 && DailyPlan.clampPerDay(1) == 3, "clamp 3..5");
        check(DailyPlan.cardPosition(0, 0, 5) == 0, "card at midnight");
        check(DailyPlan.cardPosition(23, 0, 5) == 3, "card at 23h");
        check(DailyPlan.cardPosition(4, 1, 5) == 0, "card tap wraps");
        check(DailyPlan.dayNumber(100, 100) == 1 && DailyPlan.dayNumber(100, 104) == 5, "day number");
        check(DailyPlan.dayNumber(100, 90) == 1, "day number with clock set back");

        // Layout choice: Duolingo's slot is about 160 x 97 dp on the P30 Pro
        check(!Sizing.fitsList(160, 97, 3), "2x1 slot shows a card");
        check(Sizing.fitsList(160, 200, 3), "2x2 slot with 3 words shows a list");
        check(!Sizing.fitsList(160, 200, 5), "2x2 slot with 5 words shows a card");
        check(Sizing.fitsList(330, 300, 5), "4x3 slot shows a list");
        check(!Sizing.fitsList(0, 0, 3), "unknown size shows a card");

        float longWord = Sizing.cardKoreanSp("남대문시장", 160, false);
        check(longWord < 26f && longWord >= 14f, "long word shrinks: " + longWord);
        check(Sizing.cardKoreanSp("것", 160, false) == 26f, "short word capped at 26sp");
        check(Sizing.cardKoreanSp("것", 0, true) == 34f, "unknown width uses cap");

        check(Sizing.cardMeaningLines(97, 26f, true) == 1, "2x1 with romanization: 1 meaning line");
        check(Sizing.cardMeaningLines(97, 26f, false) == 2, "2x1 without romanization: 2 lines");
        check(Sizing.cardMeaningLines(0, 26f, true) == 1, "unknown height: 1 line");
        check(Sizing.cardMeaningLines(180, 34f, true) == 3, "tall card: 3 lines");

        // Every word must render something in each field the widget shows
        int longest = 0;
        String longestWord = "";
        for (WordList.Word w : words) {
            check(!w.korean.isEmpty() && !w.meaning.isEmpty(), "empty field near " + w.korean);
            if (w.korean.length() > longest) {
                longest = w.korean.length();
                longestWord = w.korean;
            }
        }
        float worst = Sizing.cardKoreanSp(longestWord, 160, false);
        System.out.println("longest word: " + longestWord + " (" + longest + " chars) -> " + worst + "sp in a 2-column slot");

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("LogicTest OK: " + total + " words");
    }
}
