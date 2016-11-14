package com.geirsson.codegen

/**
  * Created by apollo on 11/11/16.
  */
object Config {

  val options = CodegenOptions(
    schema = s"scala_db_codegen",
    `package` = "com.geirsson.codegen"
  )

  val sql =
    s"""|drop schema if exists ${options.schema} cascade;
          |create schema ${options.schema};
          |SET search_path TO ${options.schema};
          |
          |create table test_user(
          |  id integer not null,
          |  name varchar(255),
          |  primary key (id)
          |);
          |
          |create table article(
          |  id integer not null,
          |  article_unique_id uuid,
          |  author_id integer,
          |  is_published boolean,
          |  primary key (id)
          |);
          |
          |ALTER TABLE article
          |  ADD CONSTRAINT author_id_fk
          |  FOREIGN KEY (author_id)
          |  REFERENCES test_user (id);
          |
          |INSERT INTO test_user VALUES(
          | 1,
          | 'Mark Twain'
          |);
          |
          |INSERT INTO article VALUES(
          |1,
          |'9d5f622e-aa53-11e6-80f5-76304dec7eb7',
          |1,
          |true
          |);
      """.stripMargin

}
