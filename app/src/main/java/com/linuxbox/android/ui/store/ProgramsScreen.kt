package com.linuxbox.android.ui.store

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxbox.android.core.LinuxProgram
import com.linuxbox.android.core.PackageCatalog
import com.linuxbox.android.core.PackageManagerType
import com.linuxbox.android.core.ProgramCategory
import com.linuxbox.android.core.RepoSearchResult
import com.linuxbox.android.ui.LinuxBoxViewModel
import com.linuxbox.android.ui.theme.LinuxBoxColors

@Composable
fun ProgramsScreen(
    viewModel: LinuxBoxViewModel,
    modifier: Modifier = Modifier
) {
    val activeDistro by viewModel.activeDistro.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val installedPackages by viewModel.installedPackages.collectAsState()
    val isRefreshing by viewModel.isRefreshingPackages.collectAsState()
    val repoResults by viewModel.repoSearchResults.collectAsState()
    val isSearchingRepo by viewModel.isSearchingRepo.collectAsState()

    val pickInstaller = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.handleInstallerUri(uri)
        }
    }

    val filteredPrograms = PackageCatalog.programs.filter { prog ->
        val matchesCategory = selectedCategory == ProgramCategory.ALL || prog.category == selectedCategory
        val q = searchQuery.trim()
        val matchesQuery = q.isEmpty() ||
            prog.name.contains(q, ignoreCase = true) ||
            prog.shortDesc.contains(q, ignoreCase = true) ||
            prog.packagesFor(activeDistro.packageManager).any { it.contains(q, ignoreCase = true) }
        matchesCategory && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background)
    ) {
        // Header banner
        Surface(
            color = LinuxBoxColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Loja de Programas Linux",
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeDistro.name} ${activeDistro.version} • ${activeDistro.packageManager.displayName} • ${installedPackages.size} pacotes",
                            color = LinuxBoxColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { viewModel.refreshInstalledPackages() }) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LinuxBoxColors.Primary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "Recarregar", tint = LinuxBoxColors.TextSecondary)
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateAllPackages() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, LinuxBoxColors.Primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SystemUpdateAlt,
                                null,
                                tint = LinuxBoxColors.Primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Atualizar", color = LinuxBoxColors.Primary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { pickInstaller.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Secondary.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        null,
                        tint = LinuxBoxColors.Secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Abrir instalador local (.deb, .rpm, .sh, .AppImage)",
                        color = LinuxBoxColors.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Search bar + live repo search action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                "Buscar python, ffmpeg, vim, git...",
                                fontSize = 13.sp,
                                color = LinuxBoxColors.TextSecondary
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = LinuxBoxColors.TextSecondary)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.searchRepositoryPackages()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LinuxBoxColors.Primary,
                            unfocusedBorderColor = LinuxBoxColors.Border,
                            focusedTextColor = LinuxBoxColors.TextPrimary,
                            unfocusedTextColor = LinuxBoxColors.TextPrimary,
                            focusedContainerColor = LinuxBoxColors.Background,
                            unfocusedContainerColor = LinuxBoxColors.Background
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.trim().length >= 2) {
                        Button(
                            onClick = { viewModel.searchRepositoryPackages() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Secondary)
                        ) {
                            if (isSearchingRepo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF041520)
                                )
                            } else {
                                Text("Repo", color = Color(0xFF041520), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProgramCategory.entries.forEach { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setCategory(cat) },
                            label = {
                                Text(cat.label, fontSize = 12.sp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LinuxBoxColors.PrimaryDim,
                                selectedLabelColor = LinuxBoxColors.Primary,
                                containerColor = LinuxBoxColors.SurfaceElevated,
                                labelColor = LinuxBoxColors.TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Scrollable catalog & live repo results
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (repoResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Resultados do repositório ${activeDistro.packageManager.displayName} (${repoResults.size})",
                        color = LinuxBoxColors.Secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                items(repoResults, key = { "repo-${it.packageName}" }) { item ->
                    RepoPackageCard(
                        item = item,
                        onInstall = { viewModel.installPackageByName(item.packageName) },
                        onRemove = { viewModel.removePackageByName(item.packageName) }
                    )
                }
            }

            if (filteredPrograms.isEmpty() && repoResults.isEmpty()) {
                item {
                    Surface(
                        color = LinuxBoxColors.Surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LinuxBoxColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Nenhum programa na lista rápida para \"$searchQuery\".",
                                color = LinuxBoxColors.TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.searchRepositoryPackages() },
                                colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Secondary)
                            ) {
                                Text(
                                    "Buscar \"$searchQuery\" no repositório Linux",
                                    color = Color(0xFF041520),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.installPackageByName(searchQuery.trim()) }
                            ) {
                                Text("Instalar pacote direto: ${searchQuery.trim()}")
                            }
                        }
                    }
                }
            }

            items(filteredPrograms, key = { it.id }) { prog ->
                val pkgs = prog.packagesFor(activeDistro.packageManager)
                val isInstalled = pkgs.isNotEmpty() && pkgs.all { it in installedPackages }
                ProgramCard(
                    program = prog,
                    pm = activeDistro.packageManager,
                    isInstalled = isInstalled,
                    onInstall = { viewModel.installProgram(prog) },
                    onRemove = { viewModel.removeProgram(prog) },
                    onLaunch = { viewModel.runCommandInTerminal(prog.launchCommand) }
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: LinuxProgram,
    pm: PackageManagerType,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onLaunch: () -> Unit
) {
    val pkgs = program.packagesFor(pm)
    Surface(
        color = LinuxBoxColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isInstalled) LinuxBoxColors.Primary.copy(alpha = 0.55f) else LinuxBoxColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = program.name,
                    color = LinuxBoxColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isInstalled) {
                    Surface(
                        color = LinuxBoxColors.PrimaryDim.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = LinuxBoxColors.Primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Instalado",
                                color = LinuxBoxColors.Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = program.shortDesc,
                color = LinuxBoxColors.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pkgs.joinToString(" "),
                    color = LinuxBoxColors.Secondary.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isInstalled) {
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, LinuxBoxColors.Danger.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                null,
                                tint = LinuxBoxColors.Danger,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Button(
                            onClick = onLaunch,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                null,
                                tint = Color(0xFF041309),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Abrir", color = Color(0xFF041309), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                null,
                                tint = Color(0xFF041309),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Instalar", color = Color(0xFF041309), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoPackageCard(
    item: RepoSearchResult,
    onInstall: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = LinuxBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LinuxBoxColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.packageName,
                    color = LinuxBoxColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        color = LinuxBoxColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (item.isInstalled) {
                OutlinedButton(
                    onClick = onRemove,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Danger)
                ) {
                    Text("Remover", color = LinuxBoxColors.Danger, fontSize = 11.5.sp)
                }
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Secondary)
                ) {
                    Text("Instalar", color = Color(0xFF041520), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            }
        }
    }
}
