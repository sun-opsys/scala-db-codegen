package com.geirsson.codegen
import java.util.{Date, UUID}

import com.geirsson.codegen.Tables.Article
import com.geirsson.codegen.Tables.TestUser
import io.getquill._
import java.util.UUID

abstract class AbstractArticleSchema(
  val ctx: JdbcContext[PostgresDialect, SnakeCase]
) {
  import ctx._

//  implicit val encodeArticleUniqueId = MappedEncoding[Article.ArticleUniqueId, UUID](_.value)
//  implicit val decodeArticleUniqueId = MappedEncoding[UUID, Article.ArticleUniqueId ](uuid => Article.ArticleUniqueId(uuid))

  implicit val uuidDecoder: Decoder[UUID] =
    decoder(java.sql.Types.OTHER, (index, row) => UUID.fromString(row.getObject(index).toString)) // database-specific implementation

  implicit val uuidEncoder: Encoder[UUID] =
    encoder(java.sql.Types.OTHER, (index, value, row) => row.setObject(index, value, java.sql.Types.OTHER)) // database-specific implementation

  val article = quote {
    query[Article]
  }

  def articleAll(limit: Int = 20): List[Article] = ctx.transaction {
    ctx.run(article.take(lift(limit)))
  }

  def createArticle(articleInstance: Article) = ctx.transaction {
    ctx.run(article.insert(lift(articleInstance)).returning(_.id))
  }

  def articleByPk(id: Article.Id): Option[Article] =
    ctx.run(article.filter(_.id == lift(id))).headOption

  def articleByArticleUniqueId(articleUniqueId: Option[Article.ArticleUniqueId]): List[Article] = ctx.transaction {
    ctx.run(article.filter(_.articleUniqueId == lift(articleUniqueId)))
  }

  def articleByIsPublished(isPublished: Option[Article.IsPublished]): List[Article] = ctx.transaction {
    ctx.run(article.filter(_.isPublished == lift(isPublished)))
  }

  def articleByTitle(title: Article.Title): List[Article] = ctx.transaction {
    ctx.run(article.filter(_.title == lift(title)))
  }

  implicit class LikeArticleTitle(s1: Article.Title) {
    def like(s2: String) = quote(infix"$s1 like $s2".as[Boolean])
  }

  def articleSearch(title: Article.Title): List[Article] = ctx.transaction {
    ctx.run(article.filter(t => LikeArticleTitle(t.title) like lift(s"%$title%")))
  }

  val testUser = quote {
    query[TestUser]
  }

  def testUserAll(limit: Int = 20): List[TestUser] = ctx.transaction {
    ctx.run(testUser.take(lift(limit)))
  }

  def createTestUser(testUserInstance: TestUser) = ctx.transaction {
   ctx.run(testUser.insert(lift(testUserInstance)).returning(_.id))
  }

  def testUserByPk(id: TestUser.Id): Option[TestUser] =
    ctx.run(testUser.filter(_.id == lift(id))).headOption

  def testUserByName(name: Option[TestUser.Name]): List[TestUser] = ctx.transaction {
    ctx.run(testUser.filter(_.name == lift(name)))
  }

  implicit class LikeTestUserName(s1: Option[TestUser.Name]) {
    def like(s2: String) = quote(infix"$s1 like $s2".as[Boolean])
  }

  def testUserSearch(name: TestUser.Name): List[TestUser] = ctx.transaction {
    ctx.run(testUser.filter(n => LikeTestUserName(n.name) like lift(s"%$name%")))
  }

  def disconnect() =
    ctx.close()

}
