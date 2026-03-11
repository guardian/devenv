# Containerised Development

Supply chain poisoning attacks are a present danger to developers who run code on their own devices.

See https://www.upwind.io/feed/hackerbot-claw-github-actions-pull-request-rce for a real world example of an end to end attack resulting in compromised, trusted, code in a dependency.

There is therefore a need to provide development capability without permitting arbitrary code from dependencies to run natively on a developer laptop, which may have wide ranging permissions, or powerful keys.

### General Approach

Both Visual Studio and IntelliJ have added capability to develop code in a container to encapsulate runtimes from the rest of the machine.  The standard, familiar, UI connects to it providing a fairly seamless developer experience. 

However, beyond limited dotfiles support, neither provide the capability to integrate the developers more complex preferences such as plugins, with the project requirements, which means that experience, while familiar, cannot be simply tailored to the individual.

To bridge that gap, tooling in this repository enables the creation of working environments which are both streamlined and capable of tailoring to the developer.

### SSH agent

Your git credentials will not be visible inside the container.  The IDE will mediate access to the ssh agent on the host (laptop) for you.

Ensure your git key is available to the ssh agent:
    
```
ssh-add -k ~/.ssh/id_ed25519
```

### AWS Credentials

Similarly, your ~/.aws directory (where your personal AWS credentials are stored) is not mounted, for obvious reasons.  As a result, limited credentials must be obtained and configured within the container. 

# Bootstrapping

See [here](containerised-development-in-intellij.md) for IntelliJ specific instructions and [here](containerised-development-in-vscode.md) for Visual Studio Code.  

## Terminal Users

Generally, the IDE terminal is recommended, as your IDE will mediate services such as ssh-agent.

For people whose process is terminal-heavy it can be very useful to switch the terminal window to a non-docked window, so it begins to look and feel like good old-fashioned Terminal.

Your application's source code and the IDE session will be isolated in a docker container, and you can get a shell in that container the normal way:

```
docker exec -it -u vscode <container> bash
```
Note: the default user for devcontainers is not vscode, so you'll need to specify this username.

## When finished

1. Close the project, but leave the container
   1. You can stop it if you need/want to, but ideally don’t terminate it - it’s not taking up enough resource to care
   2. If you do terminate it, the source volume is kept anyway and will be reused
   3. If you remove the source volume, you’ll need to start over completely
2. To begin using that project again, open the project and use the Services tool to re-open the container

# Issues and Gotchas

## Startup

`postCreateCommand` does not block container connections, so you can start an apparently functional terminal but then find that the tooling you expect (eg node) is not installed.  Give it time.

## Slow start

The “remote dev server” process does some setup and may require some downloads (eg plugins).

This can make the remote service busy for long periods, resulting in a timeout in the client.  
In IntelliJ this is only 60s, which on a slow connection is easily exceeded.

However, it will pick up where it left off every time you hit retry.  Eventually, it will connect.  See Logging below.

## Control

Restarting the container outside the IDE can cause IntelliJ to freeze

## Volume re-use

The reused volume, while helpful, may mean that when you restart a container, you are actually not on the branch you expect.  Even if you specify clone/main on the IDE, the volume will be re-used if it exists and the checked out source on that disk may be on another branch.  
This may be addressed in the future as a plugin issue.

## Logging (IntelliJ)

The “remote dev server” process used to run the project environment inside the container stores its logging in /.jbdevcontainer/JetBrains/IntelliJIdea2025.3/log/idea.log ***inside*** the container.

If it is unresponsive, the logging can still be viewed via docker:
```
docker exec -it <container> tail -f /.jbdevcontainer/JetBrains/IntelliJIdea2025.3/log/idea.log
```

## SSH Agent

SSH Agent is commonly used for communication with github and other ssh-based services.

It will work in the IDE, but it is not forwarded to the docker environment when using `docker exec`.  

## Absolutely Minimal Bootstrap Process

As an "advanced" option, if you prefer to be strict with regard to source code on disk, but still have the ability to apply your own preferences, see [here](containerised-development-minimal.md)


