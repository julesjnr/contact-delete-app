# ContactsApp

A minimal Android app (Kotlin + Jetpack Compose) that:

1. Reads and lists all contacts on the device.
2. Lets you tap **Delete** next to any contact, confirm, and remove it from
   the device's Contacts provider.

## How to run

1. Open this folder in **Android Studio** (File → Open → select `ContactsApp`).
   Let it sync Gradle — first sync will download the Android Gradle Plugin
   and dependencies, so you need an internet connection.
2. Run on an emulator or a real device (API 24+ / Android 7.0+).
3. On first launch the app asks for **Contacts** permission (read + write).
   Grant it — the list won't load and delete won't work otherwise.
4. If you're on an emulator, add a couple of dummy contacts first
   (Contacts app → Add contact), since a fresh emulator has none.

## Where things live

- `ContactsRepository.kt` — all `ContentResolver` calls: querying
  `ContactsContract.Contacts` for the list, `ContactsContract.CommonDataKinds.Phone`
  for numbers, and `contentResolver.delete(...)` on the contact's lookup URI
  to delete.
- `ContactsViewModel.kt` — holds the in-memory list and which contact (if any)
  is pending a delete confirmation.
- `MainActivity.kt` — permission request flow + the Compose UI (list, delete
  button, confirmation dialog).

## Notes / things you may want to change

- **Runtime permissions only cover reading/writing contacts you didn't create
  as "restricted" via a Device Policy** — on a normal personal phone this
  isn't an issue.
- Deletion goes through `ContactsContract.Contacts.getLookupUri(...)`, which
  is the recommended way (safer than deleting by raw `_ID`, since a contact
  can be merged from multiple raw contacts — the lookup URI resolves to all
  of them).
- There's no "undo" — the confirmation dialog is the only safety net, matching
  how the stock Android Contacts app behaves.
- The phone number lookup does one extra query per contact. Fine for normal
  contact-list sizes; if you expect thousands of contacts, switch to a single
  `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` query joined by
  `CONTACT_ID` instead of querying per-row.
