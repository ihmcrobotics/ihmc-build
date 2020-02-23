package us.ihmc.ci;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GradleSubBuildTools
{
   public static String buildsDir = "builds/";
   public static String gradleCommand = SystemUtils.IS_OS_WINDOWS ? "gradlew.bat" : "gradle";
   public static String gradleExe = Paths.get(buildsDir + gradleCommand).toAbsolutePath().toString();

   public static String runCommand(String command, Path workingDir)
   {
      try
      {
         String[] parts = command.split("\\s");
         Process processBuilder = new ProcessBuilder(parts).directory(workingDir.toFile())
                                                           .redirectOutput(ProcessBuilder.Redirect.PIPE)
                                                           .redirectError(ProcessBuilder.Redirect.PIPE)
                                                           .start();

         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         TeeOutputStream teeOutputStream = new TeeOutputStream(System.out, byteArrayOutputStream);
         TeeInputStream teeSysOut = new TeeInputStream(processBuilder.getInputStream(), teeOutputStream);
         TeeInputStream teeSysErr = new TeeInputStream(processBuilder.getErrorStream(), teeOutputStream);
         while (processBuilder.isAlive())
         {
            readAllFromTee(teeSysOut, teeSysErr);
         }
         readAllFromTee(teeSysOut, teeSysErr);

         String output = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
         return output;
      }
      catch (IOException | InterruptedException e)
      {
         e.printStackTrace();
         return null;
      }
   }

   private static void readAllFromTee(TeeInputStream teeSysOut, TeeInputStream teeSysErr) throws IOException, InterruptedException
   {
      while (teeSysOut.read() > -1 || teeSysErr.read() > -1)
         ;
      Thread.sleep(200);
   }

   public static String runGradleTask(String command, String project)
   {
      String commandString = gradleExe;
      if (command != null && !command.isEmpty())
      {
         commandString += " " + command;
      }

      System.out.println("Running " + commandString + " in " + Paths.get(buildsDir + project).toAbsolutePath());

      return runCommand(gradleExe + " " + command, Paths.get(buildsDir + project).toAbsolutePath());
   }
}
