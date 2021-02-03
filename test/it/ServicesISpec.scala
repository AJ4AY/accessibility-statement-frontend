/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it

import java.io.File

import cats.syntax.either._
import org.scalatest.TryValues
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.accessibilitystatementfrontend.config.{AppConfig, ServicesFinder, SourceConfig}
import uk.gov.hmrc.accessibilitystatementfrontend.models.{AccessibilityStatement, Draft}
import uk.gov.hmrc.accessibilitystatementfrontend.parsers.AccessibilityStatementParser

import scala.util.Try

class ServicesISpec extends PlaySpec with GuiceOneAppPerSuite with TryValues {
  private val statementParser = new AccessibilityStatementParser

  "parsing the configuration files" should {
    val sourceConfig   = app.injector.instanceOf[SourceConfig]
    val servicesFinder = app.injector.instanceOf[ServicesFinder]

    servicesFinder.findAll().foreach { (service: String) =>
      val source       = sourceConfig.statementSource(service)
      val statementTry =
        Try(statementParser.parseFromSource(source).valueOr(throw _))

      s"enforce a correctly formatted accessibility statement yaml file for $service" in {
        statementTry must be a 'success
      }

      s"enforce statement not contain missing milestones for $service" in {
        val statement: AccessibilityStatement = statementTry.get
        val hasMilestones                     = statement.milestones.getOrElse(Seq.empty).nonEmpty
        hasMilestones || statement.isNonCompliant || statement.isFullyCompliant must be(
          true
        )
      }

      s"enforce serviceDomain in the format of aaaa.bbbb.cccc for $service" in {
        val domainRegex                       = "([a-z0-9-]*[\\.]*)*[a-z0-9]*"
        val statement: AccessibilityStatement = statementTry.get
        statement.serviceDomain.matches(domainRegex) must be(true)
      }

      s"enforce serviceUrl starting with / for $service" in {
        val statement: AccessibilityStatement = statementTry.get
        statement.serviceUrl.startsWith("/") must be(true)
      }

      s"enforce serviceDescription exists for public statement $service" in {
        val statement: AccessibilityStatement = statementTry.get
        statement.serviceDescription.trim.length > 0 || statement.statementVisibility == Draft must be(
          true
        )
      }
    }
  }

  "the file names in the directory" should {
    val servicesFinder = app.injector.instanceOf[ServicesFinder]
    val appConfig      = app.injector.instanceOf[AppConfig]

    val servicesDirectoryPath =
      new File(
        getClass.getClassLoader.getResource(appConfig.servicesDirectory).getPath
      )
    val fileNames             =
      servicesDirectoryPath
        .listFiles()
        .toSeq
        .filter(_.isFile)
        .map(_.getName)
        .sorted

    fileNames.foreach { fileName =>
      s"end in extension .yml for $fileName" in {
        fileName.endsWith(".yml") must be(true)
      }

      s"should match to a service returned by the service finder for $fileName" in {
        val serviceName = fileName.split("\\.").head
        val services    = servicesFinder.findAll()
        services.contains(serviceName) must be(true)
      }
    }
  }
}
