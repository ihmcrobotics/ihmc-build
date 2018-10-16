package us.ihmc.ci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.nio.FileTools;

import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;

/**
 * Must be run from ihmc-ci root directory.
 */
public class ParallelExecutionTest
{
   @Test
   public void testAllTestsRun() throws UnsupportedEncodingException
   {
      String projectName = "categories";
      String cleanOutput = GradleSubBuildTools.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleSubBuildTools.runGradleTask("test", projectName);
      Assertions.assertTrue(output.contains("BUILD FAILED"));

      String results = new String(FileTools.readAllBytes(Paths.get("src/junitfive-test/builds/categories/src/test/build/reports/tests/test/index.html"), e -> Assertions.fail(e)), "UTF-8");

      // Asserts 11 tests pass, 1 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains("<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>\r\n</td>\r\n<td>11</td>\r\n<td>1</td>\r\n<td>0</td>"));
   }

   @Test
   public void testFastTestsRun() throws UnsupportedEncodingException
   {
      String projectName = "categories";
      String cleanOutput = GradleSubBuildTools.runGradleTask("clean", projectName);
      Assertions.assertTrue(cleanOutput.contains("BUILD SUCCESSFUL"));
      String output = GradleSubBuildTools.runGradleTask("test -PincludeTags=fast", projectName);
      Assertions.assertTrue(output.contains("BUILD SUCCESSFUL"));

      String results = new String(FileTools.readAllBytes(Paths.get("src/junitfive-test/builds/categories/src/test/build/reports/tests/test/index.html"), e -> Assertions.fail(e)), "UTF-8");

      // Asserts 5 tests pass, 0 test fails, 0 tests ignored
      Assertions.assertTrue(results.contains("<a href=\"packages/us.ihmc.ci.html\">us.ihmc.ci</a>\r\n</td>\r\n<td>5</td>\r\n<td>0</td>\r\n<td>0</td>"));
   }
}
