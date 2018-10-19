package us.ihmc.ci

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention

fun testProjects(project: Project): List<Project>
{
   val testProjects = arrayListOf<Project>()
   for (allproject in project.allprojects)
   {
      if (allproject.name.endsWith("-test"))
      {
         testProjects += allproject
      }
   }
   return testProjects
}

fun containsIHMCTestMultiProject(project: Project): Boolean
{
   for (allproject in project.allprojects)
   {
      if (allproject.name.endsWith("-test"))
      {
         return true
      }
   }
   return false
}
