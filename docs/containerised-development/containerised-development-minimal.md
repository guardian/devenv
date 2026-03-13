# Absolutely Minimal Bootstrap Process
 
This results in a tailored environment, but no executable or source outside the container. 

This example macOS approach uses the github command line tool `gh`.  It assumes you want the devcontainer files from `main` branch:

## Setup
```
repo=<repo>           # replace as needed
organisation=guardian # or any other github organisation

gh auth login  
mkdir -p $repo  
cd $repo

filepath=.devcontainer/devenv.yaml
mkdir -p $(dirname $filepath);  
curl -H "Authorization: Bearer $(gh auth token)" -H 'Accept: application/vnd.github.v3.raw' "https://api.github.com/repos/$organisation/$repo/contents/$filepath" > $filepath;
```

Customise the devenv.yaml file as desired, then generate devcontainer files:
```
devenv generate  
```

Open the IDE of your choice:

## IntelliJ

```
open -na 'Intellij Idea' --args . --line 1 .devcontainer/user/devcontainer.json
```
In Intellij, trust the (minimal) project and click the cube ![gutter-icon.png](gutter-icon.png) in the gutter of .devcontainer/user/devcontainer.json as before.

## VS Code

```
open -na 'Visual Studio Code' .
```
In VSCode, the devcontainer files will be autodetected. Follow the onscreen instructions as before.