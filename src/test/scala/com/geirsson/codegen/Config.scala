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
          |CREATE SEQUENCE test_user_seq;
          |
          |create table test_user(
          |  id integer not null default nextval('test_user_seq'),
          |  name varchar(255),
          |  primary key (id)
          |);
          |
          |ALTER SEQUENCE test_user_seq OWNED BY test_user.id;
          |-- ALTER TABLE test_user ADD COLUMN id SERIAL PRIMARY KEY;
          |
          |create table article(
          |  id serial primary key,
          |  article_unique_id uuid,
          |  author_id integer,
          |  is_published boolean,
          |  title varchar(255) not null
          |);
          |
          |--  ALTER TABLE article ADD COLUMN id SERIAL PRIMARY KEY;
          |
          |ALTER TABLE article
          |  ADD CONSTRAINT author_id_fk
          |  FOREIGN KEY (author_id)
          |  REFERENCES test_user (id);
          |
          |
          |CREATE UNIQUE INDEX test_user_name_idx ON test_user (name);
          |CREATE UNIQUE INDEX article_title_idx ON article (title);
      """.stripMargin

}

/*
          |INSERT INTO test_user VALUES(
          | 1,
          | 'Mark Twain'
          |);
          |
          |INSERT INTO article VALUES(
          |'1',
          |'9d5f622e-aa53-11e6-80f5-76304dec7eb7',
          |1,
          |true,
          |'Moby Dick explained'
          |);
 */
