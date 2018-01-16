package com.emberjs.resolver

import com.emberjs.EmberFileType
import com.emberjs.utils.findMainPackageJson
import com.emberjs.utils.parentEmberModule
import com.emberjs.utils.parentModule
import com.emberjs.utils.parents
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile

data class EmberName(val emberPackageName: String, val type: String, val name: String) {

    val fullName by lazy { "$emberPackageName:$type:$name" }

    val displayName by lazy {
        if (type == "template" && name.startsWith("components/")) {
            "${name.removePrefix("components/").replace('/', '.')} component-template"
        } else {
            "${name.replace('/', '.')} $type"
        }
    }

    val isTest: Boolean = type.endsWith("-test")

    val isInApp: Boolean = emberPackageName.endsWith("/app")

    val isInAddon: Boolean = emberPackageName.endsWith("/addon")

    val onlyPackageName by lazy { emberPackageName.removeSuffix("/app").removeSuffix("/addon") }

    companion object {
        fun from(fullName: String): EmberName? {
            val parts = fullName.split(":")
            return when {
                parts.count() == 3 -> EmberName(parts[0], parts[1], parts[2])
                else -> null
            }
        }

        fun from(file: VirtualFile) = file.parentEmberModule?.let { from(it, file) }

        fun from(root: VirtualFile, file: VirtualFile): EmberName? {
            val emberPackageName = findMainPackageJson(file)?.name ?: ""
            val appFolder = root.findChild("app")
            val addonFolder = root.findChild("addon")
            val testsFolder = root.findChild("tests")
            val unitTestsFolder = testsFolder?.findChild("unit")
            val integrationTestsFolder = testsFolder?.findChild("integration")
            val acceptanceTestsFolder = testsFolder?.findChild("acceptance")
            val dummyAppFolder = testsFolder?.findFileByRelativePath("dummy/app")

            return fromPod(emberPackageName, appFolder, file) ?:
                    fromPod(emberPackageName, addonFolder, file) ?:
                    fromPodTest(emberPackageName, unitTestsFolder, file) ?:
                    fromPodTest(emberPackageName, integrationTestsFolder, file) ?:
                    fromClassic(emberPackageName, appFolder, file) ?:
                    fromClassic(emberPackageName, addonFolder, file) ?:
                    fromClassic(emberPackageName, dummyAppFolder, file) ?:
                    fromClassicTest(emberPackageName, unitTestsFolder, file) ?:
                    fromClassicTest(emberPackageName, integrationTestsFolder, file) ?:
                    fromAcceptanceTest(emberPackageName, acceptanceTestsFolder, file)
        }

        fun fromClassic(emberPackageName: String, appFolder: VirtualFile?, file: VirtualFile): EmberName? {
            appFolder ?: return null

            val typeFolder = file.parents.find { it.parent == appFolder } ?: return null

            return EmberFileType.FOLDER_NAMES[typeFolder.name]?.let { type ->

                val path = file.parents
                        .takeWhile { it != typeFolder }
                        .map { it.name }
                        .reversed()
                        .joinToString("/")

                val name = "$path/${file.nameWithoutExtension}".removePrefix("/")

                EmberName("$emberPackageName/${appFolder.name}", type.name.toLowerCase(), name)
            }
        }

        fun fromClassicTest(emberPackageName: String, testsFolder: VirtualFile?, file: VirtualFile): EmberName? {
            testsFolder ?: return null

            val typeFolder = file.parents.find { it.parent == testsFolder } ?: return null

            val testSuffix = when (testsFolder.name) {
                "unit" -> "-test"
                else -> "-${testsFolder.name}-test"
            }

            return EmberFileType.FOLDER_NAMES[typeFolder.name]?.let { type ->

                val path = file.parents
                        .takeWhile { it != typeFolder }
                        .map { it.name }
                        .reversed()
                        .joinToString("/")

                val name = "$path/${file.nameWithoutExtension.removeSuffix("-test")}".removePrefix("/")

                EmberName(emberPackageName, "${type.name.toLowerCase()}$testSuffix", name)
            }
        }

        fun fromAcceptanceTest(emberPackageName: String, testsFolder: VirtualFile?, file: VirtualFile): EmberName? {
            testsFolder ?: return null

            if (!isAncestor(testsFolder, file, true))
                return null

            val path = file.parents
                    .takeWhile { it != testsFolder }
                    .map { it.name }
                    .reversed()
                    .joinToString("/")

            val name = "$path/${file.nameWithoutExtension.removeSuffix("-test")}".removePrefix("/")

            return EmberName(emberPackageName, "acceptance-test", name)
        }

        fun fromPod(emberPackageName: String, appFolder: VirtualFile?, file: VirtualFile): EmberName? {
            appFolder ?: return null

            if (!isAncestor(appFolder, file, true))
                return null

            return EmberFileType.FILE_NAMES[file.name]?.let { type ->

                file.parents.takeWhile { it != appFolder }
                        .map { it.name }
                        .reversed()
                        .joinToString("/")
                        .let {
                            when (type) {
                                EmberFileType.COMPONENT -> it.removePrefix("components/")
                                else -> it
                            }
                        }
                        .let { EmberName(emberPackageName, type.name.toLowerCase(), it) }
            }
        }

        fun fromPodTest(emberPackageName: String, testsFolder: VirtualFile?, file: VirtualFile): EmberName? {
            testsFolder ?: return null

            if (!isAncestor(testsFolder, file, true))
                return null

            val fileName = "${file.nameWithoutExtension.removeSuffix("-test")}.${file.extension}"

            val testSuffix = when (testsFolder.name) {
                "unit" -> "-test"
                else -> "-${testsFolder.name}-test"
            }

            return EmberFileType.FILE_NAMES[fileName]?.let { type ->

                val name = file.parents
                        .takeWhile { it != testsFolder }
                        .map { it.name }
                        .reversed()
                        .joinToString("/")
                        .let {
                            when (type) {
                                EmberFileType.COMPONENT -> it.removePrefix("components/")
                                else -> it
                            }
                        }

                EmberName(emberPackageName, "${type.name.toLowerCase()}$testSuffix", name)
            }
        }
    }
}
