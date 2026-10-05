package app.hanguldaily;

import java.util.List;

/**
 * Layout decisions from the widget's size in dp. The numbers mirror the paddings and
 * text sizes in res/layout/widget_*.xml; keep them in step when those change.
 */
final class Sizing {
    /** List: 12dp padding top and bottom, plus the header line. */
    static final int LIST_CHROME_DP = 48;
    /** List row: 6dp gap, a 16sp line and a 12sp line. */
    static final int LIST_ROW_DP = 44;
    /** Extra 11sp line for a row's example. */
    static final int LIST_EXAMPLE_DP = 15;
    static final int LIST_MIN_WIDTH_DP = 150;
    /** Card height from which the header line is shown. */
    static final int ROOMY_CARD_DP = 120;
    /** Assumed height when the launcher does not report one: a 2x1 slot. */
    static final int UNKNOWN_HEIGHT_DP = 100;
    /** Assumed size of the 2x2 widget when the launcher does not report one. */
    static final int BIG_UNKNOWN_DP = 160;
    /** From this height the card shows word, example and parts together (one face per word). */
    static final int COMBINED_MIN_DP = 150;

    private static final int CARD_PADDING_H_DP = 14 * 2;
    private static final int CARD_PADDING_V_DP = 10 * 2;
    private static final int CARD_COUNTER_DP = 30;
    private static final int ROMAN_LINE_DP = 18;
    private static final int HEADER_LINE_DP = 17;
    private static final int MEANING_LINE_DP = 17;
    private static final int SMALL_LINE_DP = 16;
    private static final int CARD_MARGINS_DP = 4;

    private Sizing() {}

    static int height(int heightDp) {
        return heightDp > 0 ? heightDp : UNKNOWN_HEIGHT_DP;
    }

    static boolean fitsList(int widthDp, int heightDp, int rows, int rowsWithExample) {
        return widthDp >= LIST_MIN_WIDTH_DP
                && heightDp >= LIST_CHROME_DP + rows * LIST_ROW_DP + rowsWithExample * LIST_EXAMPLE_DP;
    }

    static boolean roomyCard(int heightDp) {
        return heightDp >= ROOMY_CARD_DP;
    }

    /** Text width in ems: a Hangul syllable is about 1em, a space about 0.3em, Latin about 0.55em. */
    private static float ems(String text) {
        float ems = 0f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '가' && ch <= '힣') {
                ems += 1.0f;
            } else if (ch == ' ') {
                ems += 0.3f;
            } else {
                ems += 0.55f;
            }
        }
        return Math.max(ems, 1f);
    }

    private static float lineWidthDp(int widthDp) {
        return widthDp - CARD_PADDING_H_DP - CARD_COUNTER_DP;
    }

    /** Largest size up to the cap at which the word fits on one line. */
    static float cardKoreanSp(String korean, int widthDp, boolean roomy) {
        float cap = roomy ? 34f : 26f;
        if (widthDp <= 0) {
            return cap;
        }
        float fit = lineWidthDp(widthDp) / (ems(korean) * 1.05f);
        return Math.max(14f, Math.min(cap, fit));
    }

    /** How many lines the meaning gets under the word, 1 to 3. */
    static int cardMeaningLines(int heightDp, float koreanSp, boolean showRoman) {
        int h = height(heightDp);
        float avail = h - CARD_PADDING_V_DP - CARD_MARGINS_DP - koreanSp * 1.2f
                - (showRoman ? ROMAN_LINE_DP : 0)
                - (roomyCard(h) ? HEADER_LINE_DP : 0);
        return clampLines(avail / MEANING_LINE_DP, 3);
    }

    /** Example sentence size: one line if it fits at 15sp or more, else two lines. */
    static float exampleSp(String sentence, int widthDp, boolean roomy) {
        float cap = roomy ? 22f : 18f;
        if (widthDp <= 0) {
            return 15f;
        }
        float oneLine = lineWidthDp(widthDp) / (ems(sentence) * 1.05f);
        if (oneLine >= 15f) {
            return Math.min(cap, oneLine);
        }
        return Math.max(13f, Math.min(cap, 2 * oneLine));
    }

    static int exampleLines(String sentence, int widthDp, float sp) {
        if (widthDp <= 0) {
            return 2;
        }
        // Half a dp of slack so a sentence sized to fit exactly is not counted as wrapping.
        return ems(sentence) * 1.05f * sp > lineWidthDp(widthDp) + 0.5f ? 2 : 1;
    }

    /** Lines for the translation under the example, 1 to 3. */
    static int translationLines(int heightDp, float sentenceSp, int sentenceLines, boolean showRoman) {
        int h = height(heightDp);
        float avail = h - CARD_PADDING_V_DP - CARD_MARGINS_DP - sentenceLines * sentenceSp * 1.25f
                - (showRoman ? ROMAN_LINE_DP : 0)
                - (roomyCard(h) ? HEADER_LINE_DP : 0);
        return clampLines(avail / SMALL_LINE_DP, 3);
    }

    /** Lines for the parts list under the small sentence line, 1 to 8. */
    static int partsLines(int heightDp) {
        int h = height(heightDp);
        float avail = h - CARD_PADDING_V_DP - CARD_MARGINS_DP - HEADER_LINE_DP;
        return clampLines(avail / SMALL_LINE_DP, 8);
    }

    static boolean combinedCard(int heightDp) {
        return heightDp >= COMBINED_MIN_DP;
    }

    /** Example size on the combined card: up to 16sp on one line, else 14sp on two. */
    static float combinedSentenceSp(String sentence, int widthDp) {
        if (widthDp <= 0) {
            return 14f;
        }
        float oneLine = (widthDp - CARD_PADDING_H_DP) / (ems(sentence) * 1.05f);
        return oneLine >= 13f ? Math.min(16f, oneLine) : 14f;
    }

    static int combinedSentenceLines(String sentence, int widthDp, float sp) {
        if (widthDp <= 0) {
            return 2;
        }
        return ems(sentence) * 1.05f * sp > widthDp - CARD_PADDING_H_DP + 0.5f ? 2 : 1;
    }

    /**
     * Space left for the parts on the combined card, after the word line, meaning,
     * example and translation. Returns {parts lines (0 to 6), 1 if the example's
     * romanization fits too, else 0}. Parts win over the example's romanization.
     */
    static int[] combinedRest(int heightDp, float koreanSp, float sentenceSp, int sentenceLines,
                              boolean showRoman) {
        float avail = heightDp - CARD_PADDING_V_DP - CARD_MARGINS_DP - koreanSp * 1.25f
                - SMALL_LINE_DP - 6 - sentenceLines * sentenceSp * 1.3f - 15;
        if (showRoman) {
            int withRoman = (int) Math.floor((avail - 14) / 14);
            if (withRoman >= 1) {
                return new int[] {Math.min(6, withRoman), 1};
            }
        }
        return new int[] {Math.max(0, Math.min(6, (int) Math.floor(avail / 14))), 0};
    }

    private static int clampLines(float lines, int max) {
        return Math.max(1, Math.min(max, (int) Math.floor(lines)));
    }

    /**
     * A gloss short enough for the widget: the text before the first comma, keeping a
     * dictionary form in brackets. "have, there is (있다)" becomes "have (있다)".
     */
    static String shortGloss(String gloss) {
        int comma = gloss.indexOf(", ");
        if (comma < 0) {
            return gloss;
        }
        String head = gloss.substring(0, comma);
        if (!head.contains("(")) {
            int open = gloss.indexOf('(', comma);
            int close = open < 0 ? -1 : gloss.indexOf(')', open);
            if (close > open) {
                head = head + " " + gloss.substring(open, close + 1);
            }
        }
        return head;
    }

    /** One item per line if they fit, otherwise several per line, at most maxLines lines. */
    static String packLines(List<String> items, int maxLines) {
        int perLine = (items.size() + maxLines - 1) / Math.max(1, maxLines);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(i % perLine == 0 ? "\n" : "   ");
            }
            out.append(items.get(i));
        }
        return out.toString();
    }
}
