package com.geirsson.codegen

import java.io.{File, PrintStream}
import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager, ResultSet}

import caseapp.{AppOf, _}
import com.typesafe.scalalogging.Logger
import io.getquill.NamingStrategy
import org.scalafmt.{FormatResult, Scalafmt, ScalafmtStyle}

case class Error(msg: String) extends Exception(msg)

@AppName("db-codegen")
@AppVersion(Versions.nightly)
@ProgName("db-codegen")
case class CodegenOptions(
    @HelpMessage("user on database server") user: String = "postgres",
    @HelpMessage("password for user on database server") password: String =
      "postgres",
    @HelpMessage("jdbc url") url: String = "jdbc:postgresql:postgres",
    @HelpMessage("schema on database") schema: String = "public",
    @HelpMessage("only tested with postgresql") jdbcDriver: String =
      "org.postgresql.Driver",
    @HelpMessage(
      "top level imports of generated file"
    ) imports: String =
      """|import java.util.{Date, UUID}
      """.stripMargin,
    @HelpMessage(
      "package name for generated classes"
    ) `package`: String = "tables",
    @HelpMessage(
      "Which types should write to which types? Format is: numeric,BigDecimal;int8,Long;..."
    ) typeMap: TypeMap = TypeMap.default,
    @HelpMessage(
      "Do not generate classes for these tables."
    ) excludedTables: List[String] = List("schema_version"),
    @HelpMessage(
      "Write generated queries to this filename. Prints to stdout if not set"
    ) queryFile: Option[String],
    @HelpMessage(
      "Write generated case class rows to this filename. Prints to stdout if not set."
    ) caseClassRowFile: Option[String] = None
) extends App {

  Codegen.cliRun(this)

}

case class Codegen(options: CodegenOptions, namingStrategy: NamingStrategy) {
  import Codegen._

  val excludedTables = options.excludedTables.toSet
  val columnType2scalaType = options.typeMap.pairs.toMap

  def results(resultSet: ResultSet): Iterator[ResultSet] = {
    new Iterator[ResultSet] {
      def hasNext = resultSet.next()
      def next() = resultSet
    }
  }

  def getForeignKeys(db: Connection): Set[ForeignKey] = {

    val foreignKeys =
      db.getMetaData.getExportedKeys(null, options.schema, null)

    results(foreignKeys).map { row =>
      ForeignKey(
        from = SimpleColumn(
          tableName = row.getString(FK_TABLE_NAME),
          columnName = row.getString(FK_COLUMN_NAME)
        ),
        to = SimpleColumn(
          tableName = row.getString(PK_TABLE_NAME),
          columnName = row.getString(PK_COLUMN_NAME)
        )
      )
    }.toSet

  }

  def warn(msg: String): Unit = {
    System.err.println(s"[${Console.YELLOW}warn${Console.RESET}] $msg")
  }

  def getSchema(tables: Seq[Table]) = {
    Schema(tables)
  }

  def getTables(db: Connection, foreignKeys: Set[ForeignKey]): Seq[Table] = {
    val rs: ResultSet =
      db.getMetaData.getTables(null, options.schema, "%", Array("TABLE"))
    results(rs).flatMap { row =>
      val name = row.getString(TABLE_NAME)
      if (!excludedTables.contains(name)) {
        val columns = getColumns(db, name, foreignKeys)
        val mappedColumns = columns.filter(_.isRight).map(_.right.get)
        val unmappedColumns = columns.filter(_.isLeft).map(_.left.get)
        if (unmappedColumns.nonEmpty)
          warn(s"The following columns from table $name need a mapping: $unmappedColumns")
        Some(Table(
          name,
          mappedColumns
        ))
      } else {
        None
      }
    }.toVector
  }

  def getColumns(
    db: Connection,
    tableName: String,
    foreignKeys: Set[ForeignKey]
  ): Seq[Either[String, Column]] = {

    val primaryKeys:
    Set[String] = getPrimaryKeys(db, tableName)

    val cols =
      db.getMetaData.getColumns(null, options.schema, tableName, null)

    val indexes =
      db.getMetaData.getIndexInfo(null, options.schema, tableName, false, false)

    // TODO: this needs to be converted into a hashmap

    val cci = results(indexes).map { index =>
       indexes.getString(COLUMN_NAME)
    }.toList

    results(cols).map { row =>

      val colName = cols.getString(COLUMN_NAME)
      val simpleColumn = SimpleColumn(tableName, colName)
      val ref = foreignKeys.find(_.from == simpleColumn).map(_.to)
      val isSearchable = if(cci.contains(colName)) true else false
      val colType = cols.getString(TYPE_NAME)

      columnType2scalaType.get(colType).map { scalaType =>
        Right(Column(
          tableName,
          colName,
          colType,
          scalaType,
          cols.getBoolean(NULLABLE),
          primaryKeys contains cols.getString(COLUMN_NAME),
          isSearchable,
          ref
        ))
      }.getOrElse(Left(colType))

    }.toVector

  }

  def getPrimaryKeys(db: Connection, tableName: String): Set[String] = {
    val sb = Set.newBuilder[String]
    val primaryKeys = db.getMetaData.getPrimaryKeys(null, null, tableName)
    while (primaryKeys.next()) {
      sb += primaryKeys.getString(COLUMN_NAME)
    }
    sb.result()
  }

  def tables2code(tables: Seq[Table],
                  namingStrategy: NamingStrategy,
                  options: CodegenOptions) = {
    val body = tables.map(_.toCode).mkString("\n\n")
    s"""|package ${options.`package`}
        |${options.imports}
        |
        |object Tables {
        |$body
        |}
     """.stripMargin
  }

  def schema2code(
    schema: Schema,
    namingStrategy: NamingStrategy,
    options:CodegenOptions
  ) = {

    val body = schema.toCode

    s"""|package ${options.`package`}
        |${options.imports}
        |${schema.tables.map(t => s"import ${options.`package`}.Tables.${namingStrategy.table(t.name)}").mkString("\n")}
        |import io.getquill._
        |import java.util.UUID
        |
        |abstract class AbstractArticleSchema(
        |  val ctx: JdbcContext[PostgresDialect, SnakeCase]
        |){
        |  $body
        |}
    """.stripMargin
  }

  case class ForeignKey(from: SimpleColumn, to: SimpleColumn)

  val logger = Logger(this.getClass)

  case class SimpleColumn(tableName: String, columnName: String) {
    def toType =
      s"${namingStrategy.table(tableName)}.${namingStrategy.table(columnName)}"
  }

  case class Column(
    tableName: String,
    columnName: String,
    columnType: String,
    scalaType: String,
    nullable: Boolean,
    isPrimaryKey: Boolean,
    isIndexed: Boolean,
    references: Option[SimpleColumn]
  ) {

    def scalaOptionType =
      makeOption(scalaType)

    def makeOption(typ: String): String = {
      if (nullable) s"Option[$typ]"
      else typ
    }

    def toType: String = this.toSimple.toType

    def toArg(namingStrategy: NamingStrategy, tableName: String): String = {
      s"${namingStrategy.column(columnName)}: ${makeOption(this.toType)}"
    }

    def toSimple =
      references.getOrElse(SimpleColumn(tableName, columnName))

    def toClass: String =
      s"case class ${namingStrategy.table(columnName)}(value: $scalaType) extends AnyVal"

//      scalaType match {
//        case "UUID" => s"case class ${namingStrategy.table(columnName)}(value: $scalaType) extends Embedded"
//        case _ => s"case class ${namingStrategy.table(columnName)}(value: $scalaType) extends AnyVal"
//      }

  }

  case class Table(name: String, columns: Seq[Column]) {
    def toCode: String = {

      val scalaName = namingStrategy.table(name)

      val args = columns.map(_.toArg(namingStrategy, scalaName)).mkString(", ")

      val applyArgs = columns.map { column =>
        s"${namingStrategy.column(column.columnName)}: ${column.scalaOptionType}"
      }.mkString(", ")

      val applyArgNames = columns.map { column =>
        val typName = if (column.references.nonEmpty) {
          column.toType
        } else {
          namingStrategy.table(column.columnName)
        }
        if (column.nullable) {
          s"${namingStrategy.column(column.columnName)}.map($typName.apply)"
        } else {
          s"$typName(${namingStrategy.column(column.columnName)})"
        }
      }.mkString(", ")

      val classes =
        columns.withFilter(_.references.isEmpty).map(_.toClass).mkString("\n")

      s"""|  /////////////////////////////////////////////////////
          |  // $scalaName
          |  /////////////////////////////////////////////////////
          |case class $scalaName($args)
          |object $scalaName {
          |  def create($applyArgs): $scalaName = {
          |    $scalaName($applyArgNames)
          |  }
          |$classes
          |}""".stripMargin
    }
  }

  def capFirst(s: String) =
    if (s.isEmpty) ""
    else
      s(0).toUpper + s.substring(1, s.length)

  def lowerFirst(s: String) =
    if (s.isEmpty) ""
    else
      s(0).toLower + s.substring(1, s.length)

  case class Query(
    tableName: String,
    table: Table
  ) {
    def toQueries: String = {

      val funcName =
        lowerFirst(tableName)

      def valName(col: Column) =
        namingStrategy.column(col.columnName)

      val baseQuery =
        s"""|def ${lowerFirst(tableName)}All(limit: Int = 20): List[$tableName] = ctx.transaction {
            |  ctx.run($funcName.take(lift(limit)))
            |}
      """.stripMargin

      val primKeys =
        table.columns.count(_.isPrimaryKey)

      val primaryKeyQuery =
        if(primKeys == 0)
          ""
        else if (primKeys == 1)
          table.columns.find(_.isPrimaryKey).map { col =>
            s"""|def ${funcName}ByPk(${namingStrategy.column(col.columnName)}:${col.toType}):Option[$tableName] =
                | ctx.run($funcName.filter(_.${valName(col)} == lift(${valName(col)}))).headOption
            """.stripMargin
          }.mkString("\n")
      else
          ""

      val create =
        s"""|def create$tableName(${funcName}Instance: $tableName) = ctx.transaction {
            |   ctx.run($funcName.insert(lift(${funcName}Instance)).returning(_.id.value))
            |}
        """.stripMargin

      val foreignKey =
        table.columns.filter(_.references.isDefined)

      val searchFields = for {
        column <- table.columns
        if column.isIndexed && column.scalaType == "String"
      } yield

        if(!column.nullable) {
          s"""
          |implicit class Like${tableName}${capFirst(valName(column))}(s1: ${column.toType}) {
          |  def like(s2: String) = quote(infix"$$s1 like $$s2".as[Boolean])
          |}

          |def ${funcName}Search(${valName(column)} : ${column.toType}): List[$tableName] = ctx.transaction {
          |  ctx.run($funcName.filter(${valName(column)(0)} => Like${tableName}${capFirst(valName(column))}(${valName(column)(0)}.${valName(column)}) like lift(s"%$$${valName(column)}%")))
          |}
          """.stripMargin
        } else {
          s"""
          |implicit class Like${tableName}${capFirst(valName(column))}(s1: Option[${column.toType}]) {
          |  def like(s2: String) = quote(infix"$$s1 like $$s2".as[Boolean])
          |}

          |def ${funcName}Search(${valName(column)} : ${column.toType}): List[$tableName] = ctx.transaction {
          |  ctx.run($funcName.filter(${valName(column)(0)} => Like${tableName}${capFirst(valName(column))}(${valName(column)(0)}.${valName(column)}) like lift(s"%$$${valName(column)}%")))
          |}
          """.stripMargin
        }

      val columnQueries =
        table.columns.filter(col =>
          !col.isPrimaryKey && col.references.isEmpty
        ).map { col =>


          if (!col.nullable)
            s"""|def ${funcName}By${capFirst(valName(col))}(${valName(col)} : ${col.toType}): List[$tableName] = ctx.transaction {
              | ctx.run($funcName.filter(_.${valName(col)} == lift(${valName(col)})))
              |}
        """.stripMargin
          else
            s"""|def ${funcName}By${capFirst(valName(col))}(${valName(col)} : Option[${col.toType}]): List[$tableName] = ctx.transaction {
              | ctx.run($funcName.filter(_.${valName(col)} == lift(${valName(col)})))
              |}
            """.stripMargin

      }.mkString("\n\n")

      s"""| $baseQuery
          |
          | $create
          |
          | $primaryKeyQuery
          |
          | $columnQueries
          |
          | ${searchFields.mkString("\n")}
      """.stripMargin
    }
  }


  case class Schema(tables: Seq[Table]) {

    def toCode:String = {

      val tableNames = tables.map { table =>
        namingStrategy.table(table.name)
      }

      val findables:Map[String, String] =
        (for {
          table <- tables
          columns <- table.columns
          tableName = namingStrategy.table(table.name)
        } yield
           tableName -> Query(tableName, table).toQueries
        ).toMap[String, String]

      val tableContexts = tableNames.map { tN =>
        s"""|val ${lowerFirst(tN)}= quote {
            |  query[$tN]
            |}
            |
            |${findables(tN)}
        """.stripMargin
      }

      s"""
      import ctx._

      implicit val uuidDecoder: Decoder[UUID] =
        decoder(java.sql.Types.OTHER, (index, row) =>
          UUID.fromString(row.getObject(index).toString)) // database-specific implementation

      implicit val uuidEncoder: Encoder[UUID] =
         encoder(java.sql.Types.OTHER, (index, value, row) =>
           row.setObject(index, value, java.sql.Types.OTHER)) // database-specific implementation

      ${tableContexts.mkString("\n")}

      def disconnect() =
        ctx.close()
      """
    }

  }

}

object Codegen extends AppOf[CodegenOptions] {
  val TABLE_NAME = "TABLE_NAME"
  val INDEX_NAME = "INDEX_NAME"
  val COLUMN_NAME = "COLUMN_NAME"
  val TYPE_NAME = "TYPE_NAME"
  val NULLABLE = "NULLABLE"
  val PK_NAME = "pk_name"
  val FK_TABLE_NAME = "fktable_name"
  val FK_COLUMN_NAME = "fkcolumn_name"
  val PK_TABLE_NAME = "pktable_name"
  val PK_COLUMN_NAME = "pkcolumn_name"

  def debugPrintColumnLabels(rs: ResultSet): Unit = {
    (1 to rs.getMetaData.getColumnCount).foreach { i =>
      println(i -> rs.getMetaData.getColumnLabel(i))
    }
  }

  def cliRun(
    codegenOptions: CodegenOptions,
    outstream: PrintStream = System.out
  ): Unit = {
    try {
      generateTableCode(codegenOptions, outstream)
    } catch {
      case Error(msg) =>
        System.err.println(msg)
        System.exit(1)
    }
  }

  def generateTableCode(
    codegenOptions: CodegenOptions,
    outstream: PrintStream = System.out
  ): Unit = {

    codegenOptions.caseClassRowFile.foreach { x =>
      outstream.println("Starting...")
    }

    val startTime = System.currentTimeMillis()

    Class.forName(codegenOptions.jdbcDriver)

    val db: Connection =
      DriverManager.getConnection(codegenOptions.url,
                                  codegenOptions.user,
                                  codegenOptions.password)

    val codegen = Codegen(codegenOptions, SnakeCaseReverse)

    val foreignKeys = codegen.getForeignKeys(db)

    val tables = codegen.getTables(db, foreignKeys)

    val generatedTableCode =
      codegen.tables2code(tables, SnakeCaseReverse, codegenOptions)

    val codeStyle = ScalafmtStyle.defaultWithAlign.copy(maxColumn = 120)

    val tableCode = Scalafmt.format(generatedTableCode, style = codeStyle) match {
      case FormatResult.Success(x) => x
      case _ => generatedTableCode
    }

    codegenOptions.caseClassRowFile match {
      case Some(uri) =>
        Files.write(Paths.get(new File(uri).toURI), tableCode.getBytes)
        println(
          s"Done! Wrote to $uri (${System.currentTimeMillis() - startTime}ms)")
      case _ =>
        outstream.println(tableCode)
    }

    db.close()

  }

  def generateSchemaCode(
    codegenOptions: CodegenOptions,
    outstream: PrintStream = System.out
  ):Unit = {

    codegenOptions.queryFile.foreach { x =>
      outstream.println("Starting...")
    }

    val startTime = System.currentTimeMillis()

    Class.forName(codegenOptions.jdbcDriver)

    val db: Connection =
      DriverManager.getConnection(codegenOptions.url,
        codegenOptions.user,
        codegenOptions.password)

    val codegen = Codegen(codegenOptions, SnakeCaseReverse)

    val foreignKeys = codegen.getForeignKeys(db)

    val tables = codegen.getTables(db, foreignKeys)

    val schema = codegen.getSchema(tables)

    val generatedTableCode =
      codegen.schema2code(schema, SnakeCaseReverse, codegenOptions)

    val codeStyle = ScalafmtStyle.defaultWithAlign.copy(maxColumn = 120)

    val tableCode = Scalafmt.format(generatedTableCode, style = codeStyle) match {
      case FormatResult.Success(x) => x
      case _ => generatedTableCode
    }

    codegenOptions.queryFile match {
      case Some(uri) =>
        Files.write(Paths.get(new File(uri).toURI), tableCode.getBytes)
        println(
          s"Done! Wrote to $uri (${System.currentTimeMillis() - startTime}ms)"
        )
      case _ =>
        outstream.println(tableCode)
    }

    db.close()

  }
}
