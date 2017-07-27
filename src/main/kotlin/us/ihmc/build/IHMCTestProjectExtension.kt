package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.dependencies

open class IHMCTestProjectExtension(val project: Project)
{
   val hyphenatedName: String = project.property("hyphenatedName") as String
   val testProject = project.project(":$hyphenatedName-test")
   
   fun compile(dependencyNotation: Any?)
   {
      testProject.dependencies {
         add("compile", dependencyNotation)
      }
   }
}