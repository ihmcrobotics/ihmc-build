package us.ihmc.build

import groovy.lang.MissingPropertyException
import groovy.util.Eval
import org.gradle.api.GradleException
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File

class IHMCSettingsConfigurator(val settings: Settings, val logger: Logger, val ext: ExtraPropertiesExtension)
{
   lateinit var titleCasedName: String
   lateinit var extraSourceSets: ArrayList<String>
   lateinit var publishUrl: String
   var compositeSearchHeight: Int = 0
   var excludeFromCompositeBuild: Boolean = false
   
   init
   {
      logInfo(logger, "Evaluating " + settings.rootProject.projectDir.toPath().fileName.toString() + " settings.gradle")
      ext["org.gradle.workers.max"] = 200
      
      if (settings.gradle.gradleVersion.compareTo("4.0") < 0)
      {
         val message = "Please upgrade to Gradle version 4.1 or higher! (Recommended versions: 4.1, 4.2.1, or later)"
         logError(logger, message)
         throw GradleException(message)
      }
   }
   
   @Deprecated("Here for backwards compatibility.")
   fun configureProjectName(dummy: Any?)
   {
      // to be deleted
   }
   
   fun configureExtraSourceSets(vararg dummy: Any?)
   {
      for (sourceSetName in extraSourceSets)
      {
         val dir1 = File(settings.rootProject.projectDir, "src/$sourceSetName")
         dir1.mkdir()
         File(dir1, "java").mkdir()
      }
      
      if (ext.has("useLegacySourceSets") && ext.get("useLegacySourceSets") as String == "true")
      {
      
      }
      else
      {
         for (sourceSetName in extraSourceSets)
         {
            val kebabCasedSourceSetName = toKebabCased(sourceSetName)
            settings.include("src/$kebabCasedSourceSetName")
            settings.project(":src/$kebabCasedSourceSetName").name = settings.rootProject.name + "-" + kebabCasedSourceSetName
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
   
   fun configureAsGroupOfProjects()
   {
      titleCasedName = titleCasedNameCompatibility(settings.rootProject.name, logger, ext)
      settings.rootProject.name = titleToKebabCase(titleCasedName)
      checkForPropertyInternal("isProjectGroup", "true")
      publishUrl = publishUrlCompatibility(logger, ext)
      compositeSearchHeight = compositeSearchHeightCompatibility(logger, ext)
      checkForPropertyInternal("excludeFromCompositeBuild", "false (default)")
      checkForPropertyInternal("org.gradle.workers.max", "200")
   }
   
   fun checkRequiredPropertiesAreSet()
   {
      titleCasedName = titleCasedNameCompatibility(settings.rootProject.name, logger, ext)
      settings.rootProject.name = titleToKebabCase(titleCasedName)
      checkForPropertyInternal("extraSourceSets", "[] (ex. [\"test\", \"visualizers\"]")
      publishUrl = publishUrlCompatibility(logger, ext)
      compositeSearchHeight = compositeSearchHeightCompatibility(logger, ext)
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
            "extraSourceSets"             -> extraSourceSets = Eval.me(ext.get(property) as String) as ArrayList<String>
            "excludeFromCompositeBuild"   -> excludeFromCompositeBuild = (ext.get(property) as String).toBoolean()
         }
      }
   }
}
