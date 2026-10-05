package app.hanguldaily;

/** Which words to show on a given day. Pure arithmetic, so it can be tested off-device. */
final class DailyPlan {
    static final int MIN_PER_DAY = 3;
    static final int MAX_PER_DAY = 5;
    static final int DEFAULT_PER_DAY = 5;

    /** Sides of the small card: the word, its example sentence, and the sentence's parts. */
    static final int FACE_WORD = 0;
    static final int FACE_EXAMPLE = 1;
    static final int FACE_PARTS = 2;

    private DailyPlan() {}

    static int clampPerDay(int n) {
        return Math.max(MIN_PER_DAY, Math.min(MAX_PER_DAY, n));
    }

    /**
     * Index of today's first word. From the anchor day on, each day moves forward by
     * perDay words and wraps at the end of the list. A clock set back before the anchor
     * shows the anchor day's words.
     */
    static int firstIndex(long anchorDay, int anchorIndex, int perDay, long today, int total) {
        if (total <= 0) {
            throw new IllegalArgumentException("empty word list");
        }
        long days = Math.max(0L, today - anchorDay);
        return (int) Math.floorMod(anchorIndex + days * perDay, (long) total);
    }

    static int[] indices(long anchorDay, int anchorIndex, int perDay, long today, int total) {
        int first = firstIndex(anchorDay, anchorIndex, perDay, today, total);
        int[] out = new int[Math.min(perDay, total)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (first + i) % total;
        }
        return out;
    }

    /** Day 1 is the first day the app ran. */
    static long dayNumber(long startDay, long today) {
        return Math.max(0L, today - startDay) + 1;
    }

    /**
     * What the small card shows, as {position in today's words, face}. Each word has a
     * word face, then an example face and a parts face if it has an example. The card
     * steps one face on every hour and on every tap, wrapping after the last word.
     */
    static int[] cardFace(int hourOfDay, int taps, boolean[] hasExample) {
        int total = 0;
        for (boolean has : hasExample) {
            total += has ? 3 : 1;
        }
        int step = Math.floorMod(hourOfDay + taps, total);
        for (int i = 0; i < hasExample.length; i++) {
            int faces = hasExample[i] ? 3 : 1;
            if (step < faces) {
                return new int[] {i, step};
            }
            step -= faces;
        }
        throw new IllegalStateException("no words today");
    }
}
