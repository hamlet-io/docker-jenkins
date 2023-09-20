# Jenkins Base Image

This is a base Jenkins image that we use which

- Includes standard plugins that we use
- Disables the startup wizard as we generally expect config via JCasC
- Sets the executor and agent configuration to align with running on-demand container agents

Any further configuration should be handled via CasC or manually updated as required

The image is available on [GHCR](https://ghcr.io/hamlet-io/docker-jenkins) and is based on the [official jenkins image](https://hub.docker.com/r/jenkins/jenkins)
