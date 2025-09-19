package us.ihmc.ci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import us.ihmc.build.GradleTestingToolsKt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ParallelExecutionTest
{
   @Test
   public void testAllTestsRun() throws IOException
   {
      String projectName = "categories";
      GradleTestingToolsKt.runGradleTask("-v", projectName);
      String cleanOutput = GradleTestingToolsKt.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleTestingToolsKt.runGradleTask("test --info -Pcategory=all", projectName);
      Assertions.assertTrue(output.contains("BUILD FAILED"));

      System.out.println("Working dir: " + Paths.get(".").toAbsolutePath());
      String results = new String(Files.readAllBytes(Paths.get("tests/categories/src/test/build/reports/tests/test/index.html")));
      System.out.println(results);
      // Asserts 11 tests pass, 1 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains(
            "<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>" + System.lineSeparator() + "</td>" + System.lineSeparator() + "<td>9</td>"
            + System.lineSeparator() + "<td>1</td>" + System.lineSeparator() + "<td>0</td>"));
   }

   @Test
   @Disabled
   public void testFastTestsRun() throws IOException
   {
      String projectName = "categories";
      GradleTestingToolsKt.runGradleTask("-v", projectName);
      String cleanOutput = GradleTestingToolsKt.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleTestingToolsKt.runGradleTask("test --info -Pcategory=fast", projectName);
      Assertions.assertTrue(output.contains("BUILD SUCCESSFUL"));

      System.out.println("Working dir: " + Paths.get(".").toAbsolutePath());
      String results = new String(Files.readAllBytes(Paths.get("tests/categories/src/test/build/reports/tests/test/index.html")));
      System.out.println(results);
      // Asserts 5 tests pass, 0 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains(
            "<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>" + System.lineSeparator() + "</td>" + System.lineSeparator() + "<td>4</td>"
            + System.lineSeparator() + "<td>0</td>" + System.lineSeparator() + "<td>0</td>"));
   }
}
