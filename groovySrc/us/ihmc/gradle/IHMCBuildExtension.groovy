package us.ihmc.gradle

import org.gradle.api.Project

/**
 * Created by dstephen on 1/26/16.
 */
class IHMCBuildExtension {
    private final Project containingProject;

    def IHMCBuildExtension(Project project) {
        this.containingProject = project
    }

    def Project getProjectDependency(String projectName) {
        def projects = getAllProjects(containingProject.getRootProject())

        for(Project project : projects)
        {
            if(project.path.endsWith(projectName))
            {
                println "Found ${project.path} matching ${projectName}"
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
