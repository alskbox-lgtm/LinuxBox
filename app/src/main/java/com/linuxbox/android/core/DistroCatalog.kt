package com.linuxbox.android.core

import android.os.Build

enum class PackageManagerType(
    val displayName: String,
    val binaryName: String
) {
    APK("apk (Alpine)", "apk"),
    APT("apt (Debian/Ubuntu)", "apt-get"),
    PACMAN("pacman (Arch)", "pacman"),
    DNF("dnf (Fedora)", "dnf")
}

data class DistroDefinition(
    val id: String,
    val name: String,
    val version: String,
    val tagline: String,
    val packageManager: PackageManagerType,
    val accentHex: Long,
    val isBundled: Boolean,
    val downloadSizeLabel: String,
    val compression: ArchiveCompression,
    val stripComponents: Int,
    val defaultShell: String,
    val bundledAssetByAbi: Map<String, String> = emptyMap(),
    val downloadUrlByAbi: Map<String, String> = emptyMap()
) {
    fun supportsAbi(abi: String): Boolean =
        bundledAssetByAbi.containsKey(abi) || downloadUrlByAbi.containsKey(abi)
}

object DistroCatalog {

    val alpine = DistroDefinition(
        id = "alpine",
        name = "Alpine Linux",
        version = "3.21.10",
        tagline = "Ultra-leve e embutido no app! Funciona offline na hora com apk (Python, Node, Git, GCC, XFCE/Fluxbox).",
        packageManager = PackageManagerType.APK,
        accentHex = 0xFF0EA5E9,
        isBundled = true,
        downloadSizeLabel = "Embutido (3,7 MB)",
        compression = ArchiveCompression.GZIP,
        stripComponents = 0,
        defaultShell = "/bin/ash",
        bundledAssetByAbi = mapOf(
            "arm64-v8a" to "rootfs/alpine-arm64-v8a.tgz",
            "armeabi-v7a" to "rootfs/alpine-armeabi-v7a.tgz",
            "x86_64" to "rootfs/alpine-x86_64.tgz"
        ),
        downloadUrlByAbi = mapOf(
            "arm64-v8a" to "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.10-aarch64.tar.gz",
            "armeabi-v7a" to "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/alpine-minirootfs-3.21.10-armv7.tar.gz",
            "x86_64" to "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.10-x86_64.tar.gz"
        )
    )

    val ubuntu = DistroDefinition(
        id = "ubuntu",
        name = "Ubuntu LTS",
        version = "24.04.5 Noble",
        tagline = "O Linux mais popular do mundo com apt e enorme catálogo de programas CLI e gráficos.",
        packageManager = PackageManagerType.APT,
        accentHex = 0xFFE95420,
        isBundled = false,
        downloadSizeLabel = "~29 MB",
        compression = ArchiveCompression.GZIP,
        stripComponents = 0,
        defaultShell = "/bin/bash",
        downloadUrlByAbi = mapOf(
            "arm64-v8a" to "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            "armeabi-v7a" to "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-armhf.tar.gz",
            "x86_64" to "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz"
        )
    )

    val debian = DistroDefinition(
        id = "debian",
        name = "Debian",
        version = "12 Bookworm",
        tagline = "Estabilidade máxima com gerenciador apt, excelente para servidores, desenvolvimento e ferramentas CLI.",
        packageManager = PackageManagerType.APT,
        accentHex = 0xFFD70A53,
        isBundled = false,
        downloadSizeLabel = "~45 MB",
        compression = ArchiveCompression.XZ,
        stripComponents = 1,
        defaultShell = "/bin/bash",
        downloadUrlByAbi = mapOf(
            "arm64-v8a" to "https://github.com/termux/proot-distro/releases/download/v4.6.0/debian-aarch64-pd-v4.6.0.tar.xz",
            "x86_64" to "https://github.com/termux/proot-distro/releases/download/v4.6.0/debian-x86_64-pd-v4.6.0.tar.xz"
        )
    )

    val arch = DistroDefinition(
        id = "arch",
        name = "Arch Linux",
        version = "Rolling",
        tagline = "Rolling release com pacman e pacotes sempre atualizados para usuários avançados.",
        packageManager = PackageManagerType.PACMAN,
        accentHex = 0xFF1793D1,
        isBundled = false,
        downloadSizeLabel = "~140 MB",
        compression = ArchiveCompression.XZ,
        stripComponents = 1,
        defaultShell = "/bin/bash",
        downloadUrlByAbi = mapOf(
            "arm64-v8a" to "https://github.com/termux/proot-distro/releases/download/v4.34.2/archlinux-aarch64-pd-v4.34.2.tar.xz",
            "armeabi-v7a" to "https://github.com/termux/proot-distro/releases/download/v4.34.2/archlinux-arm-pd-v4.34.2.tar.xz",
            "x86_64" to "https://github.com/termux/proot-distro/releases/download/v4.34.2/archlinux-x86_64-pd-v4.34.2.tar.xz"
        )
    )

    val fedora = DistroDefinition(
        id = "fedora",
        name = "Fedora Linux",
        version = "Workstation Base",
        tagline = "Tecnologias Linux modernas com gerenciador de pacotes dnf.",
        packageManager = PackageManagerType.DNF,
        accentHex = 0xFF3C6EB4,
        isBundled = false,
        downloadSizeLabel = "~60 MB",
        compression = ArchiveCompression.XZ,
        stripComponents = 1,
        defaultShell = "/bin/bash",
        downloadUrlByAbi = mapOf(
            "arm64-v8a" to "https://github.com/termux/proot-distro/releases/download/v4.31.0/fedora-aarch64-pd-v4.31.0.tar.xz",
            "x86_64" to "https://github.com/termux/proot-distro/releases/download/v4.31.0/fedora-x86_64-pd-v4.31.0.tar.xz"
        )
    )

    val all: List<DistroDefinition> = listOf(alpine, ubuntu, debian, arch, fedora)

    fun byId(id: String): DistroDefinition = all.firstOrNull { it.id == id } ?: alpine

    fun detectPreferredAbi(): String {
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        for (abi in Build.SUPPORTED_ABIS) {
            if (abi in supported) return abi
        }
        return "arm64-v8a"
    }
}
