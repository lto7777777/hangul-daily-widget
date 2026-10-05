package app.hanguldaily;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/** Settings and progress in SharedPreferences, plus the cached word list. */
final class Store {
    private static final String PREFS = "hangul_daily";
    private static final String START_DAY = "startDay";
    // Version 1.0-1.3 kept only the latest words-per-day change in these two keys.
    private static final String ANCHOR_DAY = "anchorDay";
    private static final String ANCHOR_INDEX = "anchorIndex";
    private static final String PER_DAY = "perDay";
    /** Every words-per-day change, so past days can be shown as they were. */
    private static final String HISTORY = "history";
    private static final String SHOW_ROMAN = "showRoman";
    private static final String TAP_DAY = "tapDay_";
    private static final String TAP_COUNT = "tapCount_";

    private static volatile List<WordList.Word> words;

    private Store() {}

    static List<WordList.Word> words(Context context) {
        List<WordList.Word> w = words;
        if (w == null) {
            synchronized (Store.class) {
                w = words;
                if (w == null) {
                    try (InputStream in = context.getAssets().open("words.tsv")) {
                        w = WordList.parse(in);
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot read words.tsv", e);
                    }
                    words = w;
                }
            }
        }
        return w;
    }

    static long today() {
        return LocalDate.now().toEpochDay();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * On first use, today becomes day 1 and starts at the first word. An install from
     * before the history existed gets one built from its latest change.
     */
    private static SharedPreferences started(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(START_DAY)) {
            long t = today();
            p.edit()
                    .putLong(START_DAY, t)
                    .putInt(PER_DAY, DailyPlan.DEFAULT_PER_DAY)
                    .putString(HISTORY, DailyPlan.appendHistory("", t, 0, DailyPlan.DEFAULT_PER_DAY))
                    .apply();
        } else if (!p.contains(HISTORY)) {
            long t = today();
            String history = DailyPlan.appendHistory("", p.getLong(ANCHOR_DAY, p.getLong(START_DAY, t)),
                    p.getInt(ANCHOR_INDEX, 0), p.getInt(PER_DAY, DailyPlan.DEFAULT_PER_DAY));
            p.edit().putString(HISTORY, history).apply();
        }
        return p;
    }

    private static long[][] history(SharedPreferences p) {
        return DailyPlan.parseHistory(p.getString(HISTORY, ""));
    }

    static int perDay(Context context) {
        return DailyPlan.clampPerDay(started(context).getInt(PER_DAY, DailyPlan.DEFAULT_PER_DAY));
    }

    static boolean showRoman(Context context) {
        return prefs(context).getBoolean(SHOW_ROMAN, true);
    }

    static long startDay(Context context) {
        return started(context).getLong(START_DAY, today());
    }

    static long dayNumber(Context context) {
        return DailyPlan.dayNumber(startDay(context), today());
    }

    /** The words of any day since the first one. */
    static int[] indicesOn(Context context, long day, int total) {
        return DailyPlan.indicesOn(history(started(context)), day, total);
    }

    static int[] todaysIndices(Context context, int total) {
        return indicesOn(context, today(), total);
    }

    /** Change the number of words per day from today on, without moving today's first word. */
    static void setPerDay(Context context, int n) {
        SharedPreferences p = started(context);
        long t = today();
        int first = DailyPlan.firstIndexOn(history(p), t, words(context).size());
        int perDay = DailyPlan.clampPerDay(n);
        p.edit()
                .putString(HISTORY, DailyPlan.appendHistory(p.getString(HISTORY, ""), t, first, perDay))
                .putInt(PER_DAY, perDay)
                .apply();
    }

    static void setShowRoman(Context context, boolean on) {
        prefs(context).edit().putBoolean(SHOW_ROMAN, on).apply();
    }

    /** Taps on one widget's card today; starts again from zero each day. */
    static int taps(Context context, int widgetId) {
        SharedPreferences p = prefs(context);
        return p.getLong(TAP_DAY + widgetId, -1L) == today() ? p.getInt(TAP_COUNT + widgetId, 0) : 0;
    }

    static void addTap(Context context, int widgetId) {
        int n = taps(context, widgetId) + 1;
        prefs(context).edit()
                .putLong(TAP_DAY + widgetId, today())
                .putInt(TAP_COUNT + widgetId, n)
                .apply();
    }

    static void forget(Context context, int widgetId) {
        prefs(context).edit().remove(TAP_DAY + widgetId).remove(TAP_COUNT + widgetId).apply();
    }
}
