package com.geirsson.codegen

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.DriverManager

import com.geirsson.codegen.Tables.{Article, TestUser}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import caseapp.CaseApp
import org.scalatest.FunSuite
import io.getquill.{JdbcContext, PostgresDialect, SnakeCase}


class CodegenTest extends FunSuite {
  import Config._

  def structure(code: String): String = {
    import scala.meta._
    code.parse[Source].get.structure
  }

  test("--type-map") {
    val obtained =
      CaseApp.parse[CodegenOptions](
        Seq("--type-map", "numeric,BigDecimal;int8,Long"))
    val expected = Right(
      (CodegenOptions(
         typeMap = TypeMap("numeric" -> "BigDecimal", "int8" -> "Long")),
       Seq.empty[String]))
    assert(obtained === expected)
  }

  ignore("testMain") {

    Class.forName(options.jdbcDriver)
    val conn =
      DriverManager
        .getConnection(options.url, options.user, options.password)
    val stmt = conn.createStatement()

    stmt.executeUpdate(sql)
    conn.close()

    // By reading the file, we assert that the file compiles.
    val tablesPath = {
      val base = Seq(
        "src",
        "test",
        "scala",
        "com",
        "geirsson",
        "codegen",
        "Tables.scala"
      )
      // This project is a subdirectory of a closed source project.
      if (Paths.get("").toAbsolutePath.getFileName.toString == "launaskil") {
        "launaskil-codegen" +: base
      } else {
        base
      }
    }.mkString(File.separator)

    val expected = new String(
      Files.readAllBytes(Paths.get(tablesPath))
    )

    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)

    Codegen.generateTableCode(options, ps)

    val obtained = new String(baos.toByteArray, StandardCharsets.UTF_8)

    println(obtained)

    // compareing the AST trees should suffice ...
    assert(structure(expected) == structure(obtained))
    //    println(expected.trim)

    // unable to get this to work, is there a layout issue, f.ex. with IntelliJ ?
//    assert(expected.trim === obtained.trim)

  }

  ignore("Test JdbcContext and Query Api") {

    val foundArticle =
      ArticleSchema.articleByPk(Article.Id(1))
//
    val createdArticle = Article(
      Article.Id(0),
      Some(Article.ArticleUniqueId(java.util.UUID.fromString("9d5f622e-aa53-11e6-80f5-76304dec7eb8"))),
      Some(TestUser.Id(1)),
      None,
      Article.Title("Moby Dick explained")
    )
//
    val id = TestUser.Id(0)
//

    val user1 =
      ArticleSchema.testUserAll()

    println(user1)

    Thread.sleep(1000)

    println("try again")

    ArticleSchema.createTestUser(
      Tables.TestUser(
        id,
        Some(TestUser.Name("user user"))
    ))

    ArticleSchema.createTestUser(
      Tables.TestUser(
        id,
        Some(TestUser.Name("user user2"))
      ))

    val users = ArticleSchema.testUserAll()

//
//    println(
//      s"""
//
//      $createdArticle
//
//      """
//    )
//
    ArticleSchema
      .createArticle(createdArticle)
//
    val foundSecond =
      ArticleSchema.articleByPk(Article.Id(1))
//
    val articles = ArticleSchema.articleAll()
    ArticleSchema.disconnect()
//
    assert(articles.size == 1)
    assert(foundSecond.nonEmpty)
    assert(users.size == 2)
//    assert(foundArticle.nonEmpty)

  }

  ignore("Test QueryApi code generation") {

    // By reading the file, we assert that the file compiles.
    val tablesPath = {
      val base = Seq(
        "src",
        "test",
        "scala",
        "com",
        "geirsson",
        "codegen",
        "AbstractArticleSchema.scala"
      )
      // This project is a subdirectory of a closed source project.
      if (Paths.get("").toAbsolutePath.getFileName.toString == "launaskil") {
        "launaskil-codegen" +: base
      } else {
        base
      }
    }.mkString(File.separator)

    val expected = new String(
      Files.readAllBytes(Paths.get(tablesPath))
    )

    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)

    Codegen.generateSchemaCode(options, ps)

    val obtained = new String(baos.toByteArray, StandardCharsets.UTF_8)

//    println(obtained)

    assert(structure(expected) == structure(obtained))
//    assert(expected.trim === obtained.trim)
  }

}
