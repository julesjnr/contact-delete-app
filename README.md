# ContactsApp

A Kotlin + Jetpack Compose Android app for managing the contacts stored on
your device (and any accounts synced to it, e.g. Google). It talks directly
to the system Contacts provider — there's no separate database or backend.

## Features

- **List** all contacts (name + phone number), sorted alphabetically.
- **Search** by name or phone number, live as you type (digits-only matching
  works too, so partial numbers match regardless of formatting).
- **Add** a new contact (name required, phone number optional).
- **Edit** an existing contact's name and phone number.
- **Delete** a single contact, with a confirmation dialog.
- **Batch delete**: long-press a contact to enter selection mode, tap more to
  multi-select, "Select All" (respects the active search filter), then delete
  them all at once.
- **Permission handling**: requests `READ_CONTACTS` + `WRITE_CONTACTS` on
  launch, distinguishes a normal denial (can re-prompt) from a permanent
  denial (sends the user to the app's system Settings page), and re-checks
  permission state automatically when returning from Settings.
- **Storage notice**: an info dialog explaining that contacts are saved to
  device/account storage, and that direct SIM-card storage isn't supported
  (SIM storage needs vendor-specific URIs, per-slot account types, and has
  strict name-length limits — not worth the complexity here).

## How to run

1. Open this folder in **Android Studio** (File → Open → select the
   `ContactsApp` project root). Let it sync Gradle — first sync downloads the
   Android Gradle Plugin and dependencies, so you need an internet
   connection.
2. Run on an emulator or a real device (**API 24+ / Android 7.0+**).
3. On first launch the app asks for **Contacts** permission (read + write).
   Grant it — the list won't load and add/edit/delete won't work otherwise.
4. If you're on a fresh emulator, add a couple of dummy contacts first
   (system Contacts app → Add contact), since a fresh emulator has none.

## Where things live

- `Contact.kt` — the app's data model: `contactId` (provider row `_ID`),
  `lookupKey`, `name`, `phoneNumber`.
- `ContactsRepository.kt` — all `ContentResolver` calls: querying
  `ContactsContract.Contacts` for the list, `ContactsContract.CommonDataKinds.Phone`
  for numbers, batch insert/update/delete via `ContentProviderOperation`, and
  deleting through the contact's lookup URI.
- `ContactsViewModel.kt` — screen state (contact list, search query, loading/
  error state, selection set, which contact is being edited or pending
  delete) and the coroutines that call into the repository off the main
  thread.
- `MainActivity.kt` — permission request flow + all Compose UI: the contact
  list screen (search bar, selection mode, dialogs) and the add/edit form
  screen.

## Notes / things you may want to change

- **Runtime permissions only cover reading/writing contacts you didn't create
  as "restricted" via a Device Policy** — on a normal personal phone this
  isn't an issue.
- Deletion goes through `ContactsContract.Contacts.getLookupUri(...)`, which
  is the recommended way (safer than deleting by raw `_ID`, since a contact
  can be merged from multiple raw contacts — the lookup URI resolves to
  whichever raw rows currently make it up).
- There's no "undo" — the confirmation dialog is the only safety net, matching
  how the stock Android Contacts app behaves.
- The phone number lookup does one extra query per contact. Fine for normal
  contact-list sizes; if you expect thousands of contacts, switch to a single
  `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` query joined by
  `CONTACT_ID` instead of querying per-row.
- New contacts are inserted with a `null` account type/name, i.e. as a
  local, phone-only contact rather than tied to a synced account (e.g.
  Google) — they'll stay on-device unless the user later merges/moves them.
