package com.geirsson.codegen

import com.geirsson.codegen.Tables.Article
import io.getquill.{JdbcContext, PostgresDialect, SnakeCase}

/**
  * Created by tomsorlie on 11/14/16.
  */
abstract class ArticleEmbedded(
  val ctx: JdbcContext[PostgresDialect, SnakeCase]
) {
  import ctx._

/*  case class Article(
    id: Integer,
    articleUniqueId:
  )

  case class TestUser(

  )*/

}
