package us.ihmc.build

import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

val gradleCommand = if (SystemUtils.IS_OS_WINDOWS) "gradlew.bat" else "gradlew"
val gradleExe = Paths.get("tests/$gradleCommand").toAbsolutePath().toString()

fun runGradleTask(command: String?, project: String): String
{
   if (command == null || command.isEmpty())
      return runCommand("$gradleExe", Paths.get("tests/$project").toAbsolutePath())
   else
      return runCommand("$gradleExe $command", Paths.get("tests/$project").toAbsolutePath())
}

fun runCommand(command: String, workingDir: Path): String
{
   try
   {
      val parts = command.split("\\s".toRegex())
      val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
      
      proc.waitFor(60, TimeUnit.MINUTES)
      val s = proc.inputStream.bufferedReader().readText() + proc.errorStream.bufferedReader().readText()
      println(s)
      return s
   }
   catch (e: IOException)
   {
      e.printStackTrace()
      return ""
   }
}
