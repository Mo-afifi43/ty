# SmartLearn

A small Android client for the MUST Smart Learning portal (Moodle), written because the
website is painful on a phone: pages take forever, the session drops halfway through a
lecture, and every PDF is three menus deep.

SmartLearn signs you in through the university's normal login page, then quietly reads
the same pages you would open in a browser and keeps a copy on the phone. Courses, weekly
sections, files and deadlines all work offline. Lecture files download with one tap and
open in whatever app you already use for PDFs and slides.

## What it does

- **Today** – upcoming assignments and quizzes with a countdown ("Due tomorrow",
  "Overdue by 2 days"), a tick box to mark things done, and quick links to your courses.
- **Courses** – every course you're enrolled in, with weekly sections you can expand.
  Files show a download icon; assignments show their due date; anything else opens the
  portal page inside the app (already signed in).
- **Downloads** – everything you saved, grouped by course, with sizes. Open, share or
  remove from here.
- If Moodle logs you out, nothing disappears. A banner at the bottom asks you to sign in
  again and refreshing resumes from there.

## How it works

The portal uses Microsoft SSO and has the mobile web services switched off, so this app
does what a browser does:

1. **Sign in** happens in a WebView on the real login page. The app never sees the
   password. When the WebView lands on `/my/` the `MoodleSession` cookie is copied out of
   `CookieManager` and stored in `EncryptedSharedPreferences`.
2. **Fetching** uses OkHttp with that cookie attached. Redirects are followed by hand so
   a bounce to `/login/` (or straight to Microsoft) is recognised as "session expired"
   before it turns into a confusing HTML page.
3. **Parsing** is Jsoup against the Moodle 4 (Boost) markup, with fallbacks for the older
   3.x layout. Moodle 4 renders the "My courses" list and the timeline with JavaScript,
   so when the static HTML is empty the app makes the same `lib/ajax/service.php` call the
   page itself makes, using the `sesskey` found on the page. The profile page is a last
   resort for the course list.
4. **Storage** is Room. Screens only ever read from the database; a background refresh
   rewrites the tables and the UI updates on its own.
5. **Downloads** stream through OkHttp into
   `getExternalFilesDir(DIRECTORY_DOWNLOADS)/<course code>/` and are handed to other apps
   through a `FileProvider`.

### Project layout

```
app/src/main/java/com/mhma/smartlearn
├── data
│   ├── network      OkHttp client, session store, cookie interceptor
│   │   └── scraper  Jsoup parsing (pure Kotlin, unit tested)
│   ├── local        Room database, entities, DAOs
│   ├── repository   offline-first repositories
│   ├── download     background file downloader
│   └── sync         coordinates a full refresh
├── di               Hilt module
├── ui
│   ├── login        WebView sign-in
│   ├── overview     "Today" tab
│   ├── courses      course list and course detail
│   ├── downloads    offline files
│   ├── web          in-app browser for portal pages
│   ├── navigation   root + bottom tabs
│   ├── components   shared bits (chips, empty states, banner)
│   └── theme        Material 3 colours and type
└── util             dates, file names, opening files
```

## Building

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration, or:

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requirements: JDK 17+, Android SDK 36. `minSdk` is 26.

The parser tests in `app/src/test` run against saved copies of Moodle pages in
`app/src/test/resources`. If the portal's theme changes and something stops showing up,
save the new page there and adjust the selectors in `MoodleParser`.

## Things worth knowing

- Dates on the calendar page are printed in the portal's language. English formats are
  handled; anything else falls back to the timeline service, which returns real timestamps.
- Courses laid out as "one section per page" are fetched section by section. Folders are
  opened so each file inside gets its own download button.
- If a lecturer removes a file from a course, your downloaded copy stays in Downloads
  until you delete it.
- Microsoft sign-in occasionally refuses embedded browsers. The WebView identifies itself
  as a normal Chrome for that reason.

## Author

Built and maintained by **M.H.M.A**.
