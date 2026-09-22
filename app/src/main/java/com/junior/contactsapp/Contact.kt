package com.junior.contactsapp

/**
 * Minimal representation of a device contact.
 *
 * [contactId] is the row _ID from ContactsContract.Contacts — it is what we
 * need to delete the contact later, so we keep it around even though the UI
 * only shows [name] and [phoneNumber].
 */
data class Contact(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val phoneNumber: String?
)
