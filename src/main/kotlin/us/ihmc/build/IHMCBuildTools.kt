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

fun kebabCasedNameCompatibility(projectName: String, logger: Logger, ext: ExtraPropertiesExtension): String
{
   if (ext.has("kebabCasedName") && !(ext.get("kebabCasedName") as String).startsWith("$"))
   {
      val kebabCasedName = ext.get("kebabCasedName") as String
      logInfo(logger, "Loaded kebabCasedName = $kebabCasedName")
      return kebabCasedName
   }
   else if (ext.has("hyphenatedName") && !(ext.get("hyphenatedName") as String).startsWith("$"))
   {

      val kebabCasedName = ext.get("hyphenatedName") as String
      logInfo(logger, "Loaded kebabCasedName = $kebabCasedName")
      return kebabCasedName
   }
   else
   {
      val defaultValue = toKebabCased(projectName)
      logInfo(logger, "No value found for kebabCasedName. Using default value: $defaultValue")
      ext.set("kebabCasedName", defaultValue)
      return defaultValue
   }
}

fun snapshotModeCompatibility(logger: Logger, ext: ExtraPropertiesExtension): Boolean
{
   if (ext.has("snapshotMode") && !(ext.get("snapshotMode") as String).startsWith("$"))
   {
      val snapshotMode = (ext.get("snapshotMode") as String).trim().toLowerCase().contains("true")
      logInfo(logger, "Loaded snapshotMode = $snapshotMode")
      return snapshotMode;
   }
   if (ext.has("publishMode") // Backwards compatibility
         && !(ext.get("publishMode") as String).startsWith("$")
         && (ext.get("publishMode") as String).trim().toLowerCase().contains("snapshot"))
   {
      logWarn(logger, "Using publishMode = ${(ext.get("publishMode") as String)} to set snapshotMode = true.")
      return true
   }
   
   return false
}

fun publishUrlCompatibility(logger: Logger, ext: ExtraPropertiesExtension): String
{
   if (ext.has("publishMode")) // Backwards compatibility
   {
      logWarn(logger, "publishMode has been replaced by publishUrl. See README for details.")
   }
   if (ext.has("publishUrl") && !(ext.get("publishUrl") as String).startsWith("$"))
   {
      return (ext.get("publishUrl") as String).trim()
   }
   else if (ext.has("publishMode") && !(ext.get("publishMode") as String).startsWith("$")) // Backwards compatibility
   {
      val publishModeString = (ext.get("publishMode") as String).trim().toLowerCase()
      
      if (publishModeString.contains("local"))
      {
         logWarn(logger, "Using publishMode = ${(ext.get("publishMode") as String)} to set publishUrl = local.")
         return "local"
      }
      else if (publishModeString.contains("snapshot"))
      {
         logWarn(logger, "Using publishMode = ${(ext.get("publishMode") as String)} to set publishUrl = ihmcSnapshots.")
         return "ihmcSnapshots"
      }
      else
      {
         logWarn(logger, "Using publishMode = ${(ext.get("publishMode") as String)} to set publishUrl = ihmcRelease.")
         return "ihmcRelease"
      }
   }
   else
   {
      throw MissingPropertyException("Please set publishUrl = local (default) in gradle.properties.")
   }
}

fun compositeSearchHeightCompatibility(logger: Logger, ext: ExtraPropertiesExtension): Int
{
   if (ext.has("compositeSearchHeight") && !(ext.get("compositeSearchHeight") as String).startsWith("$"))
   {
      return (ext.get("compositeSearchHeight") as String).toInt()
   }
   else if (ext.has("depthFromWorkspaceDirectory") && !(ext.get("depthFromWorkspaceDirectory") as String).startsWith("$"))
   {
      return (ext.get("depthFromWorkspaceDirectory") as String).toInt()
   }
   else
   {
      val defaultValue = 0
      logInfo(logger, "No value found for compositeSearchHeight. Using default value: $defaultValue")
      ext.set("compositeSearchHeight", defaultValue)
      return defaultValue
   }
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
      pascalCased += StringUtils.capitalize(section)
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
      camelCased += StringUtils.capitalize(split[i])
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