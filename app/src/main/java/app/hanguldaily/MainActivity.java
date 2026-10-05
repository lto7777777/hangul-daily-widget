package app.hanguldaily;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * Today's words in full, earlier days one tap away, and the two settings. Opening it
 * also redraws the widget.
 */
public final class MainActivity extends Activity {
    private static final String DAYS_BACK = "daysBack";

    private RadioGroup perDay;
    private Switch showRoman;
    private Button prevDay;
    private Button nextDay;
    private boolean binding;
    /** 0 shows today, 1 yesterday, and so on back to day 1. */
    private int daysBack;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        if (state != null) {
            daysBack = state.getInt(DAYS_BACK, 0);
        }
        perDay = findViewById(R.id.per_day);
        showRoman = findViewById(R.id.show_roman);
        prevDay = findViewById(R.id.prev_day);
        nextDay = findViewById(R.id.next_day);
        perDay.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding || checkedId == View.NO_ID) {
                return;
            }
            int n = checkedId == R.id.per_day_3 ? 3 : checkedId == R.id.per_day_4 ? 4 : 5;
            Store.setPerDay(this, n);
            refresh();
        });
        showRoman.setOnCheckedChangeListener((button, on) -> {
            if (binding) {
                return;
            }
            Store.setShowRoman(this, on);
            refresh();
        });
        prevDay.setOnClickListener(v -> {
            daysBack++;
            bind();
        });
        nextDay.setOnClickListener(v -> {
            daysBack--;
            bind();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(DAYS_BACK, daysBack);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        bind();
        WordWidget.updateAll(this);
    }

    private void bind() {
        binding = true;
        List<WordList.Word> words = Store.words(this);
        long dayNumber = Store.dayNumber(this);
        daysBack = (int) Math.max(0, Math.min(daysBack, dayNumber - 1));
        long day = Store.today() - daysBack;
        int[] shown = Store.indicesOn(this, day, words.size());
        boolean roman = Store.showRoman(this);

        ((TextView) findViewById(R.id.title)).setText(daysBack == 0 ? R.string.today : R.string.past_words);
        String date = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        TextView subtitle = findViewById(R.id.subtitle);
        subtitle.setText(getString(R.string.subtitle, dayNumber - daysBack, date, shown[0] + 1, words.size()));
        setEnabled(prevDay, daysBack < dayNumber - 1);
        setEnabled(nextDay, daysBack > 0);

        LinearLayout list = findViewById(R.id.today_list);
        list.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int index : shown) {
            WordList.Word word = words.get(index);
            View item = inflater.inflate(R.layout.item_word, list, false);
            ((TextView) item.findViewById(R.id.item_korean)).setText(word.korean);
            TextView romanView = item.findViewById(R.id.item_roman);
            romanView.setText(word.roman);
            romanView.setVisibility(roman ? View.VISIBLE : View.GONE);
            ((TextView) item.findViewById(R.id.item_meaning)).setText(word.meaning);
            bindExample(inflater, item, word, roman);
            list.addView(item);
        }

        int n = Store.perDay(this);
        perDay.check(n == 3 ? R.id.per_day_3 : n == 4 ? R.id.per_day_4 : R.id.per_day_5);
        showRoman.setChecked(roman);
        binding = false;
    }

    private static void setEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.3f);
    }

    private static void bindExample(LayoutInflater inflater, View item, WordList.Word word, boolean roman) {
        View block = item.findViewById(R.id.item_example_block);
        if (!word.hasExample()) {
            block.setVisibility(View.GONE);
            return;
        }
        block.setVisibility(View.VISIBLE);
        ((TextView) item.findViewById(R.id.item_example)).setText(word.example);
        TextView exampleRoman = item.findViewById(R.id.item_example_roman);
        exampleRoman.setText(word.exampleRoman);
        exampleRoman.setVisibility(roman ? View.VISIBLE : View.GONE);
        ((TextView) item.findViewById(R.id.item_translation)).setText(word.translation);

        LinearLayout parts = item.findViewById(R.id.item_parts);
        parts.removeAllViews();
        for (WordList.Part part : word.parts) {
            View row = inflater.inflate(R.layout.item_part, parts, false);
            ((TextView) row.findViewById(R.id.part_piece)).setText(part.piece);
            ((TextView) row.findViewById(R.id.part_gloss)).setText(part.gloss);
            parts.addView(row);
        }
    }
}
