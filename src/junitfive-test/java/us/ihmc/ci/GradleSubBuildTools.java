package us.ihmc.ci;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GradleSubBuildTools
{
   public static String buildsDir = "builds/";
   public static String gradleCommand = SystemUtils.IS_OS_WINDOWS ? "gradlew.bat" : "gradlew";
   public static String gradleExe = Paths.get(buildsDir + gradleCommand).toAbsolutePath().toString();

   public static String runCommand(String command, Path workingDir)
   {
      try
      {
         String[] parts = command.split("\\s");
         Process proc = new ProcessBuilder(parts)
               .directory(workingDir.toFile())
               .redirectOutput(ProcessBuilder.Redirect.PIPE)
               .redirectError(ProcessBuilder.Redirect.PIPE)
               .start();

         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         TeeOutputStream teeOutputStream = new TeeOutputStream(System.out, baos);
         TeeInputStream teeSysOut = new TeeInputStream(proc.getInputStream(), teeOutputStream);
         TeeInputStream teeSysErr = new TeeInputStream(proc.getErrorStream(), teeOutputStream);
         while (proc.isAlive())
         {
            readAllFromTee(teeSysOut, teeSysErr);
         }
         readAllFromTee(teeSysOut, teeSysErr);

         String output = baos.toString(Charset.forName("UTF-8"));
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
      while (teeSysOut.read() > -1 || teeSysErr.read() > -1);
      Thread.sleep(200);
   }

   public static String runGradleTask(String command, String project)
   {
      System.out.println("Running " + gradleExe + " in " + Paths.get(buildsDir + project).toAbsolutePath());
      if (command == null || command.isEmpty())
         return runCommand(gradleExe, Paths.get("builds/" + project).toAbsolutePath());
      else
         return runCommand(gradleExe + " " + command, Paths.get(buildsDir + project).toAbsolutePath());
   }
}
