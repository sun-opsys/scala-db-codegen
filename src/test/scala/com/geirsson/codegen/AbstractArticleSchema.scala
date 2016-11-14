package com.geirsson.codegen

import java.util.UUID

import com.geirsson.codegen.Tables.Article
import io.getquill._

abstract class AbstractArticleSchema(
  val ctx: JdbcContext[PostgresDialect, SnakeCase]
) {

  import ctx._

  implicit val uuidDecoder: Decoder[UUID] =
    decoder(java.sql.Types.OTHER, (index, row) =>
      UUID.fromString(row.getObject(index).toString)) // database-specific implementation

  implicit val uuidEncoder: Encoder[UUID] =
    encoder(java.sql.Types.OTHER, (index, value, row) =>
      row.setObject(index, value, java.sql.Types.OTHER)) // database-specific implementation

  val articles = quote {
    query[Article]
  }

  def disconnect() =
    ctx.close()

  def articles(limit:Int = 20): List[Article] =
    ctx.run(articles.take(lift(limit)))

  def articleById(id: Article.Id): Option[Article] =
    ctx.run(articles.filter(_.id == lift(id))).headOption

  def createArticle(article: Article) =
    ctx.run(articles.insert(lift(article)))

}


