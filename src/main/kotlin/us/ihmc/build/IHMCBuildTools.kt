package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import us.ihmc.commons.nio.FileTools
import java.io.File
import java.nio.file.Files
import java.util.ArrayList

fun writeProjectSettingsFile(directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   println("[ihmc-build] Generating project file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/project_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun writeGroupSettingsFile(directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   println("[ihmc-build] Generating group file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/group_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun toPascalCased(hyphenated: String): String
{
   val split = hyphenated.split("-")
   var pascalCased = ""
   for (section in split)
   {
      pascalCased += StringUtils.capitalize(section)
   }
   return pascalCased
}

fun toHyphenated(pascalCased: String): String
{
   var hyphenated = pascalCasedToPrehyphenated(pascalCased);
   
   hyphenated = hyphenated.substring(1, hyphenated.length - 1);
   
   return hyphenated;
}

fun pascalCasedToPrehyphenated(pascalCased: String): String
{
   val parts = ArrayList<String>();
   var part = "";
   
   for (i in 0..pascalCased.length)
   {
      var character = pascalCased[i].toString();
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
   
   var hyphenated = "";
   for (i in 0..parts.size)
   {
      hyphenated += '-';
      hyphenated += parts.get(i);
   }
   hyphenated += '-';
   
   return hyphenated;
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun moveSourceFolderToMavenStandard(projectDir: File, sourceFolder: String)
{
   val subprojectFolder = projectDir.toPath().resolve(sourceFolder)
   val mavenFolder = projectDir.toPath().resolve("src").resolve(sourceFolder).resolve("java")
   if (Files.exists(subprojectFolder))
   {
      val sourceSetMavenUsFolder = mavenFolder.resolve("us")
      val sourceSetUsFolder = subprojectFolder.resolve("us")
      
      if (Files.exists(sourceSetUsFolder))
      {
         printQuiet(mavenFolder)
         printQuiet(sourceSetMavenUsFolder)
         printQuiet(sourceSetUsFolder)
         
         FileTools.deleteQuietly(sourceSetMavenUsFolder)
         
         try
         {
            FileUtils.moveDirectory(sourceSetUsFolder.toFile(), sourceSetMavenUsFolder.toFile())
         }
         catch (e: Exception)
         {
            printQuiet("Failed: " + e.printStackTrace())
         }
      }
   }
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun copyTestsForProject(projectDir: File, sourceFolder: String)
{
   val subprojectFolder = projectDir.toPath().resolve(sourceFolder)
   if (Files.exists(subprojectFolder))
   {
      val testSrcFolder = subprojectFolder.resolve("src")
      val testSrcUsFolder = testSrcFolder.resolve("us")
      val testUsFolder = subprojectFolder.resolve("us")
      
      if (Files.exists(testUsFolder))
      {
         printQuiet(testSrcFolder)
         printQuiet(testSrcUsFolder)
         printQuiet(testUsFolder)
         
         FileTools.deleteQuietly(testSrcUsFolder)
         
         try
         {
            FileUtils.copyDirectory(testUsFolder.toFile(), testSrcUsFolder.toFile())
         }
         catch (e: Exception)
         {
            printQuiet("Failed: " + e.printStackTrace())
         }
      }
   }
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun deleteTestsFromProject(projectDir: File, sourceFolder: String)
{
   val subprojectFolder = projectDir.toPath().resolve(sourceFolder)
   if (Files.exists(subprojectFolder))
   {
      val testSrcFolder = subprojectFolder.resolve("src")
      val testSrcUsFolder = testSrcFolder.resolve("us")
      val testUsFolder = subprojectFolder.resolve("us")
      
      if (Files.exists(testUsFolder))
      {
         printQuiet(testSrcFolder)
         printQuiet(testSrcUsFolder)
         printQuiet(testUsFolder)
         
         FileTools.deleteQuietly(testSrcUsFolder)
      }
   }
}

fun printQuiet(message: Any)
{
   println("[ihmc-build] " + message)
}