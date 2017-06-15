package us.ihmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.language.java.plugins.JavaLanguagePlugin

/**
 * <p>
 * The {@code IHMCBuild} Plugin configures a project with conventions used in the IHMC Robotics Lab.
 * This includes a legacy source set structure that is atypical from many other projects.
 * </p>
 *
 * <p>
 *     It also provides the {@code ihmc} extension implemented in the {@link IHMCBuildExtension} class,
 *     which exposes some helper methods related to the way we manage our workspaces.
 * </p>
 *
 * <p>
 *     Outside of adding the extension, the plugin also configures the directory structure (aka {@code sourceSets})
 *     for Java and Groovy to use the IHMC Robotics standard as opposed to the Maven Standard Directory Layout. We favor top-level
 *     source/test/resource directories instead of the nesting of different source trees under a single directory, for many reasons
 *     one of which is the inertia of our codebase.
 *
 * </p>
 * <p>
 *     The structure looks someting like this:
 * </p>
 *
 * <ul>
 *  <li><em>Java code</em></li>
 *  <ul>
 *      <li>Source: src</li>
 *      <li>Test: test</li>
 *      <li>Resources: resources</li>
 *  </ul>
 *  <li><em>Groovy code</em></li>
 *  <ul>
 *      <li>Source: groovySrc</li>
 *      <li>Test: groovyTest</li>
 *      <li>Resources: resources</li>
 *  </ul>
 * </ul>
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
class IHMCBuild implements Plugin<Project>
{
   /**
    * <p>Inherited from {@link Plugin}</p>
    *
    * <p>
    * Adds the {@link IHMCBuildExtension} property to the {@code Project} and
    * configures the Java and/or Groovy {@code sourceSets} to use the IHMC standard
    * instead of the Maven standard.
    * </p>
    *
    * @see org.gradle.api.Plugin
    * @see org.gradle.api.tasks.SourceSet
    * @see IHMCBuildExtension
    */
   @Override
   void apply(Project project)
   {
      project.configure(project) {

         if (!project.getPlugins().hasPlugin(JavaLanguagePlugin.Java))
         {
            apply plugin: 'java'
         }

         apply plugin: 'ca.cutterslade.analyze'

         configurations {
            testOutput.extendsFrom(testRuntime)
         }

         project.task("jarTest", type: Jar, dependsOn: project.testClasses) {
            from sourceSets.test.output
            classifier = 'test'
         }

         artifacts {
            testOutput project.jarTest
         }
      }

      addExtensions(project)

      project.task('checkIHMCBuild') << {
         println "IHMC Build being applied from ${project.name}"
      }
   }

   private void addExtensions(Project project)
   {
      project.extensions.create("ihmc", IHMCBuildExtension, project)
   }
}
