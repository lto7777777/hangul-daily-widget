# Hangul Daily

A home-screen widget for Android that shows 3 to 5 Korean words a day, most common words first. It was made for a Huawei P30 Pro and sized to take the place of the Duolingo streak widget.

## What it does

- The first 248 words are everyday ones: greetings, people, family, food, places, common verbs, numbers and time. Each day mixes one of each kind, so day 1 is 안녕, 하다, 사람, 좋다, 하나. After those, the rest of the list follows in frequency order. Each day moves on by 3, 4 or 5 words, and after the last word it starts over.
- In a small slot (2 columns by 1 row) the widget is a card with three sides. Tap once and the word (Hangul, romanization, meaning) turns into an example sentence with its translation. Tap again and you see what each part of that sentence means, for example 안녕 = peace · 하 = be, do · 세요 = respectful polite ending. The next tap moves on to the next word. The card also steps forward by itself every hour.
- The widget picker also offers **Hangul Daily (2×2)**. In that size the card shows the word, its example, the translation and the parts all at once, so one tap moves to the next word.
- Make the widget taller still and it lists all of today's words.
- Open the app to see today's words in large type, change how many you get per day, or hide the romanization. **‹ Previous day** goes back through earlier days, each with the words it had, even if you changed how many words per day in between.
- It works offline and asks for no permissions.

## Install on the phone

1. On the phone, sign in to GitHub in the browser and open this repository's **Releases** page.
2. Under the newest release, tap `hangul-daily.apk`. When the download finishes, open it.
3. Android asks whether to allow installs from this source (the browser or Files). Allow it, go back, and tap **Install**. Huawei may warn that AppGallery has not checked the app; install anyway.
4. If you want the Duolingo widget's spot, long-press that widget and tap **Remove**.
5. Pinch the home screen with two fingers, tap **Widgets**, find **Hangul Daily** and drag it into place.

If the words don't change after midnight, the battery manager is holding the app back. Open **Settings → Battery → App launch**, find Hangul Daily, switch it to manual and allow it to run in the background.

## Updating without losing your place

Android only installs an update over the old app when both are signed with the same key. Without a stored key, each build gets a new one, so updating means uninstalling first and starting again from day 1.

To keep one key, create it once on a PC that has Java:

```bash
keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Hangul Daily Debug"
```

Encode it (in PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("debug.keystore"))`):

```bash
base64 -w0 debug.keystore
```

Then on GitHub open **Settings → Secrets and variables → Actions → New repository secret**, name it `KEYSTORE_B64` and paste the encoded text. Keep `debug.keystore` somewhere safe, outside the repository.

## How it is built

Every push to `main` runs `.github/workflows/build.yml` on GitHub's Ubuntu runner, which already has the Android SDK. The workflow runs `tools/build_apk.py`, which:

1. tests the romanizer and the word-list parser (`tools/test_tools.py`)
2. turns `data/5324kor.txt` into `app/src/main/assets/words.tsv` (`tools/build_words.py`)
3. compiles the resources with `aapt2` and the code with `javac`, checks the day and layout logic (`tools/LogicTest.java`) and converts to dex with `d8`
4. aligns and signs the APK, then checks it with `apksigner verify` and `aapt2 dump badging`

There is no Gradle. The APK is attached to a release named `build-N`, and the full build log goes to the `ci-report` branch.

To build on a PC instead, install JDK 17 or newer and the Android SDK packages `platforms;android-35` and `build-tools;35.0.1`, then run `python tools/build_apk.py`.

## The word list

`data/5324kor.txt` is copied from the Korean study web app this widget grew out of. It holds Korean words in frequency order with English meanings. The build cleans it up:

- 53 meanings had spilled onto a second line; they are joined back. The web app had been showing them cut short.
- Stray numbers after some words (`기 13`, `구 15`) are dropped.
- Exact duplicates are removed. Words with several meanings stay as separate entries, so 말 shows up as "words, speaking", "end" and "horse". That leaves 5,643 entries.

The list is ranked by how often words appear in written Korean, so on its own it puts 안녕 at word 4,732 and newspaper words like 정부 (government) near the top. Three hand-written files sit next to it:

- `data/everyday_first.txt` picks 248 everyday words and moves them to the front. It has five sections (basics, verbs, nouns, describing words, numbers and time), and the build takes one word from each in turn. A few beginner words are not in the source list at all, so they cannot be included: 둘, 동생, 딸, 듣다, 돕다, 어떻게, 또, 나중에.
- `data/examples.tsv` has one example sentence per word, with a translation and the sentence split into parts, each with its meaning. The source list had no examples, so these were written for this app; they are not from a dictionary. The build rejects an example whose parts don't join back into the sentence, or a verb whose dictionary form (가다, 먹다…) isn't named in its part. The first 306 words have examples, which is about two months at 5 words a day; later words show only the word until more are added.
- `data/corrections.tsv` fixes meanings in the source list that are wrong or misleading, such as 여기 listed as "A hobby" (it means "here"). Each line gives the reason.

Romanization follows the Revised Romanization of Korean, applied to how a word is pronounced: 한국어 → hangugeo, 국민 → gungmin, 같이 → gachi. One difference is deliberate: ㅎ sound changes apply to nouns too (축하 → chuka, where the official spelling is chukha), because the point is to show how the word sounds. Cases that need a dictionary are not covered. For example, compounds that insert an ㄴ come out wrong (솜이불 gives somibul instead of somnibul).

## Files

- `app/src/main/java/app/hanguldaily/`: the widget (`WordWidget`), the app screen (`MainActivity`), settings (`Store`), day logic (`DailyPlan`), layout sizing (`Sizing`) and the list reader (`WordList`)
- `app/src/main/res/`: layouts, colours for light and dark mode, the icon
- `tools/`: build, data and test scripts
- `data/5324kor.txt`: the source word list
