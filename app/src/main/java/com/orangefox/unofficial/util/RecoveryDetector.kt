package com.orangefox.unofficial.util

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

enum class CheckStatus { DETECTED, NOT_FOUND, UNKNOWN }

enum class Verdict(val title: String, val description: String) {
    CUSTOM_LIKELY(
        "Custom recovery likely",
        "At least one custom-recovery signature (TWRP/OrangeFox data, artifacts or image markers) was found on this device."
    ),
    STOCK_LIKELY(
        "Stock recovery likely",
        "The bootloader appears locked and no root or custom-recovery traces were found."
    ),
    UNCERTAIN(
        "Cannot confirm",
        "Some checks were inconclusive on this device. Boot into recovery mode manually to verify."
    )
}

data class CheckResult(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val detail: String
)

/**
 * Local, best-effort detection of unlocked bootloaders, root and custom
 * recoveries (TWRP / OrangeFox). Everything runs on-device; no data leaves
 * the phone. Reading the actual recovery image requires root, so without it
 * the deep scan reports UNKNOWN instead of guessing.
 */
object RecoveryDetector {

    fun checks(context: Context): List<() -> CheckResult> = listOf(
        { checkBootloader() },
        { checkRoot() },
        { checkProps() },
        { checkArtifacts() },
        { checkPartitionNode() },
        { deepScan(context) }
    )

    fun verdictOf(results: List<CheckResult>): Verdict {
        val positive = results.any {
            it.status == CheckStatus.DETECTED && it.id != "bootloader" && it.id != "root"
        }
        if (positive) return Verdict.CUSTOM_LIKELY
        val boot = results.firstOrNull { it.id == "bootloader" }
        val root = results.firstOrNull { it.id == "root" }
        return if (boot?.status == CheckStatus.NOT_FOUND && root?.status == CheckStatus.NOT_FOUND) {
            Verdict.STOCK_LIKELY
        } else {
            Verdict.UNCERTAIN
        }
    }

    // ---- individual checks --------------------------------------------------

    private fun cmdline(): String =
        runCatching { File("/proc/cmdline").readText() }.getOrDefault("")

    fun checkBootloader(): CheckResult {
        val state = Regex("androidboot.verifiedbootstate=(\\w+)").find(cmdline())?.groupValues?.get(1)
            ?: Regex("androidboot.vbmeta.device_state=(\\w+)").find(cmdline())?.groupValues?.get(1)
        return when (state?.lowercase()) {
            "orange", "unlocked" -> CheckResult(
                "bootloader", "Bootloader", CheckStatus.DETECTED,
                "Verified boot state is 'orange' — the bootloader is unlocked."
            )
            "green", "locked" -> CheckResult(
                "bootloader", "Bootloader", CheckStatus.NOT_FOUND,
                "Verified boot state is 'green' — the bootloader appears locked."
            )
            else -> CheckResult(
                "bootloader", "Bootloader", CheckStatus.UNKNOWN,
                "The verified boot state could not be read on this device."
            )
        }
    }

    fun checkRoot(): CheckResult {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/vendor/bin/su", "/odm/bin/su"
        )
        val found = suPaths.firstOrNull { runCatching { File(it).exists() }.getOrDefault(false) }
        val testKeys = Build.TAGS?.contains("test-keys") == true
        return when {
            found != null -> CheckResult(
                "root", "Root access", CheckStatus.DETECTED,
                "su binary detected at $found."
            )
            testKeys -> CheckResult(
                "root", "Root access", CheckStatus.DETECTED,
                "This build uses test-keys — a modified ROM is likely."
            )
            else -> CheckResult(
                "root", "Root access", CheckStatus.NOT_FOUND,
                "No su binary or modified-build markers were found."
            )
        }
    }

    fun checkProps(): CheckResult {
        val twrp = systemProperty("ro.twrp.version")
        val fox = systemProperty("ro.orangefox.version") ?: systemProperty("ro.fox.version")
        return when {
            !fox.isNullOrBlank() -> CheckResult(
                "props", "Recovery version property", CheckStatus.DETECTED,
                "OrangeFox property found: ro.orangefox.version=$fox"
            )
            !twrp.isNullOrBlank() -> CheckResult(
                "props", "Recovery version property", CheckStatus.DETECTED,
                "TWRP property found: ro.twrp.version=$twrp"
            )
            else -> CheckResult(
                "props", "Recovery version property", CheckStatus.UNKNOWN,
                "No recovery version properties are visible from the running system."
            )
        }
    }

    fun checkArtifacts(): CheckResult {
        val dirs = listOf(
            "/sdcard/TWRP", "/sdcard/Fox", "/sdcard/OrangeFox",
            "/data/media/0/TWRP", "/data/media/0/Fox", "/cache/recovery"
        )
        val found = dirs.filter { runCatching { File(it).exists() }.getOrDefault(false) }
        return if (found.isNotEmpty()) {
            CheckResult(
                "artifacts", "Recovery artifacts", CheckStatus.DETECTED,
                "Folders created by custom recoveries: ${found.joinToString(", ")}."
            )
        } else {
            CheckResult(
                "artifacts", "Recovery artifacts", CheckStatus.NOT_FOUND,
                "No TWRP/OrangeFox folders were found on shared storage."
            )
        }
    }

    fun checkPartitionNode(): CheckResult {
        val paths = listOf(
            "/dev/block/bootdevice/by-name/recovery",
            "/dev/block/bootdevice/by-name/recovery_a",
            "/dev/block/by-name/recovery",
            "/dev/block/by-name/recovery_a"
        )
        val found = paths.filter { runCatching { File(it).exists() }.getOrDefault(false) }
        return if (found.isNotEmpty()) {
            CheckResult(
                "partition", "Recovery partition", CheckStatus.UNKNOWN,
                "Recovery partition node found (${found.first()}) — its content was not scanned without root."
            )
        } else {
            CheckResult(
                "partition", "Recovery partition", CheckStatus.UNKNOWN,
                "The recovery partition node is not visible from userspace on this device."
            )
        }
    }

    fun deepScan(context: Context): CheckResult {
        if (!suWorks()) {
            return CheckResult(
                "deep_scan", "Recovery image scan (root)", CheckStatus.UNKNOWN,
                "Skipped — root access was not granted, so the recovery image cannot be read."
            )
        }
        for (name in listOf("recovery", "recovery_a", "recovery_b")) {
            val node = listOf(
                "/dev/block/bootdevice/by-name/$name",
                "/dev/block/by-name/$name"
            ).firstOrNull { runCatching { File(it).exists() }.getOrDefault(false) } ?: continue
            val bytes = ddFirstKilobytes(node, 512) ?: continue
            val marker = listOf("OrangeFox", "TWRP", "FOX")
                .firstOrNull { bytes.indexOf(it.toByteArray()) >= 0 }
            if (marker != null) {
                return CheckResult(
                    "deep_scan", "Recovery image scan (root)", CheckStatus.DETECTED,
                    "Found '$marker' inside $node — a custom recovery image is installed."
                )
            }
        }
        return CheckResult(
            "deep_scan", "Recovery image scan (root)", CheckStatus.NOT_FOUND,
            "The recovery image was read, but no TWRP/OrangeFox markers were found."
        )
    }

    // ---- low-level helpers ---------------------------------------------------

    fun suWorks(): Boolean = runCatching {
        val proc = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val finished = proc.waitFor(5, TimeUnit.SECONDS)
        val out = proc.inputStream.use { it.readBytes().decodeToString() }
        if (!finished) proc.destroy()
        finished && out.contains("uid=0")
    }.getOrDefault(false)

    private fun ddFirstKilobytes(node: String, kb: Int): ByteArray? = runCatching {
        val tmp = File.createTempFile("fox_scan", ".bin")
        try {
            val proc = ProcessBuilder("su", "-c", "dd if=$node bs=1024 count=$kb")
                .redirectOutput(ProcessBuilder.Redirect.to(tmp))
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroy()
                return@runCatching null
            }
            tmp.takeIf { it.length() > 0 }?.readBytes()
        } finally {
            tmp.delete()
        }
    }.getOrNull()

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
