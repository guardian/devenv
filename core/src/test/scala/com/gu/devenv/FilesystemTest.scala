package com.gu.devenv

import com.gu.devenv.Filesystem.FileSystemStatus
import com.gu.devenv.integration.IntegrationTestHelpers.tempDir
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class FilesystemTest extends AnyFreeSpec with Matchers with TryValues {

  "setupReadme" - {
    "should create a README.md from the template" in {
      tempDir.run { rootDir =>
        val readmeFile = rootDir.resolve("README.md")

        val result = Filesystem.setupReadme(readmeFile).success.value

        result shouldBe FileSystemStatus.Created
        Files.exists(readmeFile) shouldBe true
        Files.readString(readmeFile) should include("# Dev container config")
      }
    }

    "should not overwrite an existing README.md" in {
      tempDir.run { rootDir =>
        val readmeFile     = rootDir.resolve("README.md")
        val customContents = "# My custom readme\n"
        Files.writeString(readmeFile, customContents)

        val result = Filesystem.setupReadme(readmeFile).success.value

        result shouldBe FileSystemStatus.AlreadyExists
        Files.readString(readmeFile) shouldBe customContents
      }
    }
  }
}
