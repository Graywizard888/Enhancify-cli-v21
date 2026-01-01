package app.revanced.cli.command

import app.revanced.library.ApkUtils
import app.revanced.library.ApkUtils.applyTo
import app.revanced.library.Options
import app.revanced.library.Options.setOptions
import app.revanced.library.installation.installer.*
import app.revanced.library.setOptions
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherConfig
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatchesFromJar
import com.reandroid.apkeditor.BundleMerger
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

// ═══════════════════════════════════════════════════════════════════════════════
// ANSI Colors and Styles
// ═══════════════════════════════════════════════════════════════════════════════

private object Colors {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val DIM = "\u001B[2m"
    
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val MAGENTA = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"
    
    const val BRIGHT_RED = "\u001B[91m"
    const val BRIGHT_GREEN = "\u001B[92m"
    const val BRIGHT_YELLOW = "\u001B[93m"
    const val BRIGHT_BLUE = "\u001B[94m"
    const val BRIGHT_MAGENTA = "\u001B[95m"
    const val BRIGHT_CYAN = "\u001B[96m"
    const val BRIGHT_WHITE = "\u001B[97m"
}

// ═══════════════════════════════════════════════════════════════════════════════
// Icons and Symbols
// ═══════════════════════════════════════════════════════════════════════════════

private object Icons {
    const val CHECK = "✓"
    const val CROSS = "✗"
    const val WARNING = "⚠"
    const val INFO = "ℹ"
    const val BULLET = "•"
    const val STAR = "★"
    const val GEAR = "⚙"
    const val PACKAGE = "📦"
    const val ROCKET = "🚀"
    const val CLOCK = "⏱"
    const val FOLDER = "📁"
    const val FILE = "📄"
    const val KEY = "🔑"
    const val PHONE = "📱"
    const val PLUG = "🔌"
    const val TRASH = "🗑"
    const val SPARKLE = "✨"
    const val WRENCH = "🔧"
}

// ═══════════════════════════════════════════════════════════════════════════════
// Enhanced Logger
// ═══════════════════════════════════════════════════════════════════════════════

private object EnhancifyLogger {
    private var startTime: Long = System.currentTimeMillis()
    
    fun resetTimer() {
        startTime = System.currentTimeMillis()
    }
    
    fun printBanner() {
        println()
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ╔══════════════════════════════════════════╗${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ║  ${Colors.BRIGHT_CYAN}▓█▀▀ █▄ █ █▄█ ▄▀▄ █▄ █ ▄▀▀ █ █▀ █ █${Colors.BRIGHT_MAGENTA}  ║${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ║  ${Colors.BRIGHT_CYAN}█▀▀  █ ▀█ █ █ █▀█ █ ▀█ █▄▄ █ █▀ ▀█▀${Colors.BRIGHT_MAGENTA}  ║${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ╠══════════════════════════════════════════╣${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ║     ${Colors.BRIGHT_WHITE}${Icons.SPARKLE} Advanced Patcher ${Icons.SPARKLE}${Colors.BRIGHT_MAGENTA}              ║${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}    ╚══════════════════════════════════════════╝${Colors.RESET}")
        println()
    }
    
    fun printSection(title: String, icon: String = Icons.GEAR) {
        println()
        println("${Colors.BRIGHT_CYAN}${Colors.BOLD}  ┌──────────────────────────────────────────────────┐${Colors.RESET}")
        println("${Colors.BRIGHT_CYAN}${Colors.BOLD}  │ $icon ${Colors.BRIGHT_WHITE}$title${Colors.RESET}${" ".repeat(maxOf(0, 47 - title.length))}${Colors.BRIGHT_CYAN}${Colors.BOLD}│${Colors.RESET}")
        println("${Colors.BRIGHT_CYAN}${Colors.BOLD}  └──────────────────────────────────────────────────┘${Colors.RESET}")
    }
    
    fun printSubSection(title: String) {
        println()
        println("${Colors.BRIGHT_BLUE}${Colors.BOLD}    ▶ $title${Colors.RESET}")
        println("${Colors.DIM}    ${"─".repeat(46)}${Colors.RESET}")
    }
    
    fun info(message: String) {
        println("${Colors.BRIGHT_BLUE}    ${Icons.INFO} ${Colors.WHITE}$message${Colors.RESET}")
    }
    
    fun success(message: String) {
        println("${Colors.BRIGHT_GREEN}    ${Icons.CHECK} ${Colors.WHITE}$message${Colors.RESET}")
    }
    
    fun warning(message: String) {
        println("${Colors.BRIGHT_YELLOW}    ${Icons.WARNING} ${Colors.WHITE}$message${Colors.RESET}")
    }
    
    fun error(message: String) {
        println("${Colors.BRIGHT_RED}    ${Icons.CROSS} ${Colors.WHITE}$message${Colors.RESET}")
    }
    
    fun detail(message: String) {
        println("${Colors.DIM}      ${Icons.BULLET} $message${Colors.RESET}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Progress Bar
// ═══════════════════════════════════════════════════════════════════════════════

private class ProgressBar(
    private val total: Int,
    private val width: Int = 35
) {
    private var current: Int = 0
    private val processedTimes = mutableListOf<Long>()
    
    fun start() {
        current = 0
        render()
    }
    
    fun update(newCurrent: Int, itemName: String = "") {
        current = newCurrent
        render(itemName)
    }
    
    fun recordTime(timeMs: Long) {
        processedTimes.add(timeMs)
    }
    
    private fun getETA(): String {
        if (current == 0 || processedTimes.isEmpty()) return "calculating..."
        
        val avgTime = processedTimes.average()
        val remaining = total - current
        val etaMs = (avgTime * remaining).toLong()
        
        val seconds = (etaMs / 1000) % 60
        val minutes = (etaMs / 1000) / 60
        
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
    
    private fun render(itemName: String = "") {
        val percentage = if (total > 0) (current * 100) / total else 0
        val filled = (width * current) / maxOf(total, 1)
        val empty = width - filled
        
        val progressBar = buildString {
            append("${Colors.BRIGHT_GREEN}")
            append("█".repeat(filled))
            append("${Colors.DIM}")
            append("░".repeat(empty))
            append("${Colors.RESET}")
        }
        
        val eta = if (current < total) " ETA: ${getETA()}" else " ${Icons.CHECK}"
        val percentStr = "$percentage%".padStart(4)
        
        print("\r${Colors.BRIGHT_CYAN}    [${Colors.RESET}$progressBar${Colors.BRIGHT_CYAN}]${Colors.RESET} ")
        print("${Colors.BRIGHT_WHITE}$percentStr${Colors.RESET}")
        print(" ${Colors.DIM}($current/$total)${Colors.RESET}")
        print("${Colors.BRIGHT_YELLOW}$eta${Colors.RESET}")
        
        if (itemName.isNotEmpty()) {
            val truncatedName = if (itemName.length > 25) itemName.take(22) + "..." else itemName
            print(" ${Colors.DIM}$truncatedName${Colors.RESET}")
        }
        
        print(" ".repeat(15))
        
        if (current >= total) {
            println()
        }
    }
    
    fun finish() {
        current = total
        render()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Patch Result Tracking
// ═══════════════════════════════════════════════════════════════════════════════

private data class PatchResultInfo(
    val name: String,
    val success: Boolean,
    val timeMs: Long,
    val errorMessage: String? = null,
    val stackTrace: String? = null
)

private data class WarningInfo(
    val patchName: String,
    val message: String
)

private class PatchingResults {
    val succeeded = mutableListOf<PatchResultInfo>()
    val failed = mutableListOf<PatchResultInfo>()
    val warnings = mutableListOf<WarningInfo>()
    val skipped = mutableListOf<String>()
    var totalTime: Long = 0
    
    fun addSuccess(name: String, timeMs: Long) {
        succeeded.add(PatchResultInfo(name, true, timeMs))
    }
    
    fun addFailure(name: String, timeMs: Long, error: String, stackTrace: String) {
        failed.add(PatchResultInfo(name, false, timeMs, error, stackTrace))
    }
    
    fun addWarning(patchName: String, message: String) {
        warnings.add(WarningInfo(patchName, message))
    }
    
    fun addSkipped(name: String) {
        skipped.add(name)
    }
    
    fun clear() {
        succeeded.clear()
        failed.clear()
        warnings.clear()
        skipped.clear()
        totalTime = 0
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Patch Command
// ═══════════════════════════════════════════════════════════════════════════════

@CommandLine.Command(
    name = "patch",
    description = ["Patch an APK file."],
)
internal object PatchCommand : Runnable {
    private val logger = Logger.getLogger(this::class.java.name)
    private val results = PatchingResults()

    @Spec
    private lateinit var spec: CommandSpec

    @ArgGroup(exclusive = false, multiplicity = "0..*")
    private var selection = mutableSetOf<Selection>()

    internal class Selection {
        @ArgGroup(exclusive = false)
        internal var enabled: EnableSelection? = null

        internal class EnableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: EnableSelector

            internal class EnableSelector {
                @CommandLine.Option(
                    names = ["-e", "--enable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--ei"],
                    description = ["Index of the patch in the combined list of the supplied RVP files."],
                    required = true,
                )
                internal var index: Int? = null
            }

            @CommandLine.Option(
                names = ["-O", "--options"],
                description = ["Option values keyed by option keys."],
                mapFallbackValue = CommandLine.Option.NULL_VALUE,
                converter = [OptionKeyConverter::class, OptionValueConverter::class],
            )
            internal var options = mutableMapOf<String, Any?>()
        }

        @ArgGroup(exclusive = false)
        internal var disable: DisableSelection? = null

        internal class DisableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: DisableSelector

            internal class DisableSelector {
                @CommandLine.Option(
                    names = ["-d", "--disable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--di"],
                    description = ["Index of the patch in the combined list of the supplied RVP files."],
                    required = true,
                )
                internal var index: Int? = null
            }
        }
    }

    @CommandLine.Option(
        names = ["--exclusive"],
        description = ["Disable all patches except the ones enabled."],
        showDefaultValue = ALWAYS,
    )
    private var exclusive = false

    @CommandLine.Option(
        names = ["-f", "--force"],
        description = ["Don't check for compatibility with the supplied APK's version."],
        showDefaultValue = ALWAYS,
    )
    private var force: Boolean = false

    private var outputFilePath: File? = null

    @CommandLine.Option(
        names = ["-o", "--out"],
        description = ["Path to save the patched APK file to. Defaults to the same path as the supplied APK file."],
    )
    @Suppress("unused")
    private fun setOutputFilePath(outputFilePath: File?) {
        this.outputFilePath = outputFilePath?.absoluteFile
    }

    @CommandLine.Option(
        names = ["-i", "--install"],
        description = ["Serial of the ADB device to install to. If not specified, the first connected device will be used."],
        fallbackValue = "",
        arity = "0..1",
    )
    private var deviceSerial: String? = null

    @CommandLine.Option(
        names = ["--mount"],
        description = ["Install the patched APK file by mounting."],
        showDefaultValue = ALWAYS,
    )
    private var mount: Boolean = false

    @CommandLine.Option(
        names = ["--keystore"],
        description = [
            "Path to the keystore file containing a private key and certificate pair to sign the patched APK file with. " +
                "Defaults to the same directory as the supplied APK file.",
        ],
    )
    private var keyStoreFilePath: File? = null

    @CommandLine.Option(
        names = ["--keystore-password"],
        description = ["Password of the keystore. Empty password by default."],
    )
    private var keyStorePassword: String? = null

    @CommandLine.Option(
        names = ["--keystore-entry-alias"],
        description = ["Alias of the private key and certificate pair keystore entry."],
        showDefaultValue = ALWAYS,
    )
    private var keyStoreEntryAlias = "ReVanced Key"

    @CommandLine.Option(
        names = ["--keystore-entry-password"],
        description = ["Password of the keystore entry."],
    )
    private var keyStoreEntryPassword = ""

    @CommandLine.Option(
        names = ["--signer"],
        description = ["The name of the signer to sign the patched APK file with."],
        showDefaultValue = ALWAYS,
    )
    private var signer = "ReVanced"

    @CommandLine.Option(
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    private var aaptBinaryPath: File? = null

    @CommandLine.Option(
        names = ["--unsigned"],
        description = ["Disable signing of the final apk."],
    )
    private var unsigned: Boolean = false

    @CommandLine.Option(
        names = ["--rip-lib"],
        description = ["Rip native libs from APK."],
    )
    private var ripLibs = arrayOf<String>()

    @CommandLine.Option(
        names = ["--legacy-options"],
        description = ["Path to patch options JSON file."],
    )
    private var optionsFile: File? = null

    @CommandLine.Option(
        names = ["--purge"],
        description = ["Purge temporary files directory after patching."],
        showDefaultValue = ALWAYS,
    )
    private var purge: Boolean = false

    @CommandLine.Parameters(
        description = ["APK file to patch."],
        arity = "1",
    )
    @Suppress("unused")
    private fun setApk(apk: File) {
        if (!apk.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "APK file ${apk.path} does not exist",
            )
        }
        this.apk = apk
    }

    private lateinit var apk: File

    @CommandLine.Option(
        names = ["-p", "--patches"],
        description = ["One or more path to RVP files."],
        required = true,
    )
    @Suppress("unused")
    private fun setPatchesFile(patchesFiles: Set<File>) {
        patchesFiles.firstOrNull { !it.exists() }?.let {
            throw CommandLine.ParameterException(spec.commandLine(), "${it.name} can't be found")
        }
        this.patchesFiles = patchesFiles
    }

    private var patchesFiles = emptySet<File>()

    @CommandLine.Option(
        names = ["--custom-aapt2-binary"],
        description = ["Path to a custom AAPT binary to compile resources with."],
    )
    @Suppress("unused")
    private fun setAaptBinaryPath(aaptBinaryPath: File) {
        if (!aaptBinaryPath.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "AAPT binary ${aaptBinaryPath.name} does not exist",
            )
        }
        this.aaptBinaryPath = aaptBinaryPath
    }

    override fun run() {
        results.clear()
        EnhancifyLogger.resetTimer()
        EnhancifyLogger.printBanner()
        
        val totalStartTime = System.currentTimeMillis()

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Setup & Initialization
        // ═══════════════════════════════════════════════════════════════════════
        
        EnhancifyLogger.printSection("INITIALIZATION", Icons.GEAR)
        
        if (apk.extension != "apk") {
            EnhancifyLogger.printSubSection("Merging split apk")
            EnhancifyLogger.info("Input: ${apk.name}")
            
            val mergeTime = measureTimeMillis {
                apk = BundleMerger.mergeBundle(apk, null)
            }
            
            EnhancifyLogger.success("Merged in ${mergeTime}ms")
        }

        val outputFilePath =
            outputFilePath ?: File("").absoluteFile.resolve(
                "${apk.nameWithoutExtension}-patched.${apk.extension}",
            )

        val temporaryFilesPath =
            temporaryFilesPath ?: outputFilePath.parentFile.resolve(
                "${outputFilePath.nameWithoutExtension}-temporary-files",
            )

        val optionsFile =
            optionsFile ?: outputFilePath.parentFile.resolve(
                "${outputFilePath.nameWithoutExtension}-options.json",
            )

        val keystoreFilePath =
            keyStoreFilePath ?: outputFilePath.parentFile
                .resolve("${outputFilePath.nameWithoutExtension}.keystore")

        EnhancifyLogger.printSubSection("Configuration")
        EnhancifyLogger.info("${Icons.FILE} Input: ${apk.name}")
        EnhancifyLogger.info("${Icons.FOLDER} Output: ${outputFilePath.name}")
        EnhancifyLogger.info("${Icons.KEY} Keystore: ${keystoreFilePath.name}")
        
        if (unsigned) {
            EnhancifyLogger.warning("Signing disabled")
        }
        
        if (ripLibs.isNotEmpty()) {
            EnhancifyLogger.info("${Icons.WRENCH} Ripping: ${ripLibs.joinToString(", ")}")
        }

        val installer = if (deviceSerial != null) {
            EnhancifyLogger.printSubSection("Device Connection")
            val deviceSerial = deviceSerial!!.ifEmpty { null }

            try {
                val inst = if (mount) AdbRootInstaller(deviceSerial) else AdbInstaller(deviceSerial)
                EnhancifyLogger.success("Connected${if (deviceSerial != null) ": $deviceSerial" else ""}")
                inst
            } catch (e: DeviceNotFoundException) {
                EnhancifyLogger.error("Device not found")
                EnhancifyLogger.detail("Ensure device is connected")
                printFinalSummary(totalStartTime)
                return
            }
        } else null

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Loading Patches
        // ═══════════════════════════════════════════════════════════════════════
        
        EnhancifyLogger.printSection("LOADING PATCHES", Icons.PACKAGE)
        
        EnhancifyLogger.printSubSection("Patch Files")
        patchesFiles.forEach { EnhancifyLogger.info("${Icons.FILE} ${it.name}") }
        
        val patches: Set<Patch<*>>
        val loadTime = measureTimeMillis {
            patches = loadPatchesFromJar(patchesFiles)
        }
        
        EnhancifyLogger.success("Loaded ${patches.size} patches in ${loadTime}ms")

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Patching
        // ═══════════════════════════════════════════════════════════════════════
        
        EnhancifyLogger.printSection("Initiating Patching", Icons.ROCKET)
        
        val patcherTemporaryFilesPath = temporaryFilesPath.resolve("patcher")

        val (packageName, patcherResult) = Patcher(
            PatcherConfig(
                apk,
                patcherTemporaryFilesPath,
                aaptBinaryPath?.path,
                patcherTemporaryFilesPath.absolutePath,
            ),
        ).use { patcher ->
            val packageName = patcher.context.packageMetadata.packageName
            val packageVersion = patcher.context.packageMetadata.packageVersion

            EnhancifyLogger.printSubSection("APK Information")
            EnhancifyLogger.info("${Icons.PACKAGE} Package: $packageName")
            EnhancifyLogger.info("${Icons.INFO} Version: $packageVersion")

            val filteredPatches = patches.filterPatchSelection(packageName, packageVersion)
            val patchesList = patches.toList()

            EnhancifyLogger.printSubSection("Patch Options")

            val selectionMap = selection.filter {
                it.enabled != null && it.enabled!!.options.isNotEmpty()
            }.associate {
                val enabled = it.enabled!!
                (enabled.selector.name ?: patchesList[enabled.selector.index!!].name!!) to enabled.options
            }
            
            if (selectionMap.isNotEmpty()) {
                EnhancifyLogger.info("Custom options: ${selectionMap.size}")
                selectionMap.let(filteredPatches::setOptions)
            } else if (optionsFile.exists()) {
                EnhancifyLogger.info("Loading from options file")
                patches.setOptions(optionsFile)
            } else {
                EnhancifyLogger.info("Creating default options")
                Options.serialize(patches, prettyPrint = true).let(optionsFile::writeText)
            }

            patcher += filteredPatches

            EnhancifyLogger.printSubSection("Applying Patches")
            EnhancifyLogger.info("Patches to apply: ${filteredPatches.size}")
            println()
            
            val progressBar = ProgressBar(filteredPatches.size)
            progressBar.start()
            
            var currentPatch = 0

            runBlocking {
                patcher().collect { patchResult ->
                    currentPatch++
                    val patchName = patchResult.patch.toString()
                    val exception = patchResult.exception
                    
                    val patchTime = 50L // Approximate time
                    progressBar.recordTime(patchTime)
                    
                    if (exception == null) {
                        results.addSuccess(patchName, patchTime)
                    } else {
                        val stackTrace = StringWriter().use { writer ->
                            exception.printStackTrace(PrintWriter(writer))
                            writer.toString()
                        }
                        results.addFailure(patchName, patchTime, exception.message ?: "Unknown error", stackTrace)
                    }
                    
                    progressBar.update(currentPatch, patchName)
                }
            }
            
            progressBar.finish()
            println()

            patcher.context.packageMetadata.packageName to patcher.get()
        }

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Saving APK
        // ═══════════════════════════════════════════════════════════════════════
        
        EnhancifyLogger.printSection("SAVING APK", Icons.FILE)
        
        EnhancifyLogger.printSubSection("Finalizing")
        
        val applyTime = measureTimeMillis {
            apk.copyTo(temporaryFilesPath.resolve(apk.name), overwrite = true).apply {
                patcherResult.applyTo(this, ripLibs)
            }.let { patchedApkFile ->
                if (!mount && !unsigned) {
                    EnhancifyLogger.info("${Icons.KEY} Signing APK...")
                    ApkUtils.signApk(
                        patchedApkFile,
                        outputFilePath,
                        signer,
                        ApkUtils.KeyStoreDetails(
                            keystoreFilePath,
                            keyStorePassword,
                            keyStoreEntryAlias,
                            keyStoreEntryPassword,
                        ),
                    )
                    EnhancifyLogger.success("Signed with \"$signer\"")
                } else {
                    patchedApkFile.copyTo(outputFilePath, overwrite = true)
                    if (unsigned) EnhancifyLogger.warning("APK unsigned")
                }
            }
        }
        
        EnhancifyLogger.success("Saved in ${applyTime}ms")
        EnhancifyLogger.detail(outputFilePath.absolutePath)

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Installation
        // ═══════════════════════════════════════════════════════════════════════
        
        if (deviceSerial != null && installer != null) {
            EnhancifyLogger.printSection("INSTALLATION", Icons.PHONE)
            
            EnhancifyLogger.printSubSection("Installing")
            EnhancifyLogger.info("Package: $packageName")
            
            runBlocking {
                when (val result = installer.install(Installer.Apk(outputFilePath, packageName))) {
                    RootInstallerResult.FAILURE -> {
                        EnhancifyLogger.error("Mount failed")
                        results.addWarning("Installation", "Mount failed")
                    }
                    is AdbInstallerResult.Failure -> {
                        EnhancifyLogger.error("Install failed")
                        results.addWarning("Installation", result.exception.toString())
                    }
                    else -> EnhancifyLogger.success("Installed successfully")
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION: Cleanup
        // ═══════════════════════════════════════════════════════════════════════
        
        if (purge) {
            EnhancifyLogger.printSection("Initating Cleanup", Icons.TRASH)
            EnhancifyLogger.printSubSection("Purging Files")
            purge(temporaryFilesPath)
        }

        results.totalTime = System.currentTimeMillis() - totalStartTime
        printFinalSummary(totalStartTime)
    }

    private fun printFinalSummary(startTime: Long) {
        val totalTime = System.currentTimeMillis() - startTime
        val totalSeconds = totalTime / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timeStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

        println()
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}  ╔════════════════════════════════════════════════╗${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}  ║         ${Colors.BRIGHT_WHITE}${Icons.STAR} PATCHING SUMMARY ${Icons.STAR}${Colors.BRIGHT_MAGENTA}                ║${Colors.RESET}")
        println("${Colors.BRIGHT_MAGENTA}${Colors.BOLD}  ╚════════════════════════════════════════════════╝${Colors.RESET}")
        
        println()
        println("${Colors.BRIGHT_WHITE}${Colors.BOLD}    ${Icons.CLOCK} Total Time: ${Colors.BRIGHT_CYAN}$timeStr${Colors.RESET}")
        println()
        
        val successCount = results.succeeded.size
        val failedCount = results.failed.size
        val warningCount = results.warnings.size
        val skippedCount = results.skipped.size
        
        println("    ${Colors.BRIGHT_GREEN}${Icons.CHECK} Succeeded:${Colors.RESET} $successCount")
        println("    ${Colors.BRIGHT_RED}${Icons.CROSS} Failed:${Colors.RESET}    $failedCount")
        println("    ${Colors.BRIGHT_YELLOW}${Icons.WARNING} Warnings:${Colors.RESET}  $warningCount")
        println("    ${Colors.DIM}${Icons.BULLET} Skipped:${Colors.RESET}   $skippedCount")
        
        // Succeeded
        if (results.succeeded.isNotEmpty()) {
            println()
            println("${Colors.BRIGHT_GREEN}${Colors.BOLD}    ┌─ SUCCEEDED ───────────────────────────────────┐${Colors.RESET}")
            results.succeeded.forEach {
                println("${Colors.GREEN}    │ ${Icons.CHECK} ${it.name}${Colors.RESET}")
            }
            println("${Colors.BRIGHT_GREEN}${Colors.BOLD}    └───────────────────────────────────────────────┘${Colors.RESET}")
        }
        
        // Failed
        if (results.failed.isNotEmpty()) {
            println()
            println("${Colors.BRIGHT_RED}${Colors.BOLD}    ┌─ FAILED ──────────────────────────────────────┐${Colors.RESET}")
            results.failed.forEach { patch ->
                println("${Colors.RED}    │ ${Icons.CROSS} ${patch.name}${Colors.RESET}")
                println("${Colors.DIM}    │   Error: ${patch.errorMessage?.take(40)}${Colors.RESET}")
                patch.stackTrace?.lines()?.take(2)?.forEach { line ->
                    if (line.isNotBlank()) {
                        println("${Colors.DIM}    │   ${line.trim().take(45)}${Colors.RESET}")
                    }
                }
                println("${Colors.RED}    │${Colors.RESET}")
            }
            println("${Colors.BRIGHT_RED}${Colors.BOLD}    └───────────────────────────────────────────────┘${Colors.RESET}")
        }
        
        // Warnings
        if (results.warnings.isNotEmpty()) {
            println()
            println("${Colors.BRIGHT_YELLOW}${Colors.BOLD}    ┌─ WARNINGS ────────────────────────────────────┐${Colors.RESET}")
            results.warnings.forEach {
                println("${Colors.YELLOW}    │ ${Icons.WARNING} [${it.patchName}]${Colors.RESET}")
                println("${Colors.DIM}    │   ${it.message.take(42)}${Colors.RESET}")
            }
            println("${Colors.BRIGHT_YELLOW}${Colors.BOLD}    └───────────────────────────────────────────────┘${Colors.RESET}")
        }
        
        // Skipped (collapsed)
        if (results.skipped.isNotEmpty()) {
            println()
            println("${Colors.DIM}    ┌─ SKIPPED (${results.skipped.size}) ────────────────────────────────┐${Colors.RESET}")
            results.skipped.take(5).forEach {
                println("${Colors.DIM}    │ ${Icons.BULLET} $it${Colors.RESET}")
            }
            if (results.skipped.size > 5) {
                println("${Colors.DIM}    │ ... and ${results.skipped.size - 5} more${Colors.RESET}")
            }
            println("${Colors.DIM}    └───────────────────────────────────────────────┘${Colors.RESET}")
        }
        
        // Final Status
        println()
        if (results.failed.isEmpty()) {
            println("${Colors.BRIGHT_GREEN}${Colors.BOLD}  ════════════════════════════════════════════════════${Colors.RESET}")
            println("${Colors.BRIGHT_GREEN}${Colors.BOLD}      ${Icons.SPARKLE} PATCHING COMPLETED SUCCESSFULLY! ${Icons.SPARKLE}${Colors.RESET}")
            println("${Colors.BRIGHT_GREEN}${Colors.BOLD}  ════════════════════════════════════════════════════${Colors.RESET}")
        } else {
            println("${Colors.BRIGHT_YELLOW}${Colors.BOLD}  ════════════════════════════════════════════════════${Colors.RESET}")
            println("${Colors.BRIGHT_YELLOW}${Colors.BOLD}    ${Icons.WARNING} COMPLETED WITH ${results.failed.size} ERROR(S) ${Icons.WARNING}${Colors.RESET}")
            println("${Colors.BRIGHT_YELLOW}${Colors.BOLD}  ════════════════════════════════════════════════════${Colors.RESET}")
        }
        println()
    }

    private fun Set<Patch<*>>.filterPatchSelection(
        packageName: String,
        packageVersion: String,
    ): Set<Patch<*>> = buildSet {
        val enabledPatchesByName = selection.mapNotNull { it.enabled?.selector?.name }.toSet()
        val enabledPatchesByIndex = selection.mapNotNull { it.enabled?.selector?.index }.toSet()
        val disabledPatches = selection.mapNotNull { it.disable?.selector?.name }.toSet()
        val disabledPatchesByIndex = selection.mapNotNull { it.disable?.selector?.index }.toSet()

        this@filterPatchSelection.withIndex().forEach patchLoop@{ (i, patch) ->
            val patchName = patch.name!!

            if (patchName in disabledPatches || i in disabledPatchesByIndex) {
                results.addSkipped(patchName)
                return@patchLoop
            }

            patch.compatiblePackages?.let { packages ->
                packages.singleOrNull { (name, _) -> name == packageName }?.let { (_, versions) ->
                    if (versions?.isEmpty() == true) {
                        results.addWarning(patchName, "Incompatible with $packageName")
                        return@patchLoop
                    }

                    val matchesVersion = force || versions?.any { it == packageVersion } ?: true

                    if (!matchesVersion) {
                        results.addWarning(patchName, "Version mismatch: requires ${versions?.joinToString()}")
                        return@patchLoop
                    }
                } ?: run {
                    results.addSkipped(patchName)
                    return@patchLoop
                }
            }

            val isEnabled = !exclusive && patch.use
            val isManuallyEnabled = patchName in enabledPatchesByName || i in enabledPatchesByIndex

            if (!(isEnabled || isManuallyEnabled)) {
                results.addSkipped(patchName)
                return@patchLoop
            }

            add(patch)
        }
    }

    private fun purge(resourceCachePath: File) {
        var purgedCount = 0
        
        // Patcher cache
        if (resourceCachePath.exists()) {
            if (resourceCachePath.deleteRecursively()) {
                EnhancifyLogger.success("Removed: ${resourceCachePath.name}")
                purgedCount++
            } else {
                EnhancifyLogger.warning("Failed: ${resourceCachePath.name}")
            }
        }
        
        // Termux tmp directory
        val termuxTmpDir = File("/data/data/com.termux/files/usr/tmp")
        if (termuxTmpDir.exists() && termuxTmpDir.isDirectory) {
            EnhancifyLogger.info("Cleaning Termux tmp...")
            
            termuxTmpDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("patcher") || 
                    file.name.startsWith("revanced") ||
                    file.name.startsWith("enhancify") ||
                    file.name.contains("tmp")) {
                    try {
                        if (file.deleteRecursively()) {
                            EnhancifyLogger.detail("Removed: ${file.name}")
                            purgedCount++
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        
        // Java tmp directory
        val javaTmpDir = File(System.getProperty("java.io.tmpdir", "/tmp"))
        if (javaTmpDir.exists() && javaTmpDir.isDirectory && javaTmpDir.canWrite()) {
            javaTmpDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("patcher") || file.name.startsWith("revanced")) {
                    try {
                        if (file.deleteRecursively()) {
                            EnhancifyLogger.detail("Removed: ${file.name}")
                            purgedCount++
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        
        println()
        EnhancifyLogger.success("Cleanup complete ($purgedCount items)")
    }
}
