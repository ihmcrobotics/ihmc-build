package us.ihmc.build

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.mutable.MutableInt
import org.gradle.api.*
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

lateinit var LogTools: IHMCBuildLogTools

object IHMCBuildTools
{
   fun isProjectGroupCompatibility(rawString: String): Boolean
   {
      return rawString.trim().toLowerCase().contains("true");
   }

   fun kebabCasedNameCompatibility(projectName: String, extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("title", extra))
      {
         return titleToKebabCase(propertyAsString("title", extra))
      }
      else if (containsValidStringProperty("kebabCasedName", extra))
      {
         val kebabCasedName = extra.get("kebabCasedName") as String
         LogTools.info("Loaded kebabCasedName = $kebabCasedName")
         return kebabCasedName
      }
      else
      {
         val defaultValue = toKebabCased(projectName)
         LogTools.info("No value found for kebabCasedName. Using default value: $defaultValue")
         extra.set("kebabCasedName", defaultValue)
         return defaultValue
      }
   }

   fun titleCasedNameCompatibility(projectName: String, extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("title", extra))
      {
         return propertyAsString("title", extra)
      }
      else if (containsValidStringProperty("pascalCasedName", extra))
      {
         return propertyAsString("pascalCasedName", extra)
      }
      else
      {
         return projectName
      }
   }

   fun snapshotModeCompatibility(extra: ExtraPropertiesExtension): Boolean
   {
      if (containsValidStringProperty("snapshotMode", extra))
      {
         val snapshotMode = (extra.get("snapshotMode") as String).trim().toLowerCase().contains("true")
         LogTools.info("Loaded snapshotMode = $snapshotMode")
         return snapshotMode;
      }
      if (extra.has("publishMode") // Backwards compatibility
            && !(extra.get("publishMode") as String).startsWith("$")
            && (extra.get("publishMode") as String).trim().toLowerCase().contains("snapshot"))
      {
         LogTools.warn("Using publishMode = ${(extra.get("publishMode") as String)} to set snapshotMode = true.")
         return true
      }

      return false
   }

   fun publishUrlCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (extra.has("publishMode")) // Backwards compatibility
      {
         LogTools.warn("publishMode has been replaced by publishUrl. See README for details.")
      }
      if (containsValidStringProperty("publishUrl", extra))
      {
         return (extra.get("publishUrl") as String).trim()
      }
      else if (containsValidStringProperty("publishMode", extra)) // Backwards compatibility
      {
         val publishModeString = (extra.get("publishMode") as String).trim().toLowerCase()

         if (publishModeString.contains("local"))
         {
            LogTools.warn("Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = local.")
            return "local"
         }
         else if (publishModeString.contains("snapshot"))
         {
            LogTools.warn("Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = ihmcSnapshots.")
            return "ihmcSnapshots"
         }
         else
         {
            LogTools.warn("Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = ihmcRelease.")
            return "ihmcRelease"
         }
      }
      else
      {
         return "local" // default
      }
   }

   fun publishUrlIsKeyword(publishUrl: String, keyword: String): Boolean
   {
      val sanitized = publishUrl.toLowerCase().replace("-", "")
      return sanitized == keyword
   }

   fun bintrayUsernameCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("bintrayUsername", extra))
      {
         return propertyAsString("bintrayUsername", extra)
      }
      else if (containsValidStringProperty("bintray_user", extra))
      {
         LogTools.quiet("Please set bintrayUsername = <username> in ~/.gradle/gradle.properties.")
         return propertyAsString("bintray_user", extra)
      }
      else
      {
         LogTools.info("Please set bintrayUsername = <username> in ~/.gradle/gradle.properties.")
         return "unset"
      }
   }

   fun bintrayApiKeyCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("bintrayApiKey", extra))
      {
         return propertyAsString("bintrayApiKey", extra)
      }
      else if (containsValidStringProperty("bintray_key", extra))
      {
         LogTools.quiet("Please set bintrayApiKey = <key> in ~/.gradle/gradle.properties.")
         return propertyAsString("bintray_key", extra)
      }
      else
      {
         LogTools.info("Please set bintrayApiKey = <key> in ~/.gradle/gradle.properties.")
         return "unset"
      }
   }

   fun compositeSearchHeightCompatibility(extra: ExtraPropertiesExtension): Int
   {
      if (containsValidStringProperty("compositeSearchHeight", extra))
      {
         return (extra.get("compositeSearchHeight") as String).toInt()
      }
      else if (containsValidStringProperty("depthFromWorkspaceDirectory", extra))
      {
         return (extra.get("depthFromWorkspaceDirectory") as String).toInt()
      }
      else
      {
         val defaultValue = 0
         LogTools.info("No value found for compositeSearchHeight. Using default value: $defaultValue")
         extra.set("compositeSearchHeight", defaultValue)
         return defaultValue
      }
   }

   fun ciDatabaseUrlCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("ciDatabaseUrl", extra))
      {
         return (extra.get("ciDatabaseUrl") as String).trim()
      }
      else
      {
         val defaultValue = ""
         LogTools.info("No value found for ciDatabaseUrl. Using default value: $defaultValue")
         extra.set("ciDatabaseUrl", defaultValue)
         return defaultValue
      }
   }

   fun artifactoryUrlCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("artifactoryUrl", extra))
      {
         return (extra.get("artifactoryUrl") as String).trim()
      }
      else
      {
         val defaultValue = "https://artifactory.ihmc.us"
         LogTools.info("No value found for artifactoryUrl. Using default value: $defaultValue")
         extra.set("artifactoryUrl", defaultValue)
         return defaultValue
      }
   }

   fun compatibilityVersionCompatibility(extra: ExtraPropertiesExtension): String
   {
      if (containsValidStringProperty("compatibilityVersion", extra))
      {
         return (extra.get("compatibilityVersion") as String).trim()
      }
      else
      {
         val defaultValue = "CURRENT"
         LogTools.info("No value found for compatibilityVersion. Using default value: $defaultValue")
         extra.set("compatibilityVersion", defaultValue)
         return defaultValue
      }
   }

   fun containsValidStringProperty(propertyName: String, extra: ExtraPropertiesExtension): Boolean
   {
      return extra.has(propertyName) && !(extra.get(propertyName) as String).startsWith("$")
   }

   fun propertyAsString(propertyName: String, extra: ExtraPropertiesExtension): String
   {
      return (extra.get(propertyName) as String).trim()
   }

   fun titleToKebabCase(titleCased: String): String
   {
      return titleCased.trim().toLowerCase().replace(Regex("\\s+"), "-")
   }

   fun titleToPascalCase(titleCased: String): String
   {
      return titleCased.trim().replace(Regex("\\s+"), "")
   }

   fun toSourceSetName(subproject: Project): String
   {
      return toKebabCased(subproject.name.substringAfter(subproject.parent!!.name + "-"))
   }

   fun kebabToPascalCase(kebabCased: String): String
   {
      val split = kebabCased.split("-")
      var pascalCased = ""
      for (section in split)
      {
         pascalCased += section.capitalize()
      }
      return pascalCased
   }

   fun kebabToCamelCase(kebabCased: String): String
   {
      val split = kebabCased.split("-")
      var camelCased = ""
      if (split.size > 0)
      {
         camelCased += split[0].decapitalize()
      }
      for (i in 1 until split.size)
      {
         camelCased += split[i].capitalize()
      }
      return camelCased
   }

   fun toKebabCased(anyCased: String): String
   {
      var kebabCased = toPreKababWithBookendHandles(anyCased);

      kebabCased = kebabCased.substring(1, kebabCased.length - 1);

      return kebabCased;
   }

   fun toPreKababWithBookendHandles(anyCased: String): String
   {
      val parts = ArrayList<String>();
      var part = "";

      for (i in 0 until anyCased.length)
      {
         var character = anyCased[i].toString();
         if (StringUtils.isAllUpperCase(character) || StringUtils.isNumeric(character))
         {
            if (!part.isEmpty())
            {
               parts.add(part.toLowerCase());
            }
            part = character;
         }
         else
         {
            part += character;
         }
      }
      if (!part.isEmpty())
      {
         parts.add(part.toLowerCase());
      }

      var kebab = "";
      for (i in 0 until parts.size)
      {
         kebab += '-';
         kebab += parts.get(i);
      }
      kebab += '-';

      return kebab;
   }

   fun parseDependenciesFromGradleKtsFile(buildFile: Path): SortedSet<String>
   {
      LogTools.debug("Parsing build file: ${buildFile.toAbsolutePath().normalize()} ")
      val dependencySet = TreeSet<String>()

      val fileAsString = String(Files.readAllBytes(buildFile))

      val pattern = Regex("ependencies[ \\t\\x0B\\S]*\\{").toPattern()
      val matcher = pattern.matcher(fileAsString);

      while (matcher.find())
      {
         val end = matcher.end()

         val indexAfterEndBracket = matchingBracket(fileAsString.substring(end), MutableInt(0))

         val dependencyBlockString = "   " + fileAsString.substring(end, end + indexAfterEndBracket - 1).trim()

         LogTools.debug("Matched: $dependencyBlockString ")
         dependencySet.addAll(extractDependencyArtifactNames(dependencyBlockString))
      }

      return dependencySet
   }

   fun extractDependencyArtifactNames(dependencyBlockString: String): SortedSet<String>
   {
      val artifactNames = TreeSet<String>()

      val pattern = Regex("(compile|implementation|api|runtime)[ \\t\\x0B]*\\([ \\t\\x0B]*\\\"[\\s\\-\\w\\.]+:[\\s\\:\\-\\w\\.]+\\\"").toPattern()
      val matcher = pattern.matcher(dependencyBlockString);
      while (matcher.find())
      {
         val match = matcher.toMatchResult().group()
         val artifactName = match.split(":")[1]

         artifactNames.add(artifactName)
      }

      return artifactNames
   }

   fun matchingBracket(string: String, i: MutableInt): Int
   {
      while (i.value < string.length)
      {
         if (string[i.value] == '{')
         {
            i.increment()
            i.setValue(matchingBracket(string, i))
         }
         if (string[i.value] == '}')
         {
            return i.value + 1
         }

         i.increment()
      }

      throw GradleException("No end bracket for dependencies block")
   }

   fun defineDynamicCompositeTask(project: Project)
   {
      if (project.hasProperty("taskName") || project.hasProperty("taskNames"))
      {
         val taskNames = arrayListOf<String>()

         if (project.hasProperty("taskName"))
         {
            taskNames.addAll((project.property("taskName") as String).split(","))
         }

         if (project.hasProperty("taskNames"))
         {
            taskNames.addAll((project.property("taskNames") as String).split(","))
         }

         if (taskNames.size > 0)
         {
            defineDeepCompositeTask("compositeTask", taskNames, project)
         }
      }
   }

   fun defineDeepCompositeTask(compositeTaskName: String, targetTaskName: String, project: Project): Task
   {
      return defineDeepCompositeTask(compositeTaskName, arrayListOf(targetTaskName), project)
   }

   /**
    * Create a task that runs the target tasks in both this project and the included
    * projects as well. Since we can't reference subprojects of composite included builds,
    * we must use a two layer hierarchy.
    */
   fun defineDeepCompositeTask(compositeTaskName: String, targetTaskNames: ArrayList<String>, project: Project): Task
   {
      return project.tasks.create(compositeTaskName) {
         // Configure every project to have a "handle" task that depends on all the task names
         project.allprojects.forEach { allproject ->
            targetTaskNames.forEach { targetTaskName ->

               // Configure every project to have at least an empty task with the names
               if (allproject.tasks.findByPath(targetTaskName) == null)
               {
                  configureEmptySubprojectTask(allproject, targetTaskName)
               }

               dependsOn(allproject.tasks.getByName(targetTaskName))
            }
         }

         // For the build root, make the global task depend on all the included build global tasks
         if (isBuildRoot(project))
         {
            project.gradle.includedBuilds.forEach {
               dependsOn(it.task(":$compositeTaskName"))
            }
         }
      }
   }

   /**
    * Declare empty task if it doesn't exist so it can be called without errors
    */
   fun configureEmptySubprojectTask(subproject: Project, targetTaskName: String)
   {
      try
      {
         LogTools.info("${subproject.name}: Declaring empty task: $targetTaskName")
         subproject.tasks.create(targetTaskName)
      }
      catch (e: InvalidUserDataException)
      {
         // if the task exists or something else goes wrong
         LogTools.error("${subproject.name}: InvalidUserDataException: ${e.message}: $targetTaskName")
      }
   }

   fun enforceMultiprojectTaskDependency(taskName: String, project: Project)
   {
      project.tasks.getByName(taskName) {
         project.subprojects.forEach { subproject ->
            dependsOn(subproject.tasks.getByPath(taskName))
         }
      }
   }

   fun isBuildRoot(project: Project): Boolean
   {
      return project.gradle.parent == null
   }
}
