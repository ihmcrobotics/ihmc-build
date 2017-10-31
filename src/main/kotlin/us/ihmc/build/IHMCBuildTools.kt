package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import us.ihmc.commons.nio.FileTools
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList

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

fun ihmcBuildMessage(message: Any): String
{
   return "[ihmc-build] " + message
}

fun writeProjectSettingsFile(logger: Logger, directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   logQuiet(logger, "Generating project file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/project_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun writeGroupSettingsFile(logger: Logger, directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   logQuiet(logger, "Generating group file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/group_settings.gradle").readText()
   settingsFile.writeText(fileContent)
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