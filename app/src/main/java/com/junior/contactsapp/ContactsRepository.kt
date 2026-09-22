package com.junior.contactsapp

import android.content.ContentResolver
import android.content.ContentUris
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
     * contact (fine for typical contact-list sizes; for very large lists
     * you'd want a JOIN-style single query instead).
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
     * Deletes multiple contacts from the device's Contacts provider by
     * looping over each contact and calling contentResolver.delete(...) per contact.
     *
     * Returns the count of successfully deleted contacts.
     */
    fun deleteContacts(contacts: List<Contact>): Int {
        var deletedCount = 0
        for (contact in contacts) {
            val lookupUri: Uri = ContactsContract.Contacts.getLookupUri(
                contact.contactId,
                contact.lookupKey
            )
            val rowsDeleted = contentResolver.delete(lookupUri, null, null)
            if (rowsDeleted > 0) {
                deletedCount++
            }
        }
        return deletedCount
    }
}
