
package com.geirsson.codegen
import java.util.{Date, UUID}
import io.getquill.Embedded

object Tables {
  /////////////////////////////////////////////////////
  // Article
  /////////////////////////////////////////////////////
  case class Article(id: Article.Id,
    articleUniqueId: Option[Article.ArticleUniqueId],
    authorId: Option[TestUser.Id],
    isPublished: Option[Article.IsPublished])
  object Article {
    def create(id: Int, articleUniqueId: Option[UUID], authorId: Option[Int], isPublished: Option[Boolean]): Article = {
      Article(Id(id),
        articleUniqueId.map(ArticleUniqueId.apply),
        authorId.map(TestUser.Id.apply),
        isPublished.map(IsPublished.apply))
    }
    case class Id(value: Int)               extends AnyVal
    case class ArticleUniqueId(value: UUID) extends AnyVal

    case class IsPublished(value: Boolean)  extends AnyVal
  }

  /////////////////////////////////////////////////////
  // TestUser
  /////////////////////////////////////////////////////
  case class TestUser(id: TestUser.Id, name: Option[TestUser.Name])
  object TestUser {
    def create(id: Int, name: Option[String]): TestUser = {
      TestUser(Id(id), name.map(Name.apply))
    }
    case class Id(value: Int)      extends AnyVal
    case class Name(value: String) extends AnyVal
  }
}