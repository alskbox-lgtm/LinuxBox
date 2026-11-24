package com.linuxbox.android.core

enum class ProgramCategory(val label: String) {
    ALL("Todos"),
    DEV("Desenvolvimento"),
    EDITORS("Editores & Shell"),
    NETWORK("Rede & Servidores"),
    MEDIA("Mídia & Sistema"),
    DESKTOP("Gráfico (GUI)")
}

data class LinuxProgram(
    val id: String,
    val name: String,
    val shortDesc: String,
    val category: ProgramCategory,
    val packagesByManager: Map<PackageManagerType, List<String>>,
    val primaryBinary: String,
    val launchCommand: String
) {
    fun packagesFor(pm: PackageManagerType): List<String> =
        packagesByManager[pm] ?: packagesByManager[PackageManagerType.APK].orEmpty()
}

data class RepoSearchResult(
    val packageName: String,
    val description: String,
    val isInstalled: Boolean
)

object PackageCatalog {

    val programs: List<LinuxProgram> = listOf(
        // Desenvolvimento
        LinuxProgram(
            id = "python3",
            name = "Python 3 + pip",
            shortDesc = "Linguagem Python completa com gerenciador pip para scripts, IA, automação e servidores.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("python3", "py3-pip"),
                PackageManagerType.APT to listOf("python3", "python3-pip", "python3-venv"),
                PackageManagerType.PACMAN to listOf("python", "python-pip"),
                PackageManagerType.DNF to listOf("python3", "python3-pip")
            ),
            primaryBinary = "python3",
            launchCommand = "python3"
        ),
        LinuxProgram(
            id = "nodejs",
            name = "Node.js + npm",
            shortDesc = "Runtime JavaScript/TypeScript com npm para criar servidores, bots e ferramentas CLI.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("nodejs", "npm"),
                PackageManagerType.APT to listOf("nodejs", "npm"),
                PackageManagerType.PACMAN to listOf("nodejs", "npm"),
                PackageManagerType.DNF to listOf("nodejs", "npm")
            ),
            primaryBinary = "node",
            launchCommand = "node"
        ),
        LinuxProgram(
            id = "git",
            name = "Git",
            shortDesc = "Controle de versão distribuído para clonar, editar e publicar repositórios GitHub.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("git"),
                PackageManagerType.APT to listOf("git"),
                PackageManagerType.PACMAN to listOf("git"),
                PackageManagerType.DNF to listOf("git")
            ),
            primaryBinary = "git",
            launchCommand = "git --version"
        ),
        LinuxProgram(
            id = "gcc",
            name = "Compilador C/C++ (GCC)",
            shortDesc = "Toolchain completa com gcc, g++ e make para compilar programas nativos no celular.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("build-base"),
                PackageManagerType.APT to listOf("build-essential"),
                PackageManagerType.PACMAN to listOf("base-devel"),
                PackageManagerType.DNF to listOf("gcc", "gcc-c++", "make")
            ),
            primaryBinary = "gcc",
            launchCommand = "gcc --version"
        ),
        LinuxProgram(
            id = "golang",
            name = "Go (Golang)",
            shortDesc = "Compilador oficial da linguagem Go para CLIs e serviços de alta performance.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("go"),
                PackageManagerType.APT to listOf("golang-go"),
                PackageManagerType.PACMAN to listOf("go"),
                PackageManagerType.DNF to listOf("golang")
            ),
            primaryBinary = "go",
            launchCommand = "go version"
        ),
        LinuxProgram(
            id = "rust",
            name = "Rust + Cargo",
            shortDesc = "Compilador rustc e gerenciador Cargo para desenvolvimento seguro de sistemas.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("rust", "cargo"),
                PackageManagerType.APT to listOf("rustc", "cargo"),
                PackageManagerType.PACMAN to listOf("rust"),
                PackageManagerType.DNF to listOf("rust", "cargo")
            ),
            primaryBinary = "rustc",
            launchCommand = "rustc --version && cargo --version"
        ),
        LinuxProgram(
            id = "php",
            name = "PHP CLI",
            shortDesc = "Interpretador PHP com servidor embutido para desenvolvimento web local.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("php83", "php83-cli"),
                PackageManagerType.APT to listOf("php-cli"),
                PackageManagerType.PACMAN to listOf("php"),
                PackageManagerType.DNF to listOf("php-cli")
            ),
            primaryBinary = "php",
            launchCommand = "php -v"
        ),
        LinuxProgram(
            id = "sqlite",
            name = "SQLite 3",
            shortDesc = "Banco de dados SQL embutido e cliente de linha de comando leve.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("sqlite"),
                PackageManagerType.APT to listOf("sqlite3"),
                PackageManagerType.PACMAN to listOf("sqlite"),
                PackageManagerType.DNF to listOf("sqlite")
            ),
            primaryBinary = "sqlite3",
            launchCommand = "sqlite3"
        ),
        LinuxProgram(
            id = "lua",
            name = "Lua 5.4",
            shortDesc = "Linguagem de script ultra-rápida e leve.",
            category = ProgramCategory.DEV,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("lua5.4"),
                PackageManagerType.APT to listOf("lua5.4"),
                PackageManagerType.PACMAN to listOf("lua"),
                PackageManagerType.DNF to listOf("lua")
            ),
            primaryBinary = "lua5.4",
            launchCommand = "lua5.4"
        ),

        // Editores & Shell
        LinuxProgram(
            id = "nano",
            name = "GNU Nano",
            shortDesc = "Editor de texto simples e amigável no terminal com atalhos visíveis na tela.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("nano"),
                PackageManagerType.APT to listOf("nano"),
                PackageManagerType.PACMAN to listOf("nano"),
                PackageManagerType.DNF to listOf("nano")
            ),
            primaryBinary = "nano",
            launchCommand = "nano"
        ),
        LinuxProgram(
            id = "neovim",
            name = "Neovim",
            shortDesc = "Editor moderno para programadores com suporte a LSP, Lua e plugins.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("neovim"),
                PackageManagerType.APT to listOf("neovim"),
                PackageManagerType.PACMAN to listOf("neovim"),
                PackageManagerType.DNF to listOf("neovim")
            ),
            primaryBinary = "nvim",
            launchCommand = "nvim"
        ),
        LinuxProgram(
            id = "vim",
            name = "Vim",
            shortDesc = "Clássico editor modal rápido e poderoso para edição de código e arquivos.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("vim"),
                PackageManagerType.APT to listOf("vim"),
                PackageManagerType.PACMAN to listOf("vim"),
                PackageManagerType.DNF to listOf("vim-enhanced")
            ),
            primaryBinary = "vim",
            launchCommand = "vim"
        ),
        LinuxProgram(
            id = "tmux",
            name = "tmux",
            shortDesc = "Multiplexador de terminal: divida telas e mantenha sessões rodando em segundo plano.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("tmux"),
                PackageManagerType.APT to listOf("tmux"),
                PackageManagerType.PACMAN to listOf("tmux"),
                PackageManagerType.DNF to listOf("tmux")
            ),
            primaryBinary = "tmux",
            launchCommand = "tmux"
        ),
        LinuxProgram(
            id = "bash",
            name = "GNU Bash",
            shortDesc = "Shell padrão do Linux com histórico avançado, autocompletar e compatibilidade total com scripts.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("bash"),
                PackageManagerType.APT to listOf("bash"),
                PackageManagerType.PACMAN to listOf("bash"),
                PackageManagerType.DNF to listOf("bash")
            ),
            primaryBinary = "bash",
            launchCommand = "bash"
        ),
        LinuxProgram(
            id = "mc",
            name = "Midnight Commander",
            shortDesc = "Gerenciador de arquivos visual em dois painéis diretamente no terminal.",
            category = ProgramCategory.EDITORS,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("mc"),
                PackageManagerType.APT to listOf("mc"),
                PackageManagerType.PACMAN to listOf("mc"),
                PackageManagerType.DNF to listOf("mc")
            ),
            primaryBinary = "mc",
            launchCommand = "mc"
        ),

        // Rede & Servidores
        LinuxProgram(
            id = "curl",
            name = "cURL + Wget",
            shortDesc = "Ferramentas essenciais para baixar arquivos, testar APIs REST e scripts web.",
            category = ProgramCategory.NETWORK,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("curl", "wget"),
                PackageManagerType.APT to listOf("curl", "wget"),
                PackageManagerType.PACMAN to listOf("curl", "wget"),
                PackageManagerType.DNF to listOf("curl", "wget")
            ),
            primaryBinary = "curl",
            launchCommand = "curl --version"
        ),
        LinuxProgram(
            id = "openssh",
            name = "OpenSSH (Cliente & Servidor)",
            shortDesc = "Conecte-se a servidores remotos por SSH ou transfira arquivos via SCP/SFTP.",
            category = ProgramCategory.NETWORK,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("openssh"),
                PackageManagerType.APT to listOf("openssh-client", "openssh-server"),
                PackageManagerType.PACMAN to listOf("openssh"),
                PackageManagerType.DNF to listOf("openssh-clients", "openssh-server")
            ),
            primaryBinary = "ssh",
            launchCommand = "ssh -V"
        ),
        LinuxProgram(
            id = "nginx",
            name = "Nginx Web Server",
            shortDesc = "Servidor web HTTP e proxy reverso leve rodando localmente no Android.",
            category = ProgramCategory.NETWORK,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("nginx"),
                PackageManagerType.APT to listOf("nginx"),
                PackageManagerType.PACMAN to listOf("nginx"),
                PackageManagerType.DNF to listOf("nginx")
            ),
            primaryBinary = "nginx",
            launchCommand = "nginx -v"
        ),
        LinuxProgram(
            id = "nmap",
            name = "Nmap",
            shortDesc = "Ferramenta de diagnóstico, descoberta de hosts e análise de portas na rede local.",
            category = ProgramCategory.NETWORK,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("nmap"),
                PackageManagerType.APT to listOf("nmap"),
                PackageManagerType.PACMAN to listOf("nmap"),
                PackageManagerType.DNF to listOf("nmap")
            ),
            primaryBinary = "nmap",
            launchCommand = "nmap --version"
        ),

        // Mídia & Sistema
        LinuxProgram(
            id = "ffmpeg",
            name = "FFmpeg",
            shortDesc = "Suite completa para converter, cortar e processar vídeos, áudios e streams.",
            category = ProgramCategory.MEDIA,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("ffmpeg"),
                PackageManagerType.APT to listOf("ffmpeg"),
                PackageManagerType.PACMAN to listOf("ffmpeg"),
                PackageManagerType.DNF to listOf("ffmpeg-free")
            ),
            primaryBinary = "ffmpeg",
            launchCommand = "ffmpeg -version"
        ),
        LinuxProgram(
            id = "htop",
            name = "htop",
            shortDesc = "Monitor interativo de processos, CPU e memória com visual colorido.",
            category = ProgramCategory.MEDIA,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("htop"),
                PackageManagerType.APT to listOf("htop"),
                PackageManagerType.PACMAN to listOf("htop"),
                PackageManagerType.DNF to listOf("htop")
            ),
            primaryBinary = "htop",
            launchCommand = "htop"
        ),
        LinuxProgram(
            id = "fastfetch",
            name = "Fastfetch",
            shortDesc = "Mostra informações detalhadas do sistema Linux, kernel e arquitetura no terminal.",
            category = ProgramCategory.MEDIA,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("fastfetch"),
                PackageManagerType.APT to listOf("fastfetch"),
                PackageManagerType.PACMAN to listOf("fastfetch"),
                PackageManagerType.DNF to listOf("fastfetch")
            ),
            primaryBinary = "fastfetch",
            launchCommand = "fastfetch"
        ),
        LinuxProgram(
            id = "jq",
            name = "jq + ripgrep",
            shortDesc = "Processamento ultrarrápido de arquivos JSON e busca em código-fonte.",
            category = ProgramCategory.MEDIA,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("jq", "ripgrep"),
                PackageManagerType.APT to listOf("jq", "ripgrep"),
                PackageManagerType.PACMAN to listOf("jq", "ripgrep"),
                PackageManagerType.DNF to listOf("jq", "ripgrep")
            ),
            primaryBinary = "jq",
            launchCommand = "jq --version && rg --version"
        ),
        LinuxProgram(
            id = "zip",
            name = "Zip / Unzip / 7zip",
            shortDesc = "Compactação e extração completa de arquivos .zip e .7z.",
            category = ProgramCategory.MEDIA,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("zip", "unzip", "7zip"),
                PackageManagerType.APT to listOf("zip", "unzip", "p7zip-full"),
                PackageManagerType.PACMAN to listOf("zip", "unzip", "7zip"),
                PackageManagerType.DNF to listOf("zip", "unzip", "p7zip")
            ),
            primaryBinary = "zip",
            launchCommand = "zip -v | head -n 4"
        ),

        // Ambiente Gráfico (GUI)
        LinuxProgram(
            id = "desktop_fluxbox",
            name = "Desktop Linux (Fluxbox + X11 + VNC)",
            shortDesc = "Interface gráfica leve completa para rodar janelas Linux direto na aba Desktop do app!",
            category = ProgramCategory.DESKTOP,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("xvfb", "x11vnc", "fluxbox", "xterm", "ttf-dejavu", "xsetroot"),
                PackageManagerType.APT to listOf("xvfb", "x11vnc", "fluxbox", "xterm", "fonts-dejavu-core", "x11-xserver-utils"),
                PackageManagerType.PACMAN to listOf("xorg-server-xvfb", "x11vnc", "fluxbox", "xterm", "ttf-dejavu"),
                PackageManagerType.DNF to listOf("xorg-x11-server-Xvfb", "x11vnc", "fluxbox", "xterm", "dejavu-sans-fonts")
            ),
            primaryBinary = "fluxbox",
            launchCommand = "echo 'Abra a aba Desktop no menu inferior e toque em Iniciar Desktop!'"
        ),
        LinuxProgram(
            id = "mousepad",
            name = "Mousepad (Editor Gráfico)",
            shortDesc = "Editor de texto gráfico com abas e destaque de sintaxe para usar no Desktop Linux.",
            category = ProgramCategory.DESKTOP,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("mousepad"),
                PackageManagerType.APT to listOf("mousepad"),
                PackageManagerType.PACMAN to listOf("mousepad"),
                PackageManagerType.DNF to listOf("mousepad")
            ),
            primaryBinary = "mousepad",
            launchCommand = "DISPLAY=127.0.0.1:10 mousepad &"
        ),
        LinuxProgram(
            id = "thunar",
            name = "Thunar (Arquivos Gráfico)",
            shortDesc = "Explorador de arquivos visual rápido para o ambiente Desktop Linux.",
            category = ProgramCategory.DESKTOP,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("thunar"),
                PackageManagerType.APT to listOf("thunar"),
                PackageManagerType.PACMAN to listOf("thunar"),
                PackageManagerType.DNF to listOf("thunar")
            ),
            primaryBinary = "thunar",
            launchCommand = "DISPLAY=127.0.0.1:10 thunar &"
        ),
        LinuxProgram(
            id = "geany",
            name = "Geany IDE (Gráfico)",
            shortDesc = "IDE gráfica leve para programar em Python, C, C++, HTML e Shell no Desktop Linux.",
            category = ProgramCategory.DESKTOP,
            packagesByManager = mapOf(
                PackageManagerType.APK to listOf("geany"),
                PackageManagerType.APT to listOf("geany"),
                PackageManagerType.PACMAN to listOf("geany"),
                PackageManagerType.DNF to listOf("geany")
            ),
            primaryBinary = "geany",
            launchCommand = "DISPLAY=127.0.0.1:10 geany &"
        )
    )

    fun buildInstallCommand(pm: PackageManagerType, packages: List<String>): String {
        val safe = packages.map { sanitizePkg(it) }.filter { it.isNotEmpty() }.joinToString(" ")
        return when (pm) {
            PackageManagerType.APK -> "apk update && apk add --no-cache $safe"
            PackageManagerType.APT -> "apt-get update && apt-get install -y $safe"
            PackageManagerType.PACMAN -> "pacman -Sy --noconfirm $safe"
            PackageManagerType.DNF -> "dnf install -y $safe"
        }
    }

    fun buildRemoveCommand(pm: PackageManagerType, packages: List<String>): String {
        val safe = packages.map { sanitizePkg(it) }.filter { it.isNotEmpty() }.joinToString(" ")
        return when (pm) {
            PackageManagerType.APK -> "apk del $safe"
            PackageManagerType.APT -> "apt-get remove -y $safe"
            PackageManagerType.PACMAN -> "pacman -Rs --noconfirm $safe"
            PackageManagerType.DNF -> "dnf remove -y $safe"
        }
    }

    fun buildUpdateAllCommand(pm: PackageManagerType): String = when (pm) {
        PackageManagerType.APK -> "apk update && apk upgrade"
        PackageManagerType.APT -> "apt-get update && apt-get upgrade -y"
        PackageManagerType.PACMAN -> "pacman -Syu --noconfirm"
        PackageManagerType.DNF -> "dnf upgrade -y"
    }

    fun buildListInstalledCommand(pm: PackageManagerType): String = when (pm) {
        PackageManagerType.APK -> "apk info 2>/dev/null"
        PackageManagerType.APT -> "dpkg-query -W -f='\${Package}\\n' 2>/dev/null"
        PackageManagerType.PACMAN -> "pacman -Qq 2>/dev/null"
        PackageManagerType.DNF -> "rpm -qa --qf '%{NAME}\\n' 2>/dev/null"
    }

    fun buildSearchCommand(pm: PackageManagerType, rawQuery: String): String {
        val q = sanitizePkg(rawQuery)
        if (q.isEmpty()) return "true"
        return when (pm) {
            PackageManagerType.APK ->
                "apk update >/dev/null 2>&1; apk search -v '$q' 2>/dev/null | head -n 45"
            PackageManagerType.APT ->
                "apt-cache search '$q' 2>/dev/null | head -n 45"
            PackageManagerType.PACMAN ->
                "pacman -Ss '$q' 2>/dev/null | head -n 60"
            PackageManagerType.DNF ->
                "dnf search -q '$q' 2>/dev/null | head -n 45"
        }
    }

    fun parseSearchOutput(
        pm: PackageManagerType,
        output: String,
        installedSet: Set<String>
    ): List<RepoSearchResult> {
        val results = mutableListOf<RepoSearchResult>()
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }

        when (pm) {
            PackageManagerType.APK -> {
                for (line in lines) {
                    val sep = line.indexOf(" - ")
                    if (sep > 0) {
                        val rawNameVer = line.substring(0, sep).trim()
                        val desc = line.substring(sep + 3).trim()
                        // Strip "-version-rN" suffix from Alpine apk search -v output
                        val pkgName = stripAlpineVersion(rawNameVer)
                        results.add(RepoSearchResult(pkgName, desc, pkgName in installedSet))
                    }
                }
            }

            PackageManagerType.APT -> {
                for (line in lines) {
                    val sep = line.indexOf(" - ")
                    if (sep > 0) {
                        val pkgName = line.substring(0, sep).trim()
                        val desc = line.substring(sep + 3).trim()
                        results.add(RepoSearchResult(pkgName, desc, pkgName in installedSet))
                    }
                }
            }

            PackageManagerType.PACMAN -> {
                var i = 0
                while (i < lines.size) {
                    val header = lines[i]
                    val slash = header.indexOf('/')
                    if (slash > 0) {
                        val pkgName = header.substring(slash + 1).substringBefore(' ').trim()
                        val desc = lines.getOrNull(i + 1) ?: ""
                        results.add(RepoSearchResult(pkgName, desc, pkgName in installedSet))
                        i += 2
                    } else {
                        i++
                    }
                }
            }

            PackageManagerType.DNF -> {
                for (line in lines) {
                    val sep = line.indexOf(" : ")
                    if (sep > 0) {
                        val pkgName = line.substring(0, sep).substringBefore('.').trim()
                        val desc = line.substring(sep + 3).trim()
                        results.add(RepoSearchResult(pkgName, desc, pkgName in installedSet))
                    }
                }
            }
        }
        return results.distinctBy { it.packageName }.take(40)
    }

    private fun stripAlpineVersion(nameWithVersion: String): String {
        val regex = Regex("-[0-9][^-]*-r[0-9]+$")
        return nameWithVersion.replace(regex, "")
    }

    private fun sanitizePkg(name: String): String =
        name.trim().replace(Regex("[^a-zA-Z0-9.+_:-]"), "")
}
