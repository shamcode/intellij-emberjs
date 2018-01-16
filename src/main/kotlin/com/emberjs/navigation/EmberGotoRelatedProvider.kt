package com.emberjs.navigation

import com.emberjs.index.EmberNameIndex
import com.emberjs.resolver.EmberName
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope

class EmberGotoRelatedProvider : GotoRelatedProvider() {

    override fun getItems(context: DataContext): List<GotoRelatedItem> {
        val project = PlatformDataKeys.PROJECT.getData(context) ?: return listOf()
        val file = PlatformDataKeys.VIRTUAL_FILE.getData(context) ?: return listOf()

        val psiManager = PsiManager.getInstance(project)

        return getItems(file, project)
                .map { EmberGotoRelatedItem.from(it.first, psiManager.findFile(it.second)) }
                .filterNotNull()
    }

    fun getItems(file: VirtualFile, project: Project): List<Pair<EmberName, VirtualFile>> {
        val name = EmberName.from(file) ?: return listOf()

        val modulesToSearch = when {
            name.type == "template" && name.name.startsWith("components/") ->
                listOf(EmberName(name.emberPackageName,"component", name.name.removePrefix("components/")))
            name.type == "component" ->
                listOf(EmberName(name.emberPackageName,"template", "components/${name.name}"))
            else -> RELATED_TYPES[name.type].orEmpty().flatMap {
                when {
                    name.isInApp || name.isInAddon ->
                        listOf(
                                EmberName("${name.onlyPackageName}/app", it, name.name),
                                EmberName("${name.onlyPackageName}/addon", it, name.name)
                        )
                    else -> listOf(EmberName(name.emberPackageName, it, name.name))
                }
            }
        }

        val appAddonModulesToSearch = when {
            name.isInApp ->
                listOf(EmberName("${name.onlyPackageName}/addon", name.type, name.name))
            name.isInAddon ->
                listOf(EmberName("${name.onlyPackageName}/app", name.type, name.name))
            else -> emptyList()
        }

        val scope = ProjectScope.getAllScope(project)

        return EmberNameIndex.getFilteredKeys(scope) { it in modulesToSearch || it in appAddonModulesToSearch }
                .flatMap { module -> EmberNameIndex.getContainingFiles(module, scope).map { module to it } }
    }

    companion object {
        val RELATED_TYPES = mapOf(
                "controller" to listOf("route", "template"),
                "route" to listOf("controller", "template"),
                "template" to listOf("controller", "route"),
                "model" to listOf("adapter", "serializer"),
                "adapter" to listOf("model", "serializer"),
                "serializer" to listOf("adapter", "model")
        )
    }
}
