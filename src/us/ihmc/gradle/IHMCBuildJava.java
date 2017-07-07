package us.ihmc.gradle;

import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * <p>
 * The {@code IHMCBuild} Plugin configures a project with conventions used in the IHMC Robotics Lab.
 * This includes a legacy source set structure that is atypical from many other projects.
 * </p>
 *
 * <p>
 *     It also provides the {@code ihmc} extension implemented in the {@link IHMCBuildExtensionJava} class,
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
 * @author Duncan Calvert <a href="mailto:dcalvert@ihmc.us">(dcalvert@ihmc.us)</a>
 */
public class IHMCBuildJava implements Plugin<Project>
{
   private Project project;

   @Override
   public void apply(Project project)
   {
      this.project = project;

      project.configure(project, new MethodClosure(this, "configureProject"));
   }

   public void configureProject()
   {
      if (!project.getPlugins().hasPlugin("java"))
      {
         //project.apply(new MapEntry("plugin", "java"));
      }
   }
}
