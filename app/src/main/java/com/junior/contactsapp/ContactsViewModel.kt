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

    var searchQuery by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var contactToDelete by mutableStateOf<Contact?>(null)
        private set

    var selectedContactIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var showBatchDeleteDialog by mutableStateOf(false)
        private set

    var editingContact by mutableStateOf<Contact?>(null)
        private set

    var isAddingContact by mutableStateOf(false)
        private set

    /**
     * Contacts filtered client-side by current search query (matching name or phone number).
     */
    val filteredContacts: List<Contact>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return contacts
            val normalizedDigits = query.filter { it.isDigit() }
            return contacts.filter { contact ->
                val nameMatches = contact.name.contains(query, ignoreCase = true)
                val phoneMatches = contact.phoneNumber?.let { phone ->
                    phone.contains(query, ignoreCase = true) ||
                        (normalizedDigits.isNotEmpty() && phone.filter { it.isDigit() }.contains(normalizedDigits))
                } ?: false
                nameMatches || phoneMatches
            }
        }

    /**
     * Human-readable contact count: "X contacts" or "X of Y" when a search query is active.
     */
    val contactCountText: String
        get() {
            val total = contacts.size
            if (total == 0) return ""
            val filtered = filteredContacts.size
            return if (searchQuery.isNotBlank()) {
                "$filtered of $total"
            } else {
                "$total contact${if (total == 1) "" else "s"}"
            }
        }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
    }

    fun clearErrorMessage() {
        errorMessage = null
    }

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
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load contacts"
            } finally {
                isLoading = false
            }
        }
    }

    fun startEditingContact(contact: Contact) {
        editingContact = contact
    }

    fun cancelEditing() {
        editingContact = null
    }

    /**
     * Updates an existing contact's name and phone number asynchronously on Dispatchers.IO.
     */
    fun saveContact(contactId: Long, newName: String, newPhoneNumber: String?) {
        editingContact = null
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.updateContact(contactId, newName, newPhoneNumber)
                }
                if (success) {
                    val freshContacts = withContext(Dispatchers.IO) {
                        repository.getAllContacts()
                    }
                    contacts = freshContacts
                } else {
                    errorMessage = "Failed to update contact."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error updating contact"
            } finally {
                isLoading = false
            }
        }
    }

    fun startAddingContact() {
        isAddingContact = true
    }

    fun cancelAddingContact() {
        isAddingContact = false
    }

    /**
     * Adds a new contact asynchronously on Dispatchers.IO.
     */
    fun addContact(name: String, phoneNumber: String?) {
        isAddingContact = false
        viewModelScope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.addContact(name, phoneNumber)
                }
                if (success) {
                    val freshContacts = withContext(Dispatchers.IO) {
                        repository.getAllContacts()
                    }
                    contacts = freshContacts
                } else {
                    errorMessage = "Failed to add contact."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error adding contact"
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
                withContext(Dispatchers.IO) {
                    repository.deleteContact(target)
                }
                val freshContacts = withContext(Dispatchers.IO) {
                    repository.getAllContacts()
                }
                contacts = freshContacts
                selectedContactIds = selectedContactIds - target.contactId
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error deleting contact"
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
                withContext(Dispatchers.IO) {
                    repository.deleteContacts(targets)
                }
                val freshContacts = withContext(Dispatchers.IO) {
                    repository.getAllContacts()
                }
                contacts = freshContacts
                selectedContactIds = emptySet()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error deleting contacts"
            } finally {
                isLoading = false
            }
        }
    }
}
