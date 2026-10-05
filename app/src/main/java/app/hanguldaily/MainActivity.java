package app.hanguldaily;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import java.util.List;

/** Today's words in full, plus the two settings. Opening it also redraws the widget. */
public final class MainActivity extends Activity {
    private RadioGroup perDay;
    private Switch showRoman;
    private boolean binding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        perDay = findViewById(R.id.per_day);
        showRoman = findViewById(R.id.show_roman);
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
        int[] today = Store.todaysIndices(this, words.size());
        boolean roman = Store.showRoman(this);

        TextView subtitle = findViewById(R.id.subtitle);
        subtitle.setText(getString(R.string.subtitle, Store.dayNumber(this), today[0] + 1, words.size()));

        LinearLayout list = findViewById(R.id.today_list);
        list.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int index : today) {
            WordList.Word word = words.get(index);
            View item = inflater.inflate(R.layout.item_word, list, false);
            ((TextView) item.findViewById(R.id.item_korean)).setText(word.korean);
            TextView romanView = item.findViewById(R.id.item_roman);
            romanView.setText(word.roman);
            romanView.setVisibility(roman ? View.VISIBLE : View.GONE);
            ((TextView) item.findViewById(R.id.item_meaning)).setText(word.meaning);
            list.addView(item);
        }

        int n = today.length;
        perDay.check(n == 3 ? R.id.per_day_3 : n == 4 ? R.id.per_day_4 : R.id.per_day_5);
        showRoman.setChecked(roman);
        binding = false;
    }
}
