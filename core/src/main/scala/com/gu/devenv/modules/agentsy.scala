package com.gu.devenv.modules

import com.gu.devenv.Command
import com.gu.devenv.modules.Modules.{Module, ModuleAdoption, ModuleContribution}

import scala.util.Try

/** Installs Agentsy in the development container.
  *
  * Agentsy provides features that support engineers' use of agentic AI.
  *
  * See: https://github.com/guardian/agentsy
  */
private[modules] def agentsy: Try[Module] =
  for {
    postCreateScript <- Command.fromResourceScript("agentsyPostCreateCommand.sh")
  } yield Module(
    name = "agentsy",
    summary = "Install Agentsy for managing agentic AI tooling",
    adoption = ModuleAdoption.Experimental,
    contribution = ModuleContribution(
      postCreateCommands = List(postCreateScript)
    )
  )
