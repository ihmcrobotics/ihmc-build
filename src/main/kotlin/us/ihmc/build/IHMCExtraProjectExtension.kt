package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.dependencies

open class IHMCExtraProjectExtension(val project: Project)
{
   val hyphenatedName: String = project.property("hyphenatedName") as String
   
   fun compile(sourceSet: String, dependencyNotation: Any?)
   {
      project.project(":$hyphenatedName-$sourceSet").dependencies {
         add("compile", dependencyNotation)
      }
   }
}