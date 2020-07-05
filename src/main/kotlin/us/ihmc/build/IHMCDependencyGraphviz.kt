package us.ihmc.build

import org.gradle.api.Project

class IHMCDependencyGraphviz(val project: Project)
{
   init
   {
      project.tasks.create("graphDependencies")
      {
         doLast {

         }
      }
   }
}