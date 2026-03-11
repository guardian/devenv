# Containerised Development in IntelliJ

IntelliJ uses this icon in the gutter of devcontainer files for devcontainer interactions: ![gutter-icon.png](gutter-icon.png)

## Clone-outside-container

This is the recommended approach.  It allows merge of user tooling.

NB This puts the code on the real hard drive, which **may be considered undesirable**.

1. Clone repo as normal 
   1. Eg git@github.com:guardian/interview-questions.git
2. Apply additions/alterations to devenv.yaml and regenerate devcontainer files (ie devenv generate)
3. Open the project in IntelliJ and navigate to a devcontainer file
   1. .devcontainer/shared/devcontainer.json for the shared project settings
   2. .devcontainer/user/devcontainer.json for the user-specific project settings
4. Click ![gutter-icon.png](gutter-icon.png) from the gutter of the desired config and re-open as container.  You will be asked if you want mount or clone the source.
5. "Create Dev container and Clone Sources"

InterlliJ will now create a dev container from the local source, but fetch the actual application source from git. At this point you can delete the original checkout.

Note that the option "Create Dev container and Mount Sources" is also available.  This will use the local src and local container config.  This may be useful if iterating on the devcontainer itself, but is not generally recommended as the objective is to avoid having source on the "real" machine.

## Clone-inside-container

For completeness, if you have no need for user-specific tooling, then IntelliJ's own (documentation](https://www.jetbrains.com/help/idea/start-dev-container-from-welcome-screen.html) covers starting a container project.

However, this is not recommended as there will be no caching of shared tooling, which can result in long startup times while downloading completes.