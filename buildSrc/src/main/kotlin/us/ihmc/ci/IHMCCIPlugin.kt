package us.ihmc.ci;

import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;

import java.nio.file.Path;

/**
 * TODO
 *
 * - bambooPlansToCheck
 * - remove src folder check
 * 
 * Possible
 * - multi project build options
 * - excluded projects
 */
class IHMCCIPlugin : Plugin<Project>
{
   lateinit var project: Project
   lateinit var projectPath: Path
   lateinit var multiProjectPath: Path
//   var testSuitesConfiguration: TestSuiteConfiguration

   override fun apply(project: Project)
   {
      this.project = project
      projectPath = project.getProjectDir().toPath()
      multiProjectPath = project.getRootDir().toPath().resolve("..")

      configureJUnitPlatform(project)

      //      testSuitesConfiguration = new TestSuiteConfiguration(project.getProperties())
//      testSuitesConfiguration.hyphenatedName = project.getName()
//      testSuitesConfiguration.pascalCasedName = AgileTestingTools.hyphenatedToPascalCased(project.getName())
//
//      createTask(project, "generateTestSuites")
//      createTask(project, "generateTestSuitesMultiProject")
   }

   private fun configureJUnitPlatform(project: Project)
   {
      try
      {
         val testProject = project.project(project.getName() + "-test") // assumes ihmc-build plugin
         val testExtension = testProject.getTasks().getByName("test") as Test

         testExtension.useJUnitPlatform { jUnitPlatformOptions ->
            project.getLogger().info("[ihmc-ci] Using JUnit platform")

            includeTags(project, jUnitPlatformOptions, "includeTags")
            includeTags(project, jUnitPlatformOptions, "includeTag")
            excludeTags(project, jUnitPlatformOptions, "excludeTags")
            excludeTags(project, jUnitPlatformOptions, "excludeTag")
         }
      }
      catch (e: UnknownDomainObjectException)
      {
         // do nothing
      }
   }

   private fun includeTags(project: Project, jUnitPlatformOptions: JUnitPlatformOptions, propertyName: String)
   {
      if (project.hasProperty(propertyName) && !project.property(propertyName)!!.equals("all"))
      {
         for (includeTag in (project.property(propertyName) as String).trim().toLowerCase().split(","))
         {
            project.getLogger().info("[ihmc-ci] Including tag: " + includeTag)
            jUnitPlatformOptions.includeTags(includeTag)
         }
      }
   }

   private fun excludeTags(project: Project, jUnitPlatformOptions: JUnitPlatformOptions, propertyName: String)
   {
      if (project.hasProperty(propertyName) && !project.property(propertyName)!!.equals("none"))
      {
         for (excludeTag in (project.property(propertyName) as String).trim().toLowerCase().split(","))
         {
            project.getLogger().info("[ihmc-ci] Excluding tag: " + excludeTag)
            jUnitPlatformOptions.excludeTags(excludeTag)
         }
      }
   }

//    @SuppressWarnings("unchecked")
//    private <T> T createExtension(String name, T pojo)
//    {
//       project.getExtensions().create(name, pojo.getClass());
//       return ((T) project.getExtensions().getByName(name));
//    }

//    private void createTask(Project project, String taskName)
//    {
//       project.task(taskName).doLast(new MethodClosure(this, taskName));
//    }

//    fun generateTestSuites()
//    {
//       AgileTestingStandaloneWorkspace workspace = new AgileTestingStandaloneWorkspace(new StandaloneProjectConfiguration(projectPath, testSuitesConfiguration));
//       workspace.loadClasses();
//       workspace.loadTestCloud();
//       workspace.generateAllTestSuites();
//       workspace.printAllStatistics();
//       if (testSuitesConfiguration.getCrashOnMissingTimeouts())
//       {
//          workspace.checkJUnitTimeouts();
//       }
//       if (!testSuitesConfiguration.getDisableJobCheck())
//       {
//          workspace.checkJobConfigurationOnBamboo(testSuitesConfiguration.crashOnEmptyJobs);
//       }
//    }

//    fun generateTestSuitesMultiProject()
//    {
//       BambooTestSuiteGenerator bambooTestSuiteGenerator = new BambooTestSuiteGenerator();
//       bambooTestSuiteGenerator.createForMultiProjectBuild(multiProjectPath);
//       bambooTestSuiteGenerator.generateAllTestSuites();
//       bambooTestSuiteGenerator.printAllStatistics();
//       if (!testSuitesConfiguration.getDisableJobCheck())
//       {
//          List<BambooRestPlan> bambooPlanList = new ArrayList<>();
//          for (String planKey : testSuitesConfiguration.getBambooPlanKeys())
//          {
//             bambooPlanList.add(new BambooRestPlan(planKey));
//          }
//          bambooTestSuiteGenerator.checkJobConfigurationOnBamboo(testSuitesConfiguration.getBambooUrl(), bambooPlanList);
//       }
//    }
}
