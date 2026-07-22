package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import io.circe.Json

/** Enables Docker-in-Docker functionality within the development container.
  *
  * This allows applications that use `docker compose` as part of the development workflow to
  * function correctly inside the development container.
  */
private[modules] val dockerInDocker =
  Module(
    name = "docker-in-docker",
    summary = "Enable running Docker containers within the devcontainer",
    enabledByDefault = false,
    contribution = ModuleContribution(
      features =
        Map(
          // Monitor https://github.com/devcontainers/features for latest major release
          "ghcr.io/devcontainers/features/docker-in-docker:3" -> Json.obj(
            "version" -> Json.fromString("latest"),
            // Must be false since Ubuntu 26.04
            "moby"                     -> Json.fromBoolean(false),
            "dockerDashComposeVersion" -> Json.fromString("v2")
          )
        ),
      capAdd = List("SYS_ADMIN"),
      securityOpt = List("seccomp=unconfined")
    )
  )
