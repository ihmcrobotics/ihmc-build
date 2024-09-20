package us.ihmc.cd

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.getByName
import java.io.File

class AppExtension(val project: Project)
{
   val javaApplicationMap = hashMapOf<Project, JavaApplication>()

   init
   {
      project.allprojects {
         pluginManager.apply(ApplicationPlugin::class.java)
         val javaApplication = extensions.getByType(JavaApplication::class.java)
         javaApplicationMap.put(this, javaApplication)
         javaApplication.mainClass.set("") // make user experience so N start scripts is natural
      }
   }

   fun entrypoint(applicationName: String, mainClassName: String)
   {
      entrypoint(applicationName, mainClassName, null)
   }

   fun entrypoint(applicationName: String, mainClassName: String, defaultJvmOpts: Iterable<String>? = null)
   {
      entrypoint(project, applicationName, mainClassName, defaultJvmOpts)
   }

   fun entrypoint(project: Project, applicationName: String, mainClassName: String, defaultJvmOpts: Iterable<String>? = null)
   {
      val entrypoint = project.tasks.create(applicationName.decapitalize(), CreateStartScripts::class.java) {
         this.mainClass.set(mainClassName)
         this.applicationName = applicationName
         if (defaultJvmOpts != null)
         {
            this.defaultJvmOpts = defaultJvmOpts
         }
         this.outputDir = File(project.buildDir, "scripts")
         this.classpath = project.tasks.getByName<Jar>("jar").outputs.files + project.configurations.getByName("runtimeClasspath")
      }
      javaApplicationMap[project]!!.applicationDistribution.into("bin") {
         from(entrypoint)
         duplicatesStrategy = DuplicatesStrategy.INCLUDE
      }
   }
}