package us.ihmc.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.ArtifactRepository

/**
 * Created by dstephen on 1/26/16.
 */
class IHMCBuildExtension {
    private final Project containingProject;

    def IHMCBuildExtension(Project project) {
        this.containingProject = project
    }

    def defaultArtifactRepositories(){

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
