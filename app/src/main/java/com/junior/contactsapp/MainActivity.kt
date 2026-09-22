package com.junior.contactsapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ContactsViewModel
    private var hasReadPermission by mutableStateOf(false)
    private var permissionsChecked by mutableStateOf(false)

    // Asks for both READ and WRITE contacts permission in one prompt.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val readGranted = grants[Manifest.permission.READ_CONTACTS] == true || hasPermission(Manifest.permission.READ_CONTACTS)
        hasReadPermission = readGranted
        permissionsChecked = true
        if (readGranted) {
            viewModel.loadContacts()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val readAlreadyGranted = hasPermission(Manifest.permission.READ_CONTACTS)
        val writeAlreadyGranted = hasPermission(Manifest.permission.WRITE_CONTACTS)
        hasReadPermission = readAlreadyGranted

        val needed = mutableListOf<String>()
        if (!readAlreadyGranted) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }
        if (!writeAlreadyGranted) {
            needed.add(Manifest.permission.WRITE_CONTACTS)
        }

        if (needed.isEmpty()) {
            permissionsChecked = true
            viewModel.loadContacts()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = ContactsRepository(contentResolver)
        viewModel = ContactsViewModel(repository)

        setContent {
            MaterialTheme {
                LaunchedEffect(Unit) {
                    requestPermissions()
                }

                ContactsScreen(
                    viewModel = viewModel,
                    hasReadPermission = hasReadPermission,
                    permissionsChecked = permissionsChecked,
                    onRequestPermission = { requestPermissions() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    hasReadPermission: Boolean,
    permissionsChecked: Boolean,
    onRequestPermission: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contacts") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !permissionsChecked -> {
                    // Waiting on the permission prompt result.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !hasReadPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Contacts permission is required to view and manage your contacts.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRequestPermission) {
                            Text("Grant Permission")
                        }
                    }
                }
                viewModel.contacts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No contacts found.")
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.contacts, key = { it.contactId }) { contact ->
                            ContactRow(
                                contact = contact,
                                onDeleteClick = { viewModel.requestDelete(contact) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Confirmation dialog before actually deleting anything.
            viewModel.contactToDelete?.let { contact ->
                AlertDialog(
                    onDismissRequest = { viewModel.cancelDelete() },
                    title = { Text("Delete contact?") },
                    text = { Text("Delete \"${contact.name}\" from your contacts? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmDelete() }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelDelete() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ContactRow(contact: Contact, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            if (contact.phoneNumber != null) {
                Text(
                    contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onDeleteClick) {
            Text("Delete")
        }
    }
}
