package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.mutable.MutableInt
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import us.ihmc.commons.nio.FileTools
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern

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
      val dependencySet = TreeSet<String>()

      val fileAsString = String(Files.readAllBytes(buildFile))

      val pattern = Pattern.compile("ependencies[ \\t\\x0B\\S]*\\{")
      val matcher = pattern.matcher(fileAsString);

      while (matcher.find())
      {
         val end = matcher.end()

         val indexAfterEndBracket = matchingBracket(fileAsString.substring(end), MutableInt(0))

         val dependencyBlockString = "   " + fileAsString.substring(end, end + indexAfterEndBracket - 1).trim()

         println(dependencyBlockString)

         // add dependencies
      }

      return dependencySet
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

   /**
    * Temporary tool for converting projects to new folder structure quickly.
    */
   fun moveSourceFolderToMavenStandard(projectDir: Path, sourceSetName: String)
   {
      val oldSourceFolder: Path
      if (sourceSetName == "main")
      {
         oldSourceFolder = projectDir.resolve("src")
      }
      else
      {
         oldSourceFolder = projectDir.resolve(sourceSetName)
      }
      val mavenFolder = projectDir.resolve("src").resolve(sourceSetName).resolve("java")
      moveAPackage(oldSourceFolder, mavenFolder, "us")
      moveAPackage(oldSourceFolder, mavenFolder, "optiTrack")
   }

   private fun moveAPackage(oldSourceFolder: Path, mavenFolder: Path, packageName: String)
   {
      if (Files.exists(oldSourceFolder))
      {
         val oldUs = oldSourceFolder.resolve(packageName)
         val newUs = mavenFolder.resolve(packageName)

         if (Files.exists(oldUs))
         {
            LogTools.quiet(mavenFolder)
            LogTools.quiet(oldUs)
            LogTools.quiet(newUs)

            FileTools.deleteQuietly(newUs)

            try
            {
               FileUtils.moveDirectory(oldUs.toFile(), newUs.toFile())
            }
            catch (e: Exception)
            {
               LogTools.trace(e.stackTrace)
            }
         }
      }
   }

   /**
    * Temporary tool for converting projects to new folder structure quickly.
    */
   fun revertSourceFolderFromMavenStandard(projectDir: Path, sourceSetName: String)
   {
      val oldSourceFolder: Path
      if (sourceSetName == "main")
      {
         oldSourceFolder = projectDir.resolve("src")
      }
      else
      {
         oldSourceFolder = projectDir.resolve(sourceSetName)
      }
      val mavenFolder = projectDir.resolve("src").resolve(sourceSetName).resolve("java")
      if (Files.exists(oldSourceFolder))
      {
         revertAPackage(oldSourceFolder, mavenFolder, "us")
         revertAPackage(oldSourceFolder, mavenFolder, "optiTrack")
      }
      else
      {
         LogTools.warn("File not exist: $oldSourceFolder")
      }
   }

   private fun revertAPackage(oldSourceFolder: Path, mavenFolder: Path, packageName: String)
   {
      val oldUs = oldSourceFolder.resolve(packageName)
      val newUs = mavenFolder.resolve(packageName)

      if (Files.exists(newUs))
      {
         LogTools.quiet(mavenFolder)
         LogTools.quiet(oldUs)
         LogTools.quiet(newUs)

         FileTools.deleteQuietly(oldUs)

         try
         {
            FileUtils.moveDirectory(newUs.toFile(), oldUs.toFile())
         }
         catch (e: Exception)
         {
            LogTools.trace(e.stackTrace)
         }
      }
      else
      {
         LogTools.warn("File not exist: $oldUs")
      }
   }

   fun isBuildRoot(project: Project): Boolean
   {
      return project.gradle.parent == null
   }
}
