# Containerised Development in VS Code

Visual Studio Code will detect a devcontainer.json file and prompt to reopen in container.

Once open, you will see the following information in the header bar: ![vscode.png](vscode.png)

## Clone-outside-container

This is the recommended approach.  It allows merge of user tooling.

NB This puts the code on the real hard drive, which **may be considered undesirable**.

1. Clone the repo
2. Apply additions/alterations to devenv.yaml and regenerate devcontainer files (ie devenv generate)
3. Open your preferred devcontainer file in VSCode.    
4. A toast popup (below right) will invite you to reopen in a container.  
5. You must choose the container explicitly at this point.  
6. The container will be created and will mount the existing checkout.  
7. Close and restart of these instructions will detect the existing container and re-use it, which could be a gotcha.

## Clone-inside-container

No merge of user tooling.

1. F1 (Actions), “clone”, provide the repo address, and run the action.
