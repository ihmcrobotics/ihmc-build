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
   lateinit var extraSourceSets: ArrayList<String>
   var compositeSearchHeight: Int = 0

   init
   {
      logInfo(logger, "Evaluating " + settings.rootProject.projectDir.toPath().fileName.toString() + " settings.gradle")
      ext["org.gradle.workers.max"] = 200
      
      if (SemanticVersionNumber(settings.gradle.gradleVersion).compareTo(SemanticVersionNumber("5.3.1")) < 0)
      {
         val message = "Gradle versions earlier than 5.3.1 are not supported. Please upgrade to the latest version."
         logError(logger, message)
         throw GradleException(message)
      }
   }
   
   fun configureExtraSourceSets()
   {
      for (sourceSetName in extraSourceSets)
      {
         val dir1 = File(settings.rootProject.projectDir, "src/$sourceSetName")
         dir1.mkdir()
         File(dir1, "java").mkdir()
      }
      
      for (sourceSetName in extraSourceSets)
      {
         val kebabCasedSourceSetName = toKebabCased(sourceSetName)
         settings.include("src/$kebabCasedSourceSetName")
         settings.project(":src/$kebabCasedSourceSetName").name = settings.rootProject.name + "-" + kebabCasedSourceSetName
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
      configureTitle()

      if (!containsValidStringProperty("isProjectGroup"))
      {
         throwMissingException("isProjectGroup", "true")
      }

      compositeSearchHeight = compositeSearchHeightCompatibility(logger, ext) // optional w/ default

      checkExcludeFromCompositeBuild()
      checkMaxGradleWorkers()
   }

   fun checkRequiredPropertiesAreSet()
   {
      configureTitle()

      if (!containsValidStringProperty("extraSourceSets"))
      {
         throwMissingException("extraSourceSets", "[] (ex. [\"test\", \"visualizers\"]")
      }
      else
      {
         extraSourceSets = Eval.me(ext.get("extraSourceSets") as String) as ArrayList<String>
      }

      compositeSearchHeight = compositeSearchHeightCompatibility(logger, ext) // optional w/ default

      checkExcludeFromCompositeBuild()
      checkMaxGradleWorkers()
   }

   private fun configureTitle()
   {
      if (containsValidStringProperty("title"))
      {
         settings.rootProject.name = titleToKebabCase(propertyAsString("title"))
      }
      else if (containsValidStringProperty("kebabCasedName") && containsValidStringProperty("pascalCasedName"))
      {
         settings.rootProject.name = propertyAsString("kebabCasedName")
      }
      else
      {
         throwMissingException("title", "Your Project Name")
      }
   }

   private fun checkExcludeFromCompositeBuild()
   {
      if (!containsValidStringProperty("excludeFromCompositeBuild"))
      {
         throwMissingException("excludeFromCompositeBuild", "false (default)")
      }
   }

   private fun checkMaxGradleWorkers()
   {
      if (!ext.has("org.gradle.workers.max"))
      {
         throwMissingException("org.gradle.workers.max", "200")
      }
   }

   private fun containsValidStringProperty(propertyName: String): Boolean
   {
      return ext.has(propertyName) && !(ext.get(propertyName) as String).startsWith("$")
   }

   private fun throwMissingException(propertyName: String, exampleValue: String)
   {
      throw MissingPropertyException("Please set $propertyName = $exampleValue in gradle.properties.")
   }

   private fun propertyAsString(propertyName: String): String
   {
      return (ext.get(propertyName) as String).trim()
   }
}
