# Containerised Development

Supply chain poisoning attacks are a present danger to developers who run code on their own devices.

See https://www.upwind.io/feed/hackerbot-claw-github-actions-pull-request-rce for a real world example of an end to end attack resulting in compromised, trusted, code in a dependency.

There is therefore a need to provide development capability without permitting arbitrary code from dependencies to run natively on a developer laptop, which may have wide ranging permissions, or powerful keys.

### General Approach

Both Visual Studio and IntelliJ have added capability to develop code in a container to encapsulate runtimes from the rest of the machine.  The standard, familiar, UI connects to it providing a fairly seamless developer experience. 

However, neither provide the capability to integrate the developers own preferences with the project requirements, which means that experience, while familiar, cannot be simply tailored to the individual.

To bridge that gap, tooling in this repository enables the creation of working environments which are both streamlined and capable of tailoring to the developer.

### SSH agent

Your git credentials will not be visible inside the container.  The IDE will mediate access to the ssh agent on the host (laptop) for you.

Ensure your git key is available to the ssh agent:

```
ssh-add -k ~/.ssh/id_ed25519
```

### AWS Credentials

Similarly, your .aws directory is not mounted, for obvious reasons.  As a result, limited credentials must be obtained and configured within the container. 

# Bootstrapping

## IntelliJ

### Clone-outside-container (allows merge of user tooling)

1. Clone repo NB This puts the code on the real hard drive, which **may be considered undesirable**.
   1. Eg git@github.com:guardian/interview-questions.git
2. Apply additions/alterations to devcontainer as desired **_(ie devenv generate)_**
3. Open in IntelliJ
4. Re-open as container (from the gutter of the desired config)
   1. When the project is reopened with mount, it will use the local src and local container config.  This may be useful if iterating on the devcontainer itself.
   2. When the project is reopened with clone, it will use the git src and local container config. At this point you can delete the original checkout.

### Clone-inside-container (no merge of user tooling)

1. Go to IntelliJ \-\> File \-\> Remote Development \-\> New Dev Container  
2. Specify repo  
   1. Eg git@github.com:guardian/interview-questions.git  
   2. If the devcontainer file is not at the standard location within the repo you are cloning then specify.  NB this means you can’t poke in your own additions, without pushing to a remote branch, which is undesirable.  
3. Build container and continue  
4. Wait for “Dev container built”  
5. Click “Connect”.  On a slow connection, you may need to retry a few times while the server populates \- see [Logging](#volume-re-use) below.  
6. Trust the project (it’s in a container, that’s OK)

See [https://www.jetbrains.com/help/idea/start-dev-container-from-welcome-screen.html](https://www.jetbrains.com/help/idea/start-dev-container-from-welcome-screen.html)

### When finished

1. Close the project, but leave the container
   1. You can stop it if you need/want to, but ideally don’t terminate it \- it’s not taking up enough resource to care
   2. If you do terminate it, the source volume is kept anyway and will be reused
   3. If you remove the source volume, you’ll need to start over completely
2. To begin using that project again, open the project and use the Services tool to re-open the container

## Visual Studio

### Clone-outside-container (allows merge of user tooling)

1. Clone the repo
   1. Apply additions/alterations to devcontainer as desired **_(ie devenv generate)_**
   2. Open your preferred devcontainer file in VSCode.    
2. A toast popup (below right) will invite you to reopen in a container.  
3. You must choose the container explicitly at this point.  
4. The container will be created and will mount the existing checkout.  
5. Close and restart of these instructions will detect the existing container and re-use it, which could be a gotcha.

### Clone-inside-container (no merge of user tooling)

1. F1 (Actions), “clone”, provide the git address, and run the action.

## Other notes

1. Start a terminal in the IDE when the project is up in its container.  `hostname` is the docker container id.  This is the simplest way to interact with a command line because the IDE mediates between internal and external processes.  
2. Optionally rename (on host)  
   * `docker rename <name> <newname>`

# Issues and Gotchas

## Startup

postCreateCommand does not block the project connection, so you can start a non-functioning terminal.  Give it time.

## Slow start

The “remote dev server” process does some setup and may require some downloads (eg plugins).

If it tries to download something big, it can make the remote service appear to hang, resulting in a timeout (only 60s in IntelliJ) in the client.

However, it will pick up where it left off every time you hit retry.  You can tail the log via docker exec or watch the file space grow under /.jbdevcontainer/ to confirm this.

Eventually, it will connect.  See also Logging below.

##  Search

1. There is *NO* local filesystem representation if the clone-in-docker approach is used.  The pointer to the project is in the IntelliJ sqllite db and the filesystem is in the docker image.
   1. This implicitly means security scanning won’t work.  
   2. Check for keys on the repo on the server side is therefore recommended.
2. Similarly, grepping for values is harder, but possible eg   
   * `docker exec -it f8a4c720aef5 /usr/bin/grep -wirl <content> /IdeaProjects`  

## Control

1. Closing the project will not stop the container.  
2. Restarting the container can cause IntelliJ to freeze  
3. For people whose process is terminal-heavy it can be very useful to switch the terminal window to a non-docked window, so it begins to look and feel like good old-fashioned Terminal.

## Volume re-use

The reused volume, while helpful, may mean that when you restart a container, you are actually not on the branch you expect.  Even if you specify clone/main on the IDE, the volume will be re-used if it exists and the disk version may be on another branch.  I would hope this will be addressed as a plugin issue.

## Logging (IntelliJ)

The “remote dev server” process used to run the project environment inside the container stores its logging in /.jbdevcontainer/JetBrains/IntelliJIdea2025.3/log/idea.log ***inside*** the container.

Useful command from host:  
```
docker exec -it <container> tail -f /.jbdevcontainer/JetBrains/IntelliJIdea2025.3/log/idea.log
```

## SSH Agent

The ssh agent socket is not forwarded to the docker environment.  It’ll work in the IDE, but not in docker exec.

## The default user for docker exec is not vscode 

Advise the use of  
```
docker exec -itu vscode <container> bash
```

## Absolutely Minimal Bootstrap Process

If you are hardcore, you may wish to check out only the files you need for a devcontainer, merge in your own preferences, and launch.  This results in a tailored environment, but no executable or source outside the container. 

This example macOS approach uses the github command line tool `gh` and IntelliJ.  It assumes you want the devcontainer files from `main` branch:

```
repo=<repo>

gh auth login  
mkdir -p $repo  
cd $repo

for filepath in .devcontainer/shared/devcontainer.json .devcontainer/devenv.yaml; do
  mkdir -p $(dirname $filepath);  
  curl -H "Authorization: Bearer $(gh auth token)" -H 'Accept: application/vnd.github.v3.raw' "https://api.github.com/repos/guardian/$repo/contents/$filepath" > $filepath;
done
```

Customise as desired, then generate devcontainer files and show them to IntelliJ
```
devenv generate  
open -na 'Intellij idea' --args . --line 1 .devcontainer/user/devcontainer.json
```

In the IDE, trust the (minimal) project and click the cube in the gutter of .devcontainer/user/devcontainer.json. 
Follow the onscreen instructions as before.