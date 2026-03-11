# Absolutely Minimal Bootstrap Process
 
This results in a tailored environment, but no executable or source outside the container. 

This example macOS approach uses the github command line tool `gh` and IntelliJ.  It assumes you want the devcontainer files from `main` branch:

```
repo=<repo>

gh auth login  
mkdir -p $repo  
cd $repo

filepath=.devcontainer/devenv.yaml
mkdir -p $(dirname $filepath);  
curl -H "Authorization: Bearer $(gh auth token)" -H 'Accept: application/vnd.github.v3.raw' "https://api.github.com/repos/guardian/$repo/contents/$filepath" > $filepath;
```

Customise the devenv.yaml file as desired, then generate devcontainer files and show them to IntelliJ
```
devenv generate  
open -na 'Intellij idea' --args . --line 1 .devcontainer/user/devcontainer.json
```

In the IDE, trust the (minimal) project and click the cube ![gutter-icon.png](gutter-icon.png) in the gutter of .devcontainer/user/devcontainer.json. 
Follow the onscreen instructions as before.
