package app.hanguldaily;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Home-screen widget. In a small slot (like Duolingo's 2x1) it is a card that steps,
 * on every tap and every hour, through each word, its example sentence, and what each
 * part of that sentence means. When all of today's words fit, it lists them.
 *
 * Today's words depend only on the date, so any update shows the right ones even if
 * the midnight alarm was held back by the phone's battery manager.
 */
public class WordWidget extends AppWidgetProvider {
    static final String ACTION_NEXT = "app.hanguldaily.action.NEXT";
    static final String ACTION_REFRESH = "app.hanguldaily.action.REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            render(context, manager, id);
        }
        scheduleMidnightRefresh(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context, manager, id);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            Store.forget(context, id);
        }
    }

    @Override
    public void onDisabled(Context context) {
        // Called per widget size; keep the alarm while the other size is still placed.
        if (ids(context, AppWidgetManager.getInstance(context)).length == 0) {
            context.getSystemService(AlarmManager.class).cancel(refreshIntent(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_NEXT.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (isOurs(context, id)) {
                Store.addTap(context, id);
                render(context, AppWidgetManager.getInstance(context), id);
            }
        } else if (ACTION_REFRESH.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            updateAll(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    /** Redraw every placed widget. The app screen calls this too. */
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = ids(context, manager);
        for (int id : ids) {
            render(context, manager, id);
        }
        if (ids.length > 0) {
            scheduleMidnightRefresh(context);
        }
    }

    private static void render(Context context, AppWidgetManager manager, int id) {
        List<WordList.Word> words = Store.words(context);
        int[] today = Store.todaysIndices(context, words.size());
        Bundle options = manager.getAppWidgetOptions(id);
        // Portrait size is min width x max height; both are 0 if the launcher does not report them.
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        boolean big = isBig(manager, id);
        if (big && heightDp <= 0) {
            heightDp = Sizing.BIG_UNKNOWN_DP;
            widthDp = widthDp > 0 ? widthDp : Sizing.BIG_UNKNOWN_DP;
        }
        boolean roman = Store.showRoman(context);

        boolean[] hasExample = new boolean[today.length];
        int withExample = 0;
        for (int i = 0; i < today.length; i++) {
            hasExample[i] = words.get(today[i]).hasExample();
            withExample += hasExample[i] ? 1 : 0;
        }

        RemoteViews views;
        int hour = LocalTime.now().getHour();
        if (Sizing.fitsList(widthDp, heightDp, today.length, withExample)) {
            views = list(context, words, today, roman);
        } else if (Sizing.combinedCard(heightDp) || (big && Sizing.roomyCard(heightDp))) {
            // One face per word: everything about it fits on the card at once.
            int[] face = DailyPlan.cardFace(hour, Store.taps(context, id), new boolean[today.length]);
            WordList.Word word = words.get(today[face[0]]);
            String counter = (face[0] + 1) + "/" + today.length;
            views = word.hasExample()
                    ? combinedCard(context, word, counter, widthDp, heightDp, roman)
                    : wordCard(context, word, counter, widthDp, heightDp, roman);
            views.setOnClickPendingIntent(R.id.card_root, nextIntent(context, id));
        } else {
            int[] face = DailyPlan.cardFace(hour, Store.taps(context, id), hasExample);
            WordList.Word word = words.get(today[face[0]]);
            String counter = (face[0] + 1) + "/" + today.length;
            if (face[1] == DailyPlan.FACE_EXAMPLE) {
                views = exampleCard(context, word, counter, widthDp, heightDp, roman);
            } else if (face[1] == DailyPlan.FACE_PARTS) {
                views = partsCard(context, word, counter, heightDp);
            } else {
                views = wordCard(context, word, counter, widthDp, heightDp, roman);
            }
            views.setOnClickPendingIntent(R.id.card_root, nextIntent(context, id));
        }
        manager.updateAppWidget(id, views);
    }

    private static RemoteViews wordCard(Context context, WordList.Word word, String counter,
                                        int widthDp, int heightDp, boolean roman) {
        boolean roomy = Sizing.roomyCard(heightDp);
        float koreanSp = Sizing.cardKoreanSp(word.korean, widthDp, roomy);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_card);
        v.setViewVisibility(R.id.card_header, roomy ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.card_counter, counter);
        v.setTextViewText(R.id.card_korean, word.korean);
        v.setTextViewTextSize(R.id.card_korean, TypedValue.COMPLEX_UNIT_SP, koreanSp);
        v.setTextViewText(R.id.card_roman, word.roman);
        v.setViewVisibility(R.id.card_roman, roman ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.card_meaning, word.meaning);
        v.setInt(R.id.card_meaning, "setMaxLines", Sizing.cardMeaningLines(heightDp, koreanSp, roman));
        return v;
    }

    private static RemoteViews exampleCard(Context context, WordList.Word word, String counter,
                                           int widthDp, int heightDp, boolean roman) {
        boolean roomy = Sizing.roomyCard(heightDp);
        float sp = Sizing.exampleSp(word.example, widthDp, roomy);
        int lines = Sizing.exampleLines(word.example, widthDp, sp);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_example);
        v.setViewVisibility(R.id.card_header, roomy ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.card_counter, counter);
        v.setTextViewText(R.id.example_sentence, word.example);
        v.setTextViewTextSize(R.id.example_sentence, TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextViewText(R.id.example_roman, word.exampleRoman);
        v.setViewVisibility(R.id.example_roman, roman ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.example_translation, word.translation);
        v.setInt(R.id.example_translation, "setMaxLines", Sizing.translationLines(heightDp, sp, lines, roman));
        return v;
    }

    private static RemoteViews partsCard(Context context, WordList.Word word, String counter, int heightDp) {
        List<String> items = new ArrayList<>();
        for (WordList.Part part : word.parts) {
            items.add(part.piece + " = " + Sizing.shortGloss(part.gloss));
        }
        int lines = Sizing.partsLines(heightDp);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_parts);
        v.setTextViewText(R.id.card_counter, counter);
        v.setTextViewText(R.id.parts_sentence, word.example);
        v.setTextViewText(R.id.parts_body, Sizing.packLines(items, lines));
        v.setInt(R.id.parts_body, "setMaxLines", lines);
        return v;
    }

    private static RemoteViews combinedCard(Context context, WordList.Word word, String counter,
                                            int widthDp, int heightDp, boolean roman) {
        float koreanSp = Sizing.cardKoreanSp(word.korean, widthDp, false);
        float sentenceSp = Sizing.combinedSentenceSp(word.example, widthDp);
        int sentenceLines = Sizing.combinedSentenceLines(word.example, widthDp, sentenceSp);
        int[] rest = Sizing.combinedRest(heightDp, koreanSp, sentenceSp, sentenceLines, roman);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_combined);
        v.setTextViewText(R.id.card_counter, counter);
        v.setTextViewText(R.id.card_korean, word.korean);
        v.setTextViewTextSize(R.id.card_korean, TypedValue.COMPLEX_UNIT_SP, koreanSp);
        v.setTextViewText(R.id.card_roman, word.roman);
        v.setViewVisibility(R.id.card_roman, roman ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.card_meaning, word.meaning);
        v.setTextViewText(R.id.example_sentence, word.example);
        v.setTextViewTextSize(R.id.example_sentence, TypedValue.COMPLEX_UNIT_SP, sentenceSp);
        v.setTextViewText(R.id.example_roman, word.exampleRoman);
        v.setViewVisibility(R.id.example_roman, rest[1] == 1 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.example_translation, word.translation);
        if (rest[0] > 0) {
            List<String> items = new ArrayList<>();
            for (WordList.Part part : word.parts) {
                items.add(part.piece + " = " + Sizing.shortGloss(part.gloss));
            }
            v.setTextViewText(R.id.parts_body, Sizing.packLines(items, rest[0]));
            v.setInt(R.id.parts_body, "setMaxLines", rest[0]);
            v.setViewVisibility(R.id.parts_body, View.VISIBLE);
        } else {
            v.setViewVisibility(R.id.parts_body, View.GONE);
        }
        return v;
    }

    private static RemoteViews list(Context context, List<WordList.Word> words, int[] today, boolean roman) {
        String pkg = context.getPackageName();
        RemoteViews v = new RemoteViews(pkg, R.layout.widget_list);
        v.setTextViewText(R.id.list_day, context.getString(R.string.day_n, Store.dayNumber(context)));
        v.removeAllViews(R.id.list_rows);
        for (int index : today) {
            WordList.Word word = words.get(index);
            RemoteViews row = new RemoteViews(pkg, R.layout.widget_row);
            row.setTextViewText(R.id.row_korean, word.korean);
            row.setTextViewText(R.id.row_roman, word.roman);
            row.setViewVisibility(R.id.row_roman, roman ? View.VISIBLE : View.GONE);
            row.setTextViewText(R.id.row_meaning, word.meaning);
            if (word.hasExample()) {
                row.setTextViewText(R.id.row_example, word.example + "  " + word.translation);
                row.setViewVisibility(R.id.row_example, View.VISIBLE);
            } else {
                row.setViewVisibility(R.id.row_example, View.GONE);
            }
            v.addView(R.id.list_rows, row);
        }
        v.setOnClickPendingIntent(R.id.list_root, openAppIntent(context));
        return v;
    }

    /** Inexact and non-waking: it fires when the phone is next awake after midnight. */
    private static void scheduleMidnightRefresh(Context context) {
        ZoneId zone = ZoneId.systemDefault();
        long at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 30_000L;
        context.getSystemService(AlarmManager.class).set(AlarmManager.RTC, at, refreshIntent(context));
    }

    private static PendingIntent nextIntent(Context context, int id) {
        Intent intent = new Intent(context, WordWidget.class)
                .setAction(ACTION_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, WordWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Ids of every placed widget, both sizes. */
    private static int[] ids(Context context, AppWidgetManager manager) {
        int[] small = manager.getAppWidgetIds(new ComponentName(context, WordWidget.class));
        int[] big = manager.getAppWidgetIds(new ComponentName(context, WordWidgetBig.class));
        int[] all = Arrays.copyOf(small, small.length + big.length);
        System.arraycopy(big, 0, all, small.length, big.length);
        return all;
    }

    private static boolean isBig(AppWidgetManager manager, int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        return info != null && info.provider != null
                && WordWidgetBig.class.getName().equals(info.provider.getClassName());
    }

    /** The receiver is exported, so only act on ids that belong to this app's widgets. */
    private static boolean isOurs(Context context, int id) {
        for (int ours : ids(context, AppWidgetManager.getInstance(context))) {
            if (ours == id) {
                return true;
            }
        }
        return false;
    }
}
