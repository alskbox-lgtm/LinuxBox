#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/antigravity/LinuxBox"
NDK="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/27.3.13750724}"
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin"
PROOT_SRC="${PROJECT_DIR}/native-build/proot/src"
TALLOC_SRC="${PROJECT_DIR}/native-build/talloc"
JNILIBS_DIR="${PROJECT_DIR}/app/src/main/jniLibs"
API_LEVEL=26

build_abi() {
    local ABI="$1"
    local CC_MAIN="$2"
    local CC_32="${3:-}"
    local OUT_DIR="${JNILIBS_DIR}/${ABI}"
    local WORK_DIR="${PROJECT_DIR}/native-build/work-${ABI}"

    echo "========================================="
    echo "Building PRoot + talloc for ABI: ${ABI}"
    echo "========================================="

    rm -rf "${WORK_DIR}"
    mkdir -p "${WORK_DIR}" "${OUT_DIR}"

    # 1. Compile talloc.c into static libtalloc.a
    echo "[1/4] Compiling static libtalloc.a for ${ABI}..."
    "${CC_MAIN}" -O2 -fPIC \
        -I"${TALLOC_SRC}" \
        -c "${TALLOC_SRC}/talloc.c" \
        -o "${WORK_DIR}/talloc.o"
    "${TOOLCHAIN}/llvm-ar" rcs "${WORK_DIR}/libtalloc.a" "${WORK_DIR}/talloc.o"

    # 2. Prepare per-ABI copy of proot/src (GNUmakefile expects in-source build)
    cp -a "${PROOT_SRC}" "${WORK_DIR}/proot-src"
    sed -i '1i #include <string.h>' "${WORK_DIR}/proot-src/extension/ashmem_memfd/ashmem_memfd.c"

    # 3. Build 32-bit loader for 64-bit ABIs
    if [ -n "${CC_32}" ]; then
        echo "[2/4] Compiling 32-bit loader for ${ABI}..."
        local LOADER32_ADDR
        local LOADER32_EXTRA=""
        if [ "${ABI}" = "arm64-v8a" ]; then
            LOADER32_ADDR="0x20000000"
        else
            LOADER32_ADDR="0xa0000000"
            LOADER32_EXTRA="-mregparm=3"
        fi
        "${CC_32}" -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I"${WORK_DIR}/proot-src" \
            -O2 -fPIC -ffreestanding ${LOADER32_EXTRA} \
            -c "${WORK_DIR}/proot-src/loader/loader.c" -o "${WORK_DIR}/proot-src/loader/loader-m32.o"
        "${CC_32}" -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I"${WORK_DIR}/proot-src" \
            -O2 -fPIC -ffreestanding ${LOADER32_EXTRA} \
            -c "${WORK_DIR}/proot-src/loader/assembly.S" -o "${WORK_DIR}/proot-src/loader/assembly-m32.o"
        "${CC_32}" -o "${WORK_DIR}/proot-src/loader/loader-m32" \
            "${WORK_DIR}/proot-src/loader/loader-m32.o" \
            "${WORK_DIR}/proot-src/loader/assembly-m32.o" \
            -static -nostdlib -Wl,--build-id=none,-Ttext="${LOADER32_ADDR}",--rosegment,-z,noexecstack
        "${TOOLCHAIN}/llvm-strip" "${WORK_DIR}/proot-src/loader/loader-m32"
    else
        echo "[2/4] Skipping 32-bit sub-loader for 32-bit ABI ${ABI}..."
    fi

    # 4. Build main loader and proot via GNUmakefile
    echo "[3/4] Compiling PRoot and loader for ${ABI}..."
    local EXTRA_LDFLAGS=""
    if [ "${ABI}" != "armeabi-v7a" ]; then
        EXTRA_LDFLAGS="-Wl,-z,max-page-size=16384"
    fi
    (
        cd "${WORK_DIR}/proot-src"
        make -j"$(nproc)" \
            -f GNUmakefile \
            V=0 \
            CC="${CC_MAIN}" \
            STRIP="${TOOLCHAIN}/llvm-strip" \
            OBJCOPY="${TOOLCHAIN}/llvm-objcopy" \
            OBJDUMP="${TOOLCHAIN}/llvm-objdump" \
            PROOT_UNBUNDLE_LOADER="." \
            CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -DARG_MAX=131072 -DPROOT_UNBUNDLE_LOADER=\\\".\\\" -DVERSION=\\\"5.4.0-linuxbox\\\" -I. -I${TALLOC_SRC}" \
            CFLAGS="-Wall -Wextra -Wno-unused-parameter -Wno-deprecated-declarations -O2 -fPIE" \
            LDFLAGS="-L${WORK_DIR} -Wl,-Bstatic -ltalloc -Wl,-Bdynamic -pie -Wl,-z,noexecstack ${EXTRA_LDFLAGS}" \
            loader/loader proot
    )

    "${TOOLCHAIN}/llvm-strip" "${WORK_DIR}/proot-src/proot"
    "${TOOLCHAIN}/llvm-strip" "${WORK_DIR}/proot-src/loader/loader"

    # 5. Install as Android jniLibs
    echo "[4/4] Installing binaries into ${OUT_DIR}..."
    install -m 755 "${WORK_DIR}/proot-src/proot" "${OUT_DIR}/libproot.so"
    install -m 755 "${WORK_DIR}/proot-src/loader/loader" "${OUT_DIR}/libproot-loader.so"
    if [ -n "${CC_32}" ]; then
        install -m 755 "${WORK_DIR}/proot-src/loader/loader-m32" "${OUT_DIR}/libproot-loader32.so"
    fi

    echo "Built ${ABI}:"
    file "${OUT_DIR}"/*.so
    "${TOOLCHAIN}/llvm-readelf" -d "${OUT_DIR}/libproot.so" | grep NEEDED || true
}

build_abi "arm64-v8a" \
    "${TOOLCHAIN}/aarch64-linux-android${API_LEVEL}-clang" \
    "${TOOLCHAIN}/armv7a-linux-androideabi${API_LEVEL}-clang"

build_abi "armeabi-v7a" \
    "${TOOLCHAIN}/armv7a-linux-androideabi${API_LEVEL}-clang"

build_abi "x86_64" \
    "${TOOLCHAIN}/x86_64-linux-android${API_LEVEL}-clang" \
    "${TOOLCHAIN}/i686-linux-android${API_LEVEL}-clang"

echo "ALL PROOT BINARIES BUILT SUCCESSFULLY!"
