package app.hanguldaily;

/** Which words to show on a given day. Pure arithmetic, so it can be tested off-device. */
final class DailyPlan {
    static final int MIN_PER_DAY = 3;
    static final int MAX_PER_DAY = 5;
    static final int DEFAULT_PER_DAY = 5;

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

    /** Which of today's words the small card shows: it moves on every hour and on every tap. */
    static int cardPosition(int hourOfDay, int taps, int count) {
        return Math.floorMod(hourOfDay + taps, count);
    }
}
