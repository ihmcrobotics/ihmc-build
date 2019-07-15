package us.ihmc.build

import groovy.lang.MissingPropertyException
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import us.ihmc.commons.nio.FileTools
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun logQuiet(logger: Logger, message: Any)
{
   logger.quiet(ihmcBuildMessage(message))
}

fun logInfo(logger: Logger, message: Any)
{
   logger.info(ihmcBuildMessage(message))
}

fun logWarn(logger: Logger, message: Any)
{
   logger.warn(ihmcBuildMessage(message))
}

fun logError(logger: Logger, message: Any)
{
   logger.error(ihmcBuildMessage(message))
}

fun logDebug(logger: Logger, message: Any)
{
   logger.debug(ihmcBuildMessage(message))
}

fun logTrace(logger: Logger, trace: Any)
{
   logger.trace(trace.toString())
}

fun hardCrash(logger: Logger,  message: Any)
{
   logError(logger, message)
   throw GradleException("[ihmc-build] " + message as String)
}

fun ihmcBuildMessage(message: Any): String
{
   return "[ihmc-build] " + message
}

fun isProjectGroupCompatibility(rawString: String): Boolean
{
   return rawString.trim().toLowerCase().contains("true");
}

fun kebabCasedNameCompatibility(projectName: String, logger: Logger, extra: ExtraPropertiesExtension): String
{
   if (containsValidStringProperty("kebabCasedName", extra))
   {
      val kebabCasedName = extra.get("kebabCasedName") as String
      logInfo(logger, "Loaded kebabCasedName = $kebabCasedName")
      return kebabCasedName
   }
   else
   {
      val defaultValue = toKebabCased(projectName)
      logInfo(logger, "No value found for kebabCasedName. Using default value: $defaultValue")
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
   else if (containsValidStringProperty("pascalCasedName",  extra))
   {
      return propertyAsString("pascalCasedName", extra)
   }
   else
   {
      return projectName
   }
}

fun snapshotModeCompatibility(logger: Logger, extra: ExtraPropertiesExtension): Boolean
{
   if (containsValidStringProperty("snapshotMode", extra))
   {
      val snapshotMode = (extra.get("snapshotMode") as String).trim().toLowerCase().contains("true")
      logInfo(logger, "Loaded snapshotMode = $snapshotMode")
      return snapshotMode;
   }
   if (extra.has("publishMode") // Backwards compatibility
         && !(extra.get("publishMode") as String).startsWith("$")
         && (extra.get("publishMode") as String).trim().toLowerCase().contains("snapshot"))
   {
      logWarn(logger, "Using publishMode = ${(extra.get("publishMode") as String)} to set snapshotMode = true.")
      return true
   }
   
   return false
}

fun publishUrlCompatibility(logger: Logger, extra: ExtraPropertiesExtension): String
{
   if (extra.has("publishMode")) // Backwards compatibility
   {
      logWarn(logger, "publishMode has been replaced by publishUrl. See README for details.")
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
         logWarn(logger, "Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = local.")
         return "local"
      }
      else if (publishModeString.contains("snapshot"))
      {
         logWarn(logger, "Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = ihmcSnapshots.")
         return "ihmcSnapshots"
      }
      else
      {
         logWarn(logger, "Using publishMode = ${(extra.get("publishMode") as String)} to set publishUrl = ihmcRelease.")
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

fun bintrayUsernameCompatibility(logger: Logger, extra: ExtraPropertiesExtension): String
{
   if (containsValidStringProperty("bintrayUsername", extra))
   {
      return propertyAsString("bintrayUsername", extra)
   }
   else if (containsValidStringProperty("bintray_user", extra))
   {
      logQuiet(logger, "Please set bintrayUsername = <username> in ~/.gradle/gradle.properties.")
      return propertyAsString("bintray_user", extra)
   }
   else
   {
      logInfo(logger, "Please set bintrayUsername = <username> in ~/.gradle/gradle.properties.")
      return "unset"
   }
}

fun bintrayApiKeyCompatibility(logger: Logger, extra: ExtraPropertiesExtension): String
{
   if (containsValidStringProperty("bintrayApiKey", extra))
   {
      return propertyAsString("bintrayApiKey", extra)
   }
   else if (containsValidStringProperty("bintray_key", extra))
   {
      logQuiet(logger, "Please set bintrayApiKey = <key> in ~/.gradle/gradle.properties.")
      return propertyAsString("bintray_key", extra)
   }
   else
   {
      logInfo(logger, "Please set bintrayApiKey = <key> in ~/.gradle/gradle.properties.")
      return "unset"
   }
}

fun compositeSearchHeightCompatibility(logger: Logger, extra: ExtraPropertiesExtension): Int
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
      logInfo(logger, "No value found for compositeSearchHeight. Using default value: $defaultValue")
      extra.set("compositeSearchHeight", defaultValue)
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

fun toPascalCased(anyCased: String): String
{
   val split = anyCased.split("-")
   var pascalCased = ""
   for (section in split)
   {
      pascalCased += section.capitalize()
   }
   return pascalCased
}

fun toCamelCased(anyCased: String): String
{
   val split = anyCased.split("-")
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

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun moveSourceFolderToMavenStandard(logger: Logger, projectDir: Path, sourceSetName: String)
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
   moveAPackage(logger, oldSourceFolder, mavenFolder, "us")
   moveAPackage(logger, oldSourceFolder, mavenFolder, "optiTrack")
}

private fun moveAPackage(logger: Logger, oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   if (Files.exists(oldSourceFolder))
   {
      val oldUs = oldSourceFolder.resolve(packageName)
      val newUs = mavenFolder.resolve(packageName)
      
      if (Files.exists(oldUs))
      {
         logQuiet(logger, mavenFolder)
         logQuiet(logger, oldUs)
         logQuiet(logger, newUs)
         
         FileTools.deleteQuietly(newUs)
         
         try
         {
            FileUtils.moveDirectory(oldUs.toFile(), newUs.toFile())
         }
         catch (e: Exception)
         {
            logTrace(logger, e.stackTrace)
         }
      }
   }
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun revertSourceFolderFromMavenStandard(logger: Logger, projectDir: Path, sourceSetName: String)
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
      revertAPackage(logger, oldSourceFolder, mavenFolder, "us")
      revertAPackage(logger, oldSourceFolder, mavenFolder, "optiTrack")
   }
   else
   {
      logWarn(logger, "File not exist: $oldSourceFolder")
   }
}

private fun revertAPackage(logger: Logger, oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   val oldUs = oldSourceFolder.resolve(packageName)
   val newUs = mavenFolder.resolve(packageName)
   
   if (Files.exists(newUs))
   {
      logQuiet(logger, mavenFolder)
      logQuiet(logger, oldUs)
      logQuiet(logger, newUs)
      
      FileTools.deleteQuietly(oldUs)
      
      try
      {
         FileUtils.moveDirectory(newUs.toFile(), oldUs.toFile())
      }
      catch (e: Exception)
      {
         logTrace(logger, e.stackTrace)
      }
   }
   else
   {
      logWarn(logger,"File not exist: $oldUs")
   }
}

fun isBuildRoot(project: Project): Boolean
{
   return project.gradle.parent == null
}