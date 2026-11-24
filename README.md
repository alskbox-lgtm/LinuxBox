# LinuxBox — Sistema Linux Autônomo no Android

O **LinuxBox** é um aplicativo Android nativo (Kotlin + Jetpack Compose + C/JNI + PRoot) que executa distribuições Linux reais diretamente no celular, **sem precisar de computador, sem Termux externo e sem acesso Root**.

## O que vem pronto no APK

- **Alpine Linux 3.21.10 embutido offline** (`arm64-v8a`, `armeabi-v7a` e `x86_64`): no primeiro acesso o app extrai o sistema automaticamente em segundos, sem depender de PC.
- **Motor PRoot nativo compilado com Android NDK r27** (`libproot.so`, `libproot-loader.so`, `libproot-loader32.so`) com `talloc` linkado estaticamente (zero dependências `.so` externas) e emulação de fake-root (`-0`), `--link2symlink` e `/proc` compatível com SELinux do Android.
- **Terminal Linux completo com PTY real (JNI C11 `liblinuxbox_pty.so`)**:
  - Emulador VT100 / ANSI / xterm-256color / 24-bit TrueColor com tela alternativa (`vim`, `nano`, `htop`, `tmux`, `less`, `python3`, `apk`, `apt`).
  - Múltiplas abas simultâneas, rolagem de histórico (scrollback), zoom de fonte, copiar/colar e barra de teclas extras (`ESC`, `TAB`, `CTRL`, `ALT`, setas, `/`, `-`, `|`, `~`, `^C`, `HOME`, `END`, `COLAR`).
  - Serviço em primeiro plano (`LinuxSessionService`) para manter servidores e instalações rodando em segundo plano.
- **Loja de Programas Linux (Gerenciador de Pacotes Visual)**:
  - Instalação com 1 toque de programas reais: Python 3 + pip, Node.js + npm, Git, GCC/C++, Go, Rust, PHP, SQLite, Nano, Vim, Neovim, tmux, Bash, Midnight Commander, cURL/Wget, OpenSSH, Nginx, Nmap, FFmpeg, htop, Fastfetch, jq/ripgrep, Zip/7zip e aplicativos gráficos (Mousepad, Thunar, Geany).
  - Busca ao vivo diretamente nos repositórios oficiais (`apk search`, `apt-cache search`, `pacman -Ss`, `dnf search`) para instalar qualquer um dos milhares de pacotes Linux disponíveis.
  - Console de instalação em tempo real e botão **Abrir no Terminal**.
- **Desktop Linux Gráfico integrado (X11 + Fluxbox + VNC nativo)**:
  - Instala `Xvfb + Fluxbox + x11vnc + xterm` com 1 toque e exibe a área de trabalho Linux dentro do próprio app usando cliente RFB/VNC 3.8 escrito em Kotlin puro (sem precisar de app VNC externo).
- **Gerenciador de Múltiplas Distros**:
  - **Alpine Linux 3.21** (embutido offline, `apk`)
  - **Ubuntu 24.04.5 LTS Noble** (download direto no celular, `apt`)
  - **Debian 12 Bookworm** (download direto no celular, `apt`)
  - **Arch Linux** (download direto no celular, `pacman`)
  - **Fedora Linux** (download direto no celular, `dnf`)

## Estrutura do Projeto

- `app/src/main/cpp/linuxbox_pty.c` — Ponte JNI em C11 para pseudo-terminal Linux (`/dev/ptmx`, `setsid`, `TIOCSCTTY`, `TIOCSWINSZ`, `execve`, sinais).
- `app/src/main/jniLibs/<abi>/` — Binários nativos `libproot.so`, `libproot-loader.so`, `libproot-loader32.so` gerados por `scripts/build_proot.sh`.
- `app/src/main/assets/rootfs/` — Imagens oficiais Alpine Linux 3.21.10 (`alpine-<abi>.tgz`).
- `app/src/main/java/com/linuxbox/android/core/` — `NativePty`, `RootfsExtractor`, `FakeProcBuilder`, `ProotCommandBuilder`, `DistroCatalog`, `DistroManager`, `LinuxCommandRunner`, `PackageCatalog`.
- `app/src/main/java/com/linuxbox/android/terminal/` — `TerminalEmulator` (VT100/256-color/alt-screen), `TerminalSession`, `SessionManager`.
- `app/src/main/java/com/linuxbox/android/desktop/` — `RfbClient` (VNC/RFB 3.8 puro em Kotlin) e `DesktopController`.
- `app/src/main/java/com/linuxbox/android/ui/` — Telas Jetpack Compose Material 3 (`TerminalScreen`, `ProgramsScreen`, `DesktopScreen`, `SystemsScreen`, `LinuxBoxRoot`).

## Como compilar o APK

```bash
# (Opcional) Recompilar binários nativos do PRoot via NDK:
./scripts/build_proot.sh

# Rodar testes unitários e gerar o APK pronto para instalar no Android:
gradle testDebugUnitTest assembleDebug
```

O APK compilado fica em:
`app/build/outputs/apk/debug/app-debug.apk`

Transfira o `.apk` para o celular Android (ou instale via `adb install app/build/outputs/apk/debug/app-debug.apk`), habilite instalação de fontes desconhecidas e abra o **LinuxBox** — ele roda 100% sozinho no aparelho.
