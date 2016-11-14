package com.geirsson.codegen

import com.geirsson.codegen.Tables.Article
import io.getquill._

abstract class AbstractArticleSchema(
  val ctx: JdbcContext[PostgresDialect, SnakeCase]
)  {

  import ctx._

  val articles = quote {
    query[Article]
  }

  def disconnect() =
    ctx.close()

  def articles(limit:Int = 20): List[Article] =
    ctx.run(articles.take(lift(limit)))

  def articleById(id: Article.Id): Option[Article] =
    ctx.run(articles.filter(_.id == lift(id))).headOption

}


