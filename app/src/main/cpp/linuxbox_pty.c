#define _GNU_SOURCE
#include <jni.h>
#include <android/log.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "LinuxBoxPTY"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static char **jstring_array_to_c(JNIEnv *env, jobjectArray arr) {
    if (arr == NULL) return NULL;
    jsize len = (*env)->GetArrayLength(env, arr);
    char **res = (char **)calloc((size_t)len + 1, sizeof(char *));
    if (!res) return NULL;

    for (jsize i = 0; i < len; i++) {
        jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        if (jstr != NULL) {
            const char *utf = (*env)->GetStringUTFChars(env, jstr, NULL);
            if (utf != NULL) {
                res[i] = strdup(utf);
                (*env)->ReleaseStringUTFChars(env, jstr, utf);
            }
            (*env)->DeleteLocalRef(env, jstr);
        }
        if (res[i] == NULL) {
            res[i] = strdup("");
        }
    }
    res[len] = NULL;
    return res;
}

static void free_c_string_array(char **arr) {
    if (!arr) return;
    for (size_t i = 0; arr[i] != NULL; i++) {
        free(arr[i]);
    }
    free(arr);
}

static void close_inherited_fds(int keep_upto) {
    DIR *dir = opendir("/proc/self/fd");
    if (dir != NULL) {
        int dir_fd = dirfd(dir);
        struct dirent *ent;
        while ((ent = readdir(dir)) != NULL) {
            if (ent->d_name[0] == '.') continue;
            int fd = atoi(ent->d_name);
            if (fd > keep_upto && fd != dir_fd) {
                close(fd);
            }
        }
        closedir(dir);
        return;
    }

    long max_fd = sysconf(_SC_OPEN_MAX);
    if (max_fd < 0 || max_fd > 4096) max_fd = 1024;
    for (int fd = keep_upto + 1; fd < (int)max_fd; fd++) {
        close(fd);
    }
}

/*
 * Spawns a subprocess attached to a real Linux pseudo-terminal (PTY).
 * Returns int[2] = { masterFd, childPid }, or NULL on error.
 */
JNIEXPORT jintArray JNICALL
Java_com_linuxbox_android_core_NativePty_createSubprocess(
    JNIEnv *env,
    jobject thiz,
    jstring jcmd,
    jstring jcwd,
    jobjectArray jargs,
    jobjectArray jenv,
    jint rows,
    jint cols
) {
    (void)thiz;

    const char *cmd_utf = (*env)->GetStringUTFChars(env, jcmd, NULL);
    if (!cmd_utf) return NULL;
    char *cmd = strdup(cmd_utf);
    (*env)->ReleaseStringUTFChars(env, jcmd, cmd_utf);

    char *cwd = NULL;
    if (jcwd != NULL) {
        const char *cwd_utf = (*env)->GetStringUTFChars(env, jcwd, NULL);
        if (cwd_utf) {
            cwd = strdup(cwd_utf);
            (*env)->ReleaseStringUTFChars(env, jcwd, cwd_utf);
        }
    }

    char **argv = jstring_array_to_c(env, jargs);
    char **envp = jstring_array_to_c(env, jenv);

    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        LOGE("open(/dev/ptmx) failed: %s", strerror(errno));
        free(cmd);
        free(cwd);
        free_c_string_array(argv);
        free_c_string_array(envp);
        return NULL;
    }

    char devname[128];
    if (grantpt(ptm) != 0 || unlockpt(ptm) != 0 || ptsname_r(ptm, devname, sizeof(devname)) != 0) {
        LOGE("PTY setup failed: %s", strerror(errno));
        close(ptm);
        free(cmd);
        free(cwd);
        free_c_string_array(argv);
        free_c_string_array(envp);
        return NULL;
    }

    // Enable UTF-8 input mode on the PTY
    struct termios tios;
    if (tcgetattr(ptm, &tios) == 0) {
        tios.c_iflag |= IUTF8;
        tios.c_iflag &= ~(IXON | IXOFF);
        tcsetattr(ptm, TCSANOW, &tios);
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    ioctl(ptm, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        close(ptm);
        free(cmd);
        free(cwd);
        free_c_string_array(argv);
        free_c_string_array(envp);
        return NULL;
    }

    if (pid == 0) {
        // Child process: never invoke JNI here.
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, NULL);

        signal(SIGHUP, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);
        signal(SIGALRM, SIG_DFL);
        signal(SIGTERM, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);
        signal(SIGTTIN, SIG_DFL);
        signal(SIGTTOU, SIG_DFL);

        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) {
            _exit(126);
        }

        ioctl(pts, TIOCSCTTY, 0);
        ioctl(pts, TIOCSWINSZ, &ws);

        dup2(pts, STDIN_FILENO);
        dup2(pts, STDOUT_FILENO);
        dup2(pts, STDERR_FILENO);
        if (pts > STDERR_FILENO) {
            close(pts);
        }

        close_inherited_fds(STDERR_FILENO);

        if (cwd != NULL && cwd[0] != '\0') {
            (void)chdir(cwd);
        }

        execve(cmd, argv, envp);

        const char *err = strerror(errno);
        (void)write(STDERR_FILENO, "LinuxBox execve error (", 23);
        (void)write(STDERR_FILENO, cmd, strlen(cmd));
        (void)write(STDERR_FILENO, "): ", 3);
        (void)write(STDERR_FILENO, err, strlen(err));
        (void)write(STDERR_FILENO, "\r\n", 2);
        _exit(127);
    }

    // Parent process
    free(cmd);
    free(cwd);
    free_c_string_array(argv);
    free_c_string_array(envp);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(ptm);
        kill(pid, SIGKILL);
        return NULL;
    }
    jint pair[2] = { (jint)ptm, (jint)pid };
    (*env)->SetIntArrayRegion(env, result, 0, 2, pair);
    LOGI("Started PTY session pid=%d fd=%d (%dx%d)", (int)pid, ptm, (int)cols, (int)rows);
    return result;
}

JNIEXPORT void JNICALL
Java_com_linuxbox_android_core_NativePty_setPtyWindowSize(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jint rows,
    jint cols
) {
    (void)env;
    (void)thiz;
    if (fd < 0 || rows <= 0 || cols <= 0) return;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_linuxbox_android_core_NativePty_waitFor(
    JNIEnv *env,
    jobject thiz,
    jint pid
) {
    (void)env;
    (void)thiz;
    if (pid <= 0) return -1;
    int status = 0;
    while (waitpid((pid_t)pid, &status, 0) < 0) {
        if (errno == EINTR) continue;
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_linuxbox_android_core_NativePty_sendSignal(
    JNIEnv *env,
    jobject thiz,
    jint pid,
    jint sig
) {
    (void)env;
    (void)thiz;
    if (pid <= 0) return;
    kill(-(pid_t)pid, sig);
    kill((pid_t)pid, sig);
}

JNIEXPORT void JNICALL
Java_com_linuxbox_android_core_NativePty_closeFd(
    JNIEnv *env,
    jobject thiz,
    jint fd
) {
    (void)env;
    (void)thiz;
    if (fd >= 0) {
        close(fd);
    }
}
