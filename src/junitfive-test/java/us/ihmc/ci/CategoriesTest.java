package us.ihmc.ci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.nio.FileTools;
import us.ihmc.log.LogTools;

import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;

/**
 * Must be run from ihmc-ci/src/junitfive-test root directory.
 */
@Tag("categories-test")
public class CategoriesTest
{
   @Test
   public void testAllTestsRun() throws UnsupportedEncodingException
   {
      String projectName = "categories";
      GradleSubBuildTools.runGradleTask("-v", projectName);
      String cleanOutput = GradleSubBuildTools.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleSubBuildTools.runGradleTask("test --info -Pcategory=all", projectName);
      Assertions.assertTrue(output.contains("BUILD FAILED"));

      LogTools.info("Working dir: " + Paths.get(".").toAbsolutePath());
      String results = new String(FileTools.readAllBytes(Paths.get("builds/categories/src/test/build/reports/tests/test/index.html"), e -> Assertions.fail(e)), "UTF-8");
      System.out.println(results);
      // Asserts 11 tests pass, 1 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains("<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>" + System.lineSeparator() +
                                                   "</td>" + System.lineSeparator() +
                                                   "<td>9</td>" + System.lineSeparator() +
                                                   "<td>1</td>" + System.lineSeparator() +
                                                   "<td>0</td>"));
   }

   @Test
   public void testFastTestsRun() throws UnsupportedEncodingException
   {
      String projectName = "categories";
      GradleSubBuildTools.runGradleTask("-v", projectName);
      String cleanOutput = GradleSubBuildTools.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleSubBuildTools.runGradleTask("test --info -PincludeTags=fast", projectName);
      Assertions.assertTrue(output.contains("BUILD SUCCESSFUL"));

      LogTools.info("Working dir: " + Paths.get(".").toAbsolutePath());
      String results = new String(FileTools.readAllBytes(Paths.get("builds/categories/src/test/build/reports/tests/test/index.html"), e -> Assertions.fail(e)), "UTF-8");
      System.out.println(results);
      // Asserts 5 tests pass, 0 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains("<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>" + System.lineSeparator() +
                                                   "</td>" + System.lineSeparator() +
                                                   "<td>6</td>" + System.lineSeparator() +
                                                   "<td>0</td>" + System.lineSeparator() +
                                                   "<td>0</td>"));
   }
}
