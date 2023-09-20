# Changelog

## latest (2023-09-20)

#### Refactorings

* simplify and move to JCasC

Full set of changes: [`7.0.0...latest`](https://github.com/hamlet-io/docker-jenkins/compare/7.0.0...latest)

## 7.0.0 (2022-09-19)

#### New Features

* add github actions for docker build
* update to jdk11 as latest lts
* bump ecs agent version
* add folder properties plugin
* add slack bot user config
* office365 connector
* docs for new variables
* add credentials types
* skip notifications plugin
* add view plugin and github checks
* (plugin): add extended read
* add build number setter
* add new pluings for display
#### Fixes

* imagr name for jenkins
* github secrets update
* remove version req on github plugin
* revert to plugin install script
* add comment on why version is locked
* downagrade github plugin
* ecs config state
* get region before ecs config
* add cobertura plugin
* support env var or metadata lookup for region
* install aws steps for pipeline
* slack config
* constructors for secret and gh app
* skip notifications plugin name
* update ecs agent
* task template structure
* remove outbound webhooks plugin
* only add ecs agent if definition found
#### Refactorings

* find region from ecs cluster arn instead of container
#### Others

* minor revisions of the readme ([#24](https://github.com/hamlet-io/docker-jenkins/issues/24))
