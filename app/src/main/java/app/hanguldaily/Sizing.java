package app.hanguldaily;

/**
 * Layout decisions from the widget's size in dp. The numbers mirror the paddings and
 * text sizes in res/layout/widget_*.xml; keep them in step when those change.
 */
final class Sizing {
    /** List: 12dp padding top and bottom, plus the header line. */
    static final int LIST_CHROME_DP = 48;
    /** List row: 6dp gap, a 16sp line and a 12sp line. */
    static final int LIST_ROW_DP = 44;
    static final int LIST_MIN_WIDTH_DP = 150;
    /** Card height from which the "오늘의 단어" header is shown. */
    static final int ROOMY_CARD_DP = 120;
    /** Assumed height when the launcher does not report one: a 2x1 slot. */
    static final int UNKNOWN_HEIGHT_DP = 100;

    private static final int CARD_PADDING_H_DP = 14 * 2;
    private static final int CARD_PADDING_V_DP = 10 * 2;
    private static final int CARD_COUNTER_DP = 30;
    private static final int ROMAN_LINE_DP = 18;
    private static final int HEADER_LINE_DP = 17;
    private static final int MEANING_LINE_DP = 17;
    private static final int CARD_MARGINS_DP = 4;

    private Sizing() {}

    static boolean fitsList(int widthDp, int heightDp, int rows) {
        return widthDp >= LIST_MIN_WIDTH_DP && heightDp >= LIST_CHROME_DP + rows * LIST_ROW_DP;
    }

    static boolean roomyCard(int heightDp) {
        return heightDp >= ROOMY_CARD_DP;
    }

    /** Largest size up to the cap at which the word fits on one line; a Hangul syllable is about 1em wide. */
    static float cardKoreanSp(String korean, int widthDp, boolean roomy) {
        float cap = roomy ? 34f : 26f;
        if (widthDp <= 0) {
            return cap;
        }
        float ems = 0f;
        for (int i = 0; i < korean.length(); i++) {
            char ch = korean.charAt(i);
            ems += (ch >= '가' && ch <= '힣') ? 1.0f : 0.55f;
        }
        float fit = (widthDp - CARD_PADDING_H_DP - CARD_COUNTER_DP) / (Math.max(ems, 1f) * 1.05f);
        return Math.max(14f, Math.min(cap, fit));
    }

    /** How many lines the meaning gets under the word, 1 to 3. */
    static int cardMeaningLines(int heightDp, float koreanSp, boolean showRoman) {
        int h = heightDp > 0 ? heightDp : UNKNOWN_HEIGHT_DP;
        float avail = h - CARD_PADDING_V_DP - CARD_MARGINS_DP - koreanSp * 1.2f
                - (showRoman ? ROMAN_LINE_DP : 0)
                - (roomyCard(h) ? HEADER_LINE_DP : 0);
        int lines = (int) Math.floor(avail / MEANING_LINE_DP);
        return Math.max(1, Math.min(3, lines));
    }
}
