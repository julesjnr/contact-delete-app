package com.junior.contactsapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {

    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var contactToDelete by mutableStateOf<Contact?>(null)
        private set

    var selectedContactIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var showBatchDeleteDialog by mutableStateOf(false)
        private set

    /**
     * Re-reads the full contact list from the provider asynchronously off the main thread.
     */
    fun loadContacts() {
        viewModelScope.launch {
            isLoading = true
            try {
                val freshContacts = withContext(Dispatchers.IO) {
                    repository.getAllContacts()
                }
                contacts = freshContacts
                // Keep only selections that still exist
                selectedContactIds = selectedContactIds.intersect(freshContacts.map { it.contactId }.toSet())
            } finally {
                isLoading = false
            }
        }
    }

    fun requestDelete(contact: Contact) {
        contactToDelete = contact
    }

    fun cancelDelete() {
        contactToDelete = null
    }

    /**
     * Deletes a single contact asynchronously off the main thread.
     */
    fun confirmDelete() {
        val target = contactToDelete ?: return
        contactToDelete = null

        viewModelScope.launch {
            isLoading = true
            try {
                val freshContacts = withContext(Dispatchers.IO) {
                    repository.deleteContact(target)
                    repository.getAllContacts()
                }
                contacts = freshContacts
                selectedContactIds = selectedContactIds - target.contactId
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleSelectContact(contactId: Long) {
        selectedContactIds = if (contactId in selectedContactIds) {
            selectedContactIds - contactId
        } else {
            selectedContactIds + contactId
        }
    }

    fun selectAll() {
        selectedContactIds = contacts.map { it.contactId }.toSet()
    }

    fun clearSelection() {
        selectedContactIds = emptySet()
    }

    fun requestDeleteSelected() {
        if (selectedContactIds.isNotEmpty()) {
            showBatchDeleteDialog = true
        }
    }

    fun cancelDeleteSelected() {
        showBatchDeleteDialog = false
    }

    /**
     * Batch deletes all currently selected contacts asynchronously off the main thread.
     */
    fun confirmDeleteSelected() {
        val idsToDelete = selectedContactIds
        if (idsToDelete.isEmpty()) return
        showBatchDeleteDialog = false

        viewModelScope.launch {
            isLoading = true
            try {
                val targets = contacts.filter { it.contactId in idsToDelete }
                val freshContacts = withContext(Dispatchers.IO) {
                    repository.deleteContacts(targets)
                    repository.getAllContacts()
                }
                contacts = freshContacts
                selectedContactIds = emptySet()
            } finally {
                isLoading = false
            }
        }
    }
}
