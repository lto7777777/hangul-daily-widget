package app.hanguldaily;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bundled word list, one word per line, tab-separated:
 * korean, romanization, meaning, example, example romanization, translation, parts.
 * The last four are empty for words without an example; parts is 'piece=gloss|piece=gloss'.
 */
final class WordList {
    static final class Part {
        final String piece;
        final String gloss;

        Part(String piece, String gloss) {
            this.piece = piece;
            this.gloss = gloss;
        }
    }

    static final class Word {
        final String korean;
        final String roman;
        final String meaning;
        final String example;
        final String exampleRoman;
        final String translation;
        final List<Part> parts;

        Word(String korean, String roman, String meaning, String example, String exampleRoman,
             String translation, List<Part> parts) {
            this.korean = korean;
            this.roman = roman;
            this.meaning = meaning;
            this.example = example;
            this.exampleRoman = exampleRoman;
            this.translation = translation;
            this.parts = parts;
        }

        boolean hasExample() {
            return !example.isEmpty();
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
            if ((f.length != 3 && f.length != 7) || f[0].isEmpty() || f[2].isEmpty()) {
                throw new IOException("bad word line: " + line);
            }
            if (f.length == 3 || f[3].isEmpty()) {
                words.add(new Word(f[0], f[1], f[2], "", "", "", Collections.<Part>emptyList()));
            } else {
                words.add(new Word(f[0], f[1], f[2], f[3], f[4], f[5], parseParts(f[6], line)));
            }
        }
        if (words.isEmpty()) {
            throw new IOException("word list is empty");
        }
        return Collections.unmodifiableList(words);
    }

    private static List<Part> parseParts(String text, String line) throws IOException {
        List<Part> parts = new ArrayList<>();
        for (String chunk : text.split("\\|")) {
            int eq = chunk.indexOf('=');
            if (eq <= 0 || eq == chunk.length() - 1) {
                throw new IOException("bad parts in line: " + line);
            }
            parts.add(new Part(chunk.substring(0, eq), chunk.substring(eq + 1)));
        }
        return Collections.unmodifiableList(parts);
    }
}
