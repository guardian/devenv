# Changing User

The choice of base docker image effectively fixes the username. The default image
`mcr.microsoft.com/devcontainers/base:ubuntu` has built in user `vscode`  (apart from root). As a result, any attempt to
connect to that image with any other remoteUser setting will fail.

Advice is therefore: **_do not do this_**.

However: you may have a pressing reason to do it anyway. Below is a cheat sheet on how to achieve a remote user of your
choice.

## How to

If you use a different image and/or username then they must match. If you use a different base image this is as simple
as setting "remoteUser" in your devcontainer.json.

### Static setting with different image

If you wish to use another base image, for whatever reason, and it has a different user baked in to it, you will need to
set both in devenv.yaml:

```
name: "devenv",
image: "<newbaseimage>",
remoteUser: "<newuser>",
...
```

### Dynamic setting with local image

If you want to dynamically create an arbitrary username, you can alter your devcontainer.json directly. You need to use
something like:

```
{
  "name" : "devenv",
  "build" : {
    "dockerfile": "Dockerfile",
  },
  "remoteUser": "myname",
...
```

and a Dockerfile correctly placed relative to the devcontainer file containing the minimum necessary changes (username,
groupname, home, sudo:

```
FROM mcr.microsoft.com/devcontainers/base:ubuntu
RUN /usr/sbin/usermod -l myname -d /home/myname -m vscode
RUN /usr/sbin/groupmod -n myname vscode
RUN /usr/bin/sed -i 's/vscode/myname/g' /etc/sudoers.d/vscode
RUN /usr/bin/mv /etc/sudoers.d/vscode /etc/sudoers.d/myname
```

This example starts with the same docker image, but changes the user as it builds.  

