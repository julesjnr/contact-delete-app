package com.junior.contactsapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {

    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set

    var contactToDelete by mutableStateOf<Contact?>(null)

    /** Re-reads the full contact list from the provider. Call after permission grant and after a delete. */
    fun loadContacts() {
        contacts = repository.getAllContacts()
    }

    fun requestDelete(contact: Contact) {
        contactToDelete = contact
    }

    fun cancelDelete() {
        contactToDelete = null
    }

    fun confirmDelete() {
        val target = contactToDelete ?: return
        repository.deleteContact(target)
        contactToDelete = null
        loadContacts()
    }
}
