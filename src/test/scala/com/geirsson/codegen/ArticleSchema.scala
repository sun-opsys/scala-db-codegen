package com.geirsson.codegen

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{SnakeCase, PostgresDialect, JdbcContext}
import Config._

object ArticleSchema extends AbstractArticleSchema (
    {
      val config = new HikariConfig()
      config.setDriverClassName(options.jdbcDriver)
      config.setUsername(options.user)
      config.setPassword(options.password)
      config.setJdbcUrl(options.url  + "?currentSchema=" + options.schema)
      config.setIdleTimeout(1000)
      config.setConnectionTimeout(1000)
      val dataSource = new HikariDataSource(config)
      new JdbcContext[PostgresDialect, SnakeCase](dataSource)
    }

) {





}
