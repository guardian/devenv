# Containerised Development in VS Code

Visual Studio Code will detect a devcontainer.json file and prompt to reopen in container.

In a dev container project, the header bar will display

`Dev Container: <project> @ <image>`

For example, opening this project (devenv) in a container based on the desktop linux image results in: ![vscode.png](vscode.png)

## Clone-outside-container

This is the recommended approach.  It allows merge of user tooling.

NB This puts the code on the real hard drive, which **may be considered undesirable**.

1. Clone the repo
2. Apply additions/alterations to devenv.yaml and regenerate devcontainer files (ie devenv generate)
3. Open your project in VSCode.    
4. A toast popup (below right) will invite you to reopen in a container.  
5. A subtle dropdown on the header bar will invite you to choose the devcontainer.json file explicitly.  Choose
   1. `.devcontainer/shared/devcontainer.json` for the shared project settings
   2. `.devcontainer/user/devcontainer.json` for the user-specific project settings
6. The container will be created and will mount the existing checkout.  
7. Close and restart of these instructions will detect the existing container and re-use it, which could be a gotcha.

## Clone-inside-container

No merge of user tooling.

1. F1 (Actions), “clone”, provide the repo address, and run the action.

## Logging

The “remote dev server” process used to run the project environment inside the container stores its logging in `/home/vscode/.vscode-server/data/logs/` ***inside*** the container with a datestamped directory.

If it is unresponsive, the logging can still be viewed via docker:
```
docker exec -it <container> bash -c 'find /home/vscode/.vscode-server/data/logs/*/remoteagent.log | tail -1 | xargs tail -f'
```
