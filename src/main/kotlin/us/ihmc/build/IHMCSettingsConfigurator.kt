package us.ihmc.build

import groovy.lang.MissingPropertyException
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension

class IHMCSettingsConfigurator(val settings: Settings, val logger: Logger, ext: ExtraPropertiesExtension)
{
   val properties = IHMCPropertyInterface().initWithExt(ext, settings.rootProject.name)
   
   init
   {
      logInfo(logger, "Evaluating " + settings.rootProject.projectDir.toPath().fileName.toString() + " settings.gradle")
      
      if (settings.gradle.gradleVersion.compareTo("4.0") < 0)
      {
         hardCrash(logger, "Please upgrade to Gradle version 4.1 or higher! (Recommended versions: 4.1, 4.2.1, or later)")
      }
   
      if (!properties.has("org.gradle.workers.max"))
      {
         throw MissingPropertyException("Please set org.gradle.workers.max = 200 in GRADLE_USER_HOME/gradle.properties.")
      }
   }
   
   fun evaluate()
   {
      for (module in modulesCompatibility(logger, properties))
      {
         module.evaluate(logger, settings, properties)
      }
   }
   
   fun findAndIncludeCompositeBuilds()
   {
      if (isBuildRoot(settings.startParameter))
      {
         val compositeBuildAssembler = IHMCCompositeBuildAssembler(this)
         for (buildToInclude in compositeBuildAssembler.findCompositeBuilds())
         {
            settings.includeBuild(buildToInclude)
         }
      }
   }
   
   @Deprecated("Will be removed in 0.15.")
   fun configureAsGroupOfProjects()
   {
      deprecationMessage(logger, "In ${settings.rootProject.name} settings.gradle, \"configureAsGroupOfProjects(?)\"", "0.15", "Please use evaluate() instead.")
      evaluate()
   }
   
   @Deprecated("Will be removed in 0.15.")
   fun checkRequiredPropertiesAreSet()
   {
      deprecationMessage(logger, "In ${settings.rootProject.name} settings.gradle, \"checkRequiredPropertiesAreSet(?)\"", "0.15", "Please use evaluate() instead.")
      evaluate()
   }
   
   @Deprecated("Will be removed in 0.15.")
   fun configureProjectName(dummy: Any?)
   {
      deprecationMessage(logger, "In ${settings.rootProject.name} settings.gradle, \"configureProjectName(?)\"", "0.15", "Please just remove this call. It does nothing.")
   }
   
   @Deprecated("Use configureModules() instead.")
   fun configureExtraSourceSets(vararg dummy: Any?)
   {
      deprecationMessage(logger, "In ${settings.rootProject.name} settings.gradle, \"configureExtraSourceSets(?)\"", "0.15", "Please just remove this call. It does nothing.")
   }
}
