package us.ihmc.gradle

import org.gradle.api.Project

/**
 * the {@code IHMCBuildExtension} provides some helper extensions to any project
 * that it is applied to exposed via the {@code ihmc} property on the project. Much
 * of this is specific to the way in which we organize our code and dependencies in
 * the IHMC Robotics Lab so may be of dubious usefulness to others.
 *
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
class IHMCBuildExtension {
    private final Project containingProject;

    def IHMCBuildExtension(Project project) {
        this.containingProject = project
    }

    /**
     * Set up a closure containing all of the commonly used artifact repos and
     * proxies common across IHMC Robotics software.
     *
     * @return a {@link Closure that generates all of the "default" {@link org.gradle.api.artifacts.repositories.ArtifactRepository}s we typically use
     */
    def ihmcDefaultArtifactProxies(){

        containingProject.repositories.mavenLocal()

        containingProject.repositories.maven {
                url "https://bengal.ihmc.us/nexus/content/repositories/releases/"
            }

        containingProject.repositories.maven {
                url "https://bengal.ihmc.us/nexus/content/repositories/thirdparty/"
            }

        containingProject.repositories.maven {
                url "https://bengal.ihmc.us/nexus/content/repositories/central/"
            }

        containingProject.repositories.maven {
                url "https://bengal.ihmc.us/nexus/content/repositories/swt-repo/"
            }

        containingProject.repositories.jcenter()

        containingProject.repositories.mavenCentral()
    }

    /**
     * This method is used to naïvely look up a Project in a multi-project build by its name,
     * irrespective of the hierarchy that contains it. The primary use case for this is to allow for
     * configuration-independent lookup of local source depenencies that can either be part of a standalone
     * multi-project build or as part of a several-level nested developer workspace. E.g., source dependencies
     * in the project IHMCOpenRoboticsSoftware can be identified as ":ProjectName" if being built within IHMCOpenRoboticsSoftware,
     * or as ":IHMCOpenRoboticsSoftware:ProjectName" if the build is kicked off by a developer workspace. Example usage of this method
     * would be:
     *
     *{@code
     * dependencies{
     *   compile ihmc.getProjectDependency(":ProjectName")
     * }
     * }
     *
     * The lookup is very simple in that it starts at the {@link Project#getRootProject()} property and gets a flattened list of all
     * child projects and then returns the first successful result of {@link String#endsWith(java.lang.String)} by comparing with {@link Project#getPath()}.
     * If there are no successful matches, it will return {@code null}.
     *
     * @param projectName The project name to locate
     * @return The {@link Project} matching the name based on the rules described above, or {@code null} if there is no match.
     */
    def Project getProjectDependency(String projectName) {
        def projects = getAllProjects(containingProject.getRootProject())

        for(Project project : projects)
        {
            if(project.path.endsWith(projectName))
            {
                containingProject.logger.debug("IHMC Build Extension Found ${project.path} for getProjectDependency(${projectName})")
                return project
            }
        }

        return null
    }

    /**
     * Uses the same search mechanism as {@link IHMCBuildExtension#getProjectDependency(java.lang.String)} but returns the
     * fully qualified Gradle path to the project as a String instead of the project object.
     *
     * @param projectName The project name to locate
     * @return The fully qualified Gradle path to the project as given by {@link Project#getPath()}
     */
    def String getProjectDependencyGradlePath(String projectName) {
        def project = getProjectDependency(projectName)

        if(project != null)
        {
            return project.path
        }

        return null
    }

    private List<Project> getAllProjects(Project rootProject)
    {
        def ret = new ArrayList<Project>()

        if(!rootProject.childProjects.isEmpty())
        {
            getAllProjectsFlattened(rootProject.childProjects.values(), ret)
        }

        return ret
    }

    private void getAllProjectsFlattened(Collection<Project> projects, List<Project> flatProjects)
    {
        for(Project project : projects)
        {
            if(!project.childProjects.isEmpty())
            {
                getAllProjectsFlattened(project.childProjects.values(), flatProjects)
            }
            else
            {
                flatProjects.add(project)
            }
        }
    }
}
