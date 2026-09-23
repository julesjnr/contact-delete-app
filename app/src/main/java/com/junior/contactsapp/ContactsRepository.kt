package com.junior.contactsapp

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract

/**
 * All reads/writes to the Android Contacts provider live here, kept separate
 * from the UI so MainActivity/ViewModel stay simple.
 */
class ContactsRepository(private val contentResolver: ContentResolver) {

    /**
     * Returns every contact that has at least a display name, sorted
     * alphabetically. Phone number is looked up as a second query per
     * contact.
     */
    fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val hasPhoneIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val lookupKey = it.getString(lookupIndex) ?: continue
                val name = it.getString(nameIndex) ?: "(No name)"
                val hasPhone = it.getInt(hasPhoneIndex) > 0

                val phoneNumber = if (hasPhone) getPhoneNumber(id) else null

                contacts.add(Contact(id, lookupKey, name, phoneNumber))
            }
        }

        return contacts
    }

    private fun getPhoneNumber(contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                return cursor.getString(numberIndex)
            }
        }
        return null
    }

    /**
     * Finds the raw contact ID associated with an aggregate contact ID.
     */
    fun getRawContactId(contactId: Long): Long? {
        val projection = arrayOf(ContactsContract.RawContacts._ID)
        val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                return cursor.getLong(idIndex)
            }
        }
        return null
    }

    /**
     * Checks if a Data row of the specified mimetype exists for a given raw contact ID.
     */
    private fun hasDataRow(rawContactId: Long, mimeType: String): Boolean {
        val projection = arrayOf(ContactsContract.Data._ID)
        val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(rawContactId.toString(), mimeType)

        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    /**
     * Updates an existing contact's display name and phone number using a batch
     * ContentProviderOperation applied to ContactsContract.AUTHORITY.
     *
     * Modifies the StructuredName.DISPLAY_NAME and Phone.NUMBER rows corresponding
     * to the contact's raw contact ID. If no existing Phone row is found and a phone
     * number is provided, an insert operation is performed instead of update.
     */
    fun updateContact(contactId: Long, newName: String, newPhoneNumber: String?): Boolean {
        val rawContactId = getRawContactId(contactId) ?: return false
        val ops = ArrayList<ContentProviderOperation>()

        // 1. StructuredName row: update if present, otherwise insert
        val hasNameRow = hasDataRow(rawContactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        if (hasNameRow) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(
                            rawContactId.toString(),
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                    .build()
            )
        } else {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                    .build()
            )
        }

        // 2. Phone row: update if exists, insert if none exists, or delete if cleared
        val trimmedPhone = newPhoneNumber?.trim()
        val hasPhoneRow = hasDataRow(rawContactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)

        if (!trimmedPhone.isNullOrEmpty()) {
            if (hasPhoneRow) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(
                                rawContactId.toString(),
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            } else {
                // Contact has no existing phone Data row yet -> insert one
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }
        } else {
            // User cleared phone number: delete existing row if one was present
            if (hasPhoneRow) {
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(
                                rawContactId.toString(),
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                        )
                        .build()
                )
            }
        }

        return try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            android.util.Log.e(
                "ContactsRepository",
                "Update of contact $contactId failed: ${e.javaClass.simpleName}: ${e.message}",
                e
            )
            false
        }
    }

    /**
     * Inserts a new contact using a batch ContentProviderOperation.
     * Inserts a new row into RawContacts, followed by StructuredName and Phone Data rows
     * linked to the new raw contact via withValueBackReference.
     */
    fun addContact(name: String, phoneNumber: String?): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        // 1. Insert into RawContacts
        val rawContactOpIndex = 0
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // 2. Insert StructuredName linked to rawContactOpIndex
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        // 3. Insert Phone linked to rawContactOpIndex (if provided)
        val trimmedPhone = phoneNumber?.trim()
        if (!trimmedPhone.isNullOrEmpty()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.e(
                "ContactsRepository",
                "Add contact \"$name\" failed: ${e.javaClass.simpleName}: ${e.message}",
                e
            )
            false
        }
    }

    /**
     * Deletes the given contact from the device's Contacts provider.
     * Uses the lookup URI (recommended over the raw _ID URI) because a
     * contact can be an aggregate of several raw contacts, and the lookup
     * URI resolves to whichever raw rows currently make it up.
     *
     * Returns true if a row was actually deleted.
     */
    fun deleteContact(contact: Contact): Boolean {
        val lookupUri: Uri = ContactsContract.Contacts.getLookupUri(
            contact.contactId,
            contact.lookupKey
        )
        val rowsDeleted = contentResolver.delete(lookupUri, null, null)
        return rowsDeleted > 0
    }

    /**
     * Deletes multiple contacts from the device's Contacts provider using
     * batch ContentProviderOperation.
     *
     * Returns the count of successfully deleted contacts.
     */
    fun deleteContacts(contacts: List<Contact>): Int {
        if (contacts.isEmpty()) return 0
        val ops = ArrayList<ContentProviderOperation>()
        for (contact in contacts) {
            val lookupUri = ContactsContract.Contacts.getLookupUri(
                contact.contactId,
                contact.lookupKey
            )
            ops.add(ContentProviderOperation.newDelete(lookupUri).build())
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            // Each ContentProviderResult corresponds 1:1 with an op, but a delete op
            // that matched zero rows still produces a result — so results.size would
            // overcount. Sum the actual affected-row counts instead.
            results.sumOf { it.count ?: 0 }
        } catch (e: Exception) {
            android.util.Log.e(
                "ContactsRepository",
                "Batch delete of ${contacts.size} contact(s) failed: ${e.javaClass.simpleName}: ${e.message}",
                e
            )
            // Fallback to individual deletion if batch fails
            var deletedCount = 0
            for (contact in contacts) {
                if (deleteContact(contact)) {
                    deletedCount++
                }
            }
            deletedCount
        }
    }
}
