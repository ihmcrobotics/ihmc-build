package us.ihmc.build

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

val gradleExe = getLocalGradlePath()

private fun getLocalGradlePath(): String
{
   val process = Runtime.getRuntime().exec(if (System.getProperty("os.name").contains("Windows")) "where gradle" else "which gradle")
   val reader = BufferedReader(InputStreamReader(process.inputStream))
   val path = reader.readLine()
   println("Gradle path: $path")
   return path;
}

fun runGradleTask(command: String?, project: String): String
{
   return if (command.isNullOrEmpty())
      runCommand(gradleExe, Paths.get("tests/$project").toAbsolutePath())
   else
      runCommand("$gradleExe $command", Paths.get("tests/$project").toAbsolutePath())
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
