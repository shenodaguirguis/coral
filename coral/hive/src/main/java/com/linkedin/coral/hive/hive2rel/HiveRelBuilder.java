package com.linkedin.coral.hive.hive2rel;

import com.google.common.collect.ImmutableList;
import com.linkedin.coral.hive.hive2rel.parsetree.Query;
import com.linkedin.coral.hive.hive2rel.parsetree.UnsupportedASTException;
import java.util.ArrayList;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.RelBuilder;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.ParseDriver;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static com.linkedin.coral.hive.hive2rel.HiveSchema.*;


public class HiveRelBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(HiveRelBuilder.class);

  private final HiveContext context;
  private final FrameworkConfig config;
  private final HiveSchema schema;
  private final RelBuilder builder;

  public static HiveRelBuilder create(HiveConf conf) {
    return null;
  }

  private HiveRelBuilder(HiveContext context, HiveSchema schema, FrameworkConfig config) {
    this.context = context;
    this.config = config;
    this.schema = schema;
    builder = RelBuilder.create(config);
  }

  public RelNode process(String sql) {
    try {
      Query query = hiveSqlToQuery(sql);
      return queryToRel(query);
    } catch (ParseException e) {
      throw new RuntimeException(String.format("Failed to parse sql: %s, error: %s", sql, e.getMessage()), e);
    }
  }

  public RelNode queryToRel(Query query) {
    visitTableSource(query.getFrom());
    return null;
  }

  private Query hiveSqlToQuery(String sql) throws ParseException {
    ParseDriver pd = new ParseDriver();
    ASTNode parseTree = pd.parse(sql);
    return Query.create(parseTree);
  }

  private void visitTableSource(ASTNode from) {
    ArrayList<Node> children = from.getChildren();
    checkState(children != null && children.size() > 0);
    ASTNode tabRefNode = (ASTNode) children.get(0);
    if (tabRefNode.getType() == HiveParser.TOK_TABREF) {
      visitTabRef(tabRefNode);
    } else {
      throw new UnsupportedASTException(
          String.format("Conversion of AST %s as data source is not supported", tabRefNode.dump()));
    }
  }

  private void visitTabRef(ASTNode node) {
    ArrayList<Node> tabRefChildren = node.getChildren();
    checkState(tabRefChildren != null && tabRefChildren.size() > 0);
    ASTNode tabNameNode = (ASTNode) tabRefChildren.get(0);
    if (tabNameNode.getType() == HiveParser.TOK_TABNAME) {
      visitTabName(tabNameNode);
    } else {
      throw new UnsupportedASTException(
          String.format("Conversion of node: %s as TABREF child is not supported", tabNameNode.dump())
      );
    }
  }

  private void visitTabName(ASTNode tabNameNode) {
    ArrayList<Node> children = tabNameNode.getChildren();
    checkState(children != null && children.size() > 0);

    ImmutableList.Builder<String> tableNameBuilder = ImmutableList.builder();
    tableNameBuilder.add(ROOT_SCHEMA);
    if (children.size() == 1) {
      tableNameBuilder.add(HiveDbSchema.DEFAULT_DB);
      tableNameBuilder.add(((ASTNode) children.get(0)).getText());
    } else if (children.size() == 2) {
      tableNameBuilder.add(((ASTNode) children.get(0)).getText());
      tableNameBuilder.add(((ASTNode) children.get(1)).getText());
    } else {
      throw new UnsupportedASTException(String.format(
          "Can not handle AST TABNAME node with > 2 children, AST: %s", tabNameNode));
    }

    builder.scan(tableNameBuilder.build());
  }
}
