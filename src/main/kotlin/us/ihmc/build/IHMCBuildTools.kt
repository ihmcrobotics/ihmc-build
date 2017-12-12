package us.ihmc.build

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.http.options.Options
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import us.ihmc.commons.nio.FileTools
import us.ihmc.commons.thread.ThreadTools
import java.io.File
import java.io.IOException
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

fun ihmcBuildMessage(message: Any): String
{
   return "[ihmc-build] " + message
}

fun kebabCasedNameCompatibility(projectName: String, logger: Logger, ext: ExtraPropertiesExtension): String
{
   if (ext.has("kebabCasedName") && !(ext.get("kebabCasedName") as String).startsWith("$"))
   {
      return ext.get("kebabCasedName") as String
   }
   else if (ext.has("hyphenatedName") && !(ext.get("hyphenatedName") as String).startsWith("$"))
   {
      return ext.get("hyphenatedName") as String
   }
   else
   {
      val defaultValue = toKebabCased(projectName)
      logInfo(logger, "No value found for kebabCasedName. Using default value: $defaultValue")
      ext.set("kebabCasedName", defaultValue)
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

fun isBuildRoot(project: Project): Boolean
{
   return project.gradle.startParameter.isSearchUpwards
}

fun requestGlobalBuildNumberFromCIDatabase(logger: Logger, buildKey: String): String
{
   var tryCount = 0
   var globalBuildNumber = "ERROR"
   while (tryCount < 5 && globalBuildNumber == "ERROR")
   {
      globalBuildNumber = tryGlobalBuildNumberRequest(logger, buildKey)
      tryCount++
      logInfo(logger, "Global build number for $buildKey: $globalBuildNumber")
   }
   
   return globalBuildNumber.toString()
}

private fun tryGlobalBuildNumberRequest(logger: Logger, buildKey: String): String
{
   try
   {
      return Unirest.get("http://alcaniz.ihmc.us:8087").queryString("globalBuildNumber", buildKey).asString().getBody()
   }
   catch (e: UnirestException)
   {
      logInfo(logger, "Failed to retrieve global build number. Trying again... " + e.message)
      ThreadTools.sleep(100)
      try
      {
         Unirest.shutdown();
         Options.refresh();
      }
      catch (ioException: IOException)
      {
         ioException.printStackTrace();
      }
      return "ERROR"
   }
}