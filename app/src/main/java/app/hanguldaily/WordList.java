package app.hanguldaily;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The bundled word list: one word per line, korean TAB romanization TAB meaning. */
final class WordList {
    static final class Word {
        final String korean;
        final String roman;
        final String meaning;

        Word(String korean, String roman, String meaning) {
            this.korean = korean;
            this.roman = roman;
            this.meaning = meaning;
        }
    }

    private WordList() {}

    static List<Word> parse(InputStream in) throws IOException {
        List<Word> words = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 3 || f[0].isEmpty() || f[2].isEmpty()) {
                throw new IOException("bad word line: " + line);
            }
            words.add(new Word(f[0], f[1], f[2]));
        }
        if (words.isEmpty()) {
            throw new IOException("word list is empty");
        }
        return Collections.unmodifiableList(words);
    }
}
