package us.ihmc.build

import groovy.lang.MissingPropertyException
import groovy.util.Eval
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File

class IHMCSettingsConfigurator(val settings: Settings, val logger: Logger, val ext: ExtraPropertiesExtension)
{
   lateinit var hyphenatedName: String
   lateinit var pascalCasedName: String
   lateinit var extraSourceSets: ArrayList<String>
   lateinit var publishMode: String
   lateinit var dependencyMode: String
   var depthFromWorkspaceDirectory: Int = 1
   var includeBuildsFromWorkspace: Boolean = true
   var excludeFromCompositeBuild: Boolean = false
   
   init
   {
      logger.info("[ihmc-build] Evaluating " + settings.rootProject.projectDir.toPath().fileName.toString() + " settings.gradle")
      ext.set("org.gradle.workers.max", 200)
   }
   
   fun configureProjectName(hyphenatedName: String)
   {
      settings.rootProject.name = hyphenatedName
   }
   
   fun configureExtraSourceSets(dummyVar2: Any?)
   {
      for (sourceSetName in extraSourceSets)
      {
         val dir1 = File(settings.rootProject.projectDir, sourceSetName)
         dir1.mkdir()
         File(dir1, "src").mkdir()
      }
      
      if (ext.has("useLegacySourceSets") && ext.get("useLegacySourceSets") as String == "true")
      {
      
      }
      else
      {
         for (sourceSetName in extraSourceSets)
         {
            settings.include(arrayOf(sourceSetName))
            settings.project(":" + sourceSetName).name = settings.rootProject.name + "-" + sourceSetName
         }
      }
   }
   
   fun findAndIncludeCompositeBuilds()
   {
      if (settings.startParameter.isSearchUpwards)
      {
         val compositeBuildAssembler = IHMCCompositeBuildAssembler(this)
         for (buildToInclude in compositeBuildAssembler.findCompositeBuilds())
         {
            settings.includeBuild(buildToInclude)
         }
      }
   }
   
   fun checkRequiredPropertiesAreSet()
   {
      checkForPropertyInternal("hyphenatedName", "your-project-hyphenated")
      checkForPropertyInternal("pascalCasedName", "YourProjectPascalCased")
      checkForPropertyInternal("extraSourceSets", "[] (ex. [\"test\", \"visualizers\"]")
      checkForPropertyInternal("publishMode", "SNAPSHOT (default)")
      checkForPropertyInternal("dependencyMode", "SNAPSHOT-LATEST (default)")
      checkForPropertyInternal("depthFromWorkspaceDirectory", "1 (default)")
      checkForPropertyInternal("includeBuildsFromWorkspace", "true (default)")
      checkForPropertyInternal("excludeFromCompositeBuild", "false (default)")
      checkForPropertyInternal("org.gradle.workers.max", "200")
   }
   
   private fun checkForPropertyInternal(property: String, message: String)
   {
      if (!ext.has(property))
      {
         throw MissingPropertyException("Please set $property = $message in gradle.properties.")
      }
      else
      {
         when (property)
         {
            "hyphenatedName"              -> hyphenatedName = ext.get(property) as String
            "pascalCasedName"             -> pascalCasedName = ext.get(property) as String
            "extraSourceSets"             -> extraSourceSets = Eval.me(ext.get(property) as String) as ArrayList<String>
            "publishMode"                 -> publishMode = ext.get(property) as String
            "dependencyMode"              -> dependencyMode = ext.get(property) as String
            "depthFromWorkspaceDirectory" -> depthFromWorkspaceDirectory = (ext.get(property) as String).toInt()
            "includeBuildsFromWorkspace"  -> includeBuildsFromWorkspace = (ext.get(property) as String).toBoolean()
            "excludeFromCompositeBuild"   -> excludeFromCompositeBuild = (ext.get(property) as String).toBoolean()
         }
      }
   }
}
