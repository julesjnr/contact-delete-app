package com.junior.contactsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ContactsViewModel
    private var hasPermissions by mutableStateOf(false)
    private var permissionsChecked by mutableStateOf(false)
    private var permissionsRequestedOnce by mutableStateOf(false)

    // Asks for both READ and WRITE contacts permissions in one prompt.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val readGranted = grants[Manifest.permission.READ_CONTACTS] == true || hasPermission(Manifest.permission.READ_CONTACTS)
        val writeGranted = grants[Manifest.permission.WRITE_CONTACTS] == true || hasPermission(Manifest.permission.WRITE_CONTACTS)
        hasPermissions = readGranted && writeGranted
        permissionsChecked = true
        permissionsRequestedOnce = true
        if (readGranted) {
            viewModel.loadContacts()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissions() {
        val readAlreadyGranted = hasPermission(Manifest.permission.READ_CONTACTS)
        val writeAlreadyGranted = hasPermission(Manifest.permission.WRITE_CONTACTS)
        val granted = readAlreadyGranted && writeAlreadyGranted
        hasPermissions = granted
        if (granted && viewModel.contacts.isEmpty() && !viewModel.isLoading) {
            viewModel.loadContacts()
        }
    }

    private fun requestPermissions() {
        val readAlreadyGranted = hasPermission(Manifest.permission.READ_CONTACTS)
        val writeAlreadyGranted = hasPermission(Manifest.permission.WRITE_CONTACTS)

        if (readAlreadyGranted && writeAlreadyGranted) {
            hasPermissions = true
            permissionsChecked = true
            viewModel.loadContacts()
        } else {
            val needed = mutableListOf<String>()
            if (!readAlreadyGranted) needed.add(Manifest.permission.READ_CONTACTS)
            if (!writeAlreadyGranted) needed.add(Manifest.permission.WRITE_CONTACTS)
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun isPermanentlyDenied(): Boolean {
        val readDenied = !hasPermission(Manifest.permission.READ_CONTACTS)
        val writeDenied = !hasPermission(Manifest.permission.WRITE_CONTACTS)
        if (!readDenied && !writeDenied) return false

        val readRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)
        val writeRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_CONTACTS)

        return permissionsRequestedOnce && (!readRationale || !writeRationale)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = ContactsRepository(contentResolver)
        viewModel = ContactsViewModel(repository)

        setContent {
            MaterialTheme {
                val lifecycleOwner = LocalLifecycleOwner.current

                // Check permissions when returning from App Settings
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            checkPermissions()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    requestPermissions()
                }

                val currentEditingContact = viewModel.editingContact
                when {
                    currentEditingContact != null -> {
                        ContactFormScreen(
                            title = "Edit Contact",
                            initialName = currentEditingContact.name,
                            initialPhone = currentEditingContact.phoneNumber ?: "",
                            saveButtonText = "Save Changes",
                            isLoading = viewModel.isLoading,
                            onSave = { newName, newPhone ->
                                viewModel.saveContact(currentEditingContact.contactId, newName, newPhone)
                            },
                            onBack = { viewModel.cancelEditing() }
                        )
                    }
                    viewModel.isAddingContact -> {
                        ContactFormScreen(
                            title = "Add Contact",
                            initialName = "",
                            initialPhone = "",
                            saveButtonText = "Save Contact",
                            isLoading = viewModel.isLoading,
                            onSave = { name, phone ->
                                viewModel.addContact(name, phone)
                            },
                            onBack = { viewModel.cancelAddingContact() }
                        )
                    }
                    else -> {
                        ContactsScreen(
                            viewModel = viewModel,
                            hasPermissions = hasPermissions,
                            permissionsChecked = permissionsChecked,
                            isPermanentlyDenied = isPermanentlyDenied(),
                            onRequestPermission = { requestPermissions() },
                            onOpenSettings = { openAppSettings() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    hasPermissions: Boolean,
    permissionsChecked: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val selectedCount = viewModel.selectedContactIds.size
    val isSelectionMode = selectedCount > 0
    var showSimInfoDialog by remember { mutableStateOf(false) }

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "$selectedCount selected" else "Contacts",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (isSelectionMode) {
                            Text(
                                text = "Tap contacts to select or deselect",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (viewModel.contactCountText.isNotEmpty()) {
                            Text(
                                text = "${viewModel.contactCountText} • Device & Accounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection mode"
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val visibleContacts = viewModel.filteredContacts
                        val allVisibleSelected = visibleContacts.isNotEmpty() &&
                            visibleContacts.all { it.contactId in viewModel.selectedContactIds }
                        if (allVisibleSelected) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Deselect All")
                            }
                        } else {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("Select All")
                            }
                        }
                    } else {
                        IconButton(onClick = { showSimInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Storage Information"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasPermissions && !isSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.startAddingContact() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Contact"
                    )
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { viewModel.requestDeleteSelected() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete Selected ($selectedCount)")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !permissionsChecked -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !hasPermissions -> {
                    // Permission edge cases: normal request vs permanent denial fallback
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isPermanentlyDenied) {
                                "Contacts permission is permanently denied.\n\nPlease enable Contacts access in App Settings to view, edit, and create contacts."
                            } else {
                                "Contacts permission is required to view, edit, and manage your contacts."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isPermanentlyDenied) {
                            Button(onClick = onOpenSettings) {
                                Text("Open App Settings")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onRequestPermission) {
                                Text("Check Again")
                            }
                        } else {
                            Button(onClick = onRequestPermission) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
                viewModel.isLoading && viewModel.contacts.isEmpty() -> {
                    // Loading during initial provider query
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.contacts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No contacts found.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap the + button to add a contact.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Live search bar
                        OutlinedTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Search by name or number...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            },
                            trailingIcon = {
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )

                        if (viewModel.filteredContacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No contacts matching \"${viewModel.searchQuery}\"")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(viewModel.filteredContacts, key = { it.contactId }) { contact ->
                                    val isSelected = contact.contactId in viewModel.selectedContactIds
                                    ContactRow(
                                        contact = contact,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onToggleSelect = { viewModel.toggleSelectContact(contact.contactId) },
                                        onClick = { viewModel.startEditingContact(contact) },
                                        onDeleteClick = { viewModel.requestDelete(contact) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Top progress bar during background I/O operations (save, delete, refresh)
            if (viewModel.isLoading && viewModel.contacts.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // Error notification snackbar
            viewModel.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearErrorMessage() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Confirmation dialog before deleting a single contact
            viewModel.contactToDelete?.let { contact ->
                AlertDialog(
                    onDismissRequest = { viewModel.cancelDelete() },
                    title = { Text("Delete contact?") },
                    text = { Text("Delete \"${contact.name}\" from your contacts? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmDelete() }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelDelete() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Confirmation dialog before deleting multiple selected contacts
            if (viewModel.showBatchDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelDeleteSelected() },
                    title = { Text("Delete selected contacts?") },
                    text = {
                        Text(
                            "Delete $selectedCount selected contact${if (selectedCount > 1) "s" else ""} from your contacts? This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmDeleteSelected() }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelDeleteSelected() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Storage and Dual-SIM Information Dialog
            if (showSimInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showSimInfoDialog = false },
                    title = { Text("Contact Storage Notice") },
                    text = {
                        Text(
                            "This app manages contacts stored on your device and synced cloud accounts (such as Google).\n\n" +
                            "Direct SIM card storage requires vendor-specific URIs (e.g. content://icc/adn), different account types per SIM slot, and imposes strict name length limits. " +
                            "For reliability and full contact field support, contacts are created and edited in device/account storage."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSimInfoDialog = false }) {
                            Text("Got it")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRow(
    contact: Contact,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onToggleSelect
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() }
        )
        Spacer(modifier = Modifier.width(12.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFormScreen(
    title: String,
    initialName: String,
    initialPhone: String,
    saveButtonText: String,
    isLoading: Boolean,
    onSave: (newName: String, newPhoneNumber: String?) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var phoneNumber by remember(initialPhone) { mutableStateOf(initialPhone) }

    BackHandler(enabled = !isLoading, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                placeholder = { Text("Full name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Contact Name"
                    )
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                placeholder = { Text("e.g. +1 555 123 4567") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone Number"
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Storage Notice Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saved to device/account storage. SIM card storage is not supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSave(name.trim(), phoneNumber.trim().ifEmpty { null }) },
                enabled = name.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(saveButtonText)
            }
        }
    }
}
