package com.geirsson.codegen

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.DriverManager

import com.zaxxer.hikari.{HikariDataSource, HikariConfig}

import caseapp.CaseApp
import org.scalatest.FunSuite

import io.getquill.{PostgresDialect, SnakeCase, JdbcContext}


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

  test("testMain") {

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
    Codegen.run(options, ps)
    val obtained = new String(baos.toByteArray, StandardCharsets.UTF_8)
    println(obtained)
    assert(structure(expected) == structure(obtained))
    assert(expected.trim === obtained.trim)
  }

  test("test JdbcContext and Query Api") {

    assert(ArticleSchema.articles().isEmpty)

  }

}
