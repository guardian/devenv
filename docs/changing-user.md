# Changing User

The choice of base docker image effectively fixes the username.  Our default user `vscode` is the built in user (apart from root) in the `mcr.microsoft.com/devcontainers/base:ubuntu` image.  As a result, any attempt to connect with any other remoteUser setting will fail.

Advice is therefore: **_do not do this_**.

However: you may have a pressing reason to do it anyway.  Below is a cheat sheet on how to achieve a remote user of your choice.

## How to

If you use a different image and/or username then they must match.  If you use a different base image this is as simple as setting "remoteUser" in your devcontainer.json.

If you want to dynamically create an arbitrary username, you need to use something like:

```
{
  "name" : "devenv",
  "build" : {
    "dockerfile": "Dockerfile",
  },
  remoteUser: "justin",
...
```

and a Dockerfile correctly placed relative to the devcontainer file containing:

```
FROM mcr.microsoft.com/devcontainers/base:ubuntu
RUN /usr/sbin/usermod -l justin -d /home/justin -m vscode
RUN /usr/sbin/groupmod -n justin vscode
RUN /usr/bin/sed -i 's/vscode/justin/g' /etc/sudoers.d/vscode
RUN /usr/bin/mv /etc/sudoers.d/vscode /etc/sudoers.d/justin
```

