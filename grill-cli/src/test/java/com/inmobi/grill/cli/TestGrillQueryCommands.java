package com.inmobi.grill.cli;

/*
 * #%L
 * Grill CLI
 * %%
 * Copyright (C) 2014 Inmobi
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import com.inmobi.grill.api.query.QueryHandle;
import com.inmobi.grill.api.query.QueryStatus;
import com.inmobi.grill.cli.commands.GrillCubeCommands;
import com.inmobi.grill.cli.commands.GrillQueryCommands;
import com.inmobi.grill.client.GrillClient;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.ws.rs.BadRequestException;
import java.io.*;
import java.net.URL;
import java.util.UUID;

public class TestGrillQueryCommands extends GrillCliApplicationTest {

  private static final Logger LOG = LoggerFactory.getLogger(
      TestGrillQueryCommands.class);

  private GrillClient client;

  private static String explainPlan = "TOK_QUERY\n" +
      "   TOK_FROM\n" +
      "      TOK_TABREF\n" +
      "         TOK_TABNAME\n" +
      "            local_dim_table\n" +
      "         test_dim\n" +
      "   TOK_INSERT\n" +
      "      TOK_DESTINATION\n" +
      "         TOK_DIR\n" +
      "            TOK_TMP_FILE\n" +
      "      TOK_SELECT\n" +
      "         TOK_SELEXPR\n" +
      "            .\n" +
      "               TOK_TABLE_OR_COL\n" +
      "                  test_dim\n" +
      "               id\n" +
      "         TOK_SELEXPR\n" +
      "            .\n" +
      "               TOK_TABLE_OR_COL\n" +
      "                  test_dim\n" +
      "               name\n" +
      "      TOK_WHERE\n" +
      "         =\n" +
      "            .\n" +
      "               TOK_TABLE_OR_COL\n" +
      "                  test_dim\n" +
      "               dt\n" +
      "            'latest'";

  @Test
  public void testQueryCommands() throws Exception {
    client = new GrillClient();
    client.setConnectionParam("grill.persistent.resultset.indriver", "false");
    setup(client);
    GrillQueryCommands qCom = new GrillQueryCommands();
    qCom.setClient(client);
    testExecuteSyncQuery(qCom);
    testExecuteAsyncQuery(qCom);
    testExplainQuery(qCom);
    testPreparedQuery(qCom);
    testShowPersistentResultSet(qCom);
    testPurgedFinishedResultSet(qCom);
  }

  private void testPreparedQuery(GrillQueryCommands qCom) throws Exception {
    long submitTime = System.currentTimeMillis();
    String sql = "cube select id, name from test_dim";
    String result = qCom.getAllPreparedQueries("all", "testPreparedName", submitTime, Long.MAX_VALUE);

    Assert.assertEquals("No prepared queries", result);
    final String qh = qCom.prepare(sql, "testPreparedName");
    result = qCom.getAllPreparedQueries("all", "testPreparedName", submitTime, System.currentTimeMillis());
    Assert.assertEquals(qh, result);

    result = qCom.getPreparedStatus(qh);
    Assert.assertTrue(result.contains("User query:cube select id, name from test_dim"));
    Assert.assertTrue(result.contains(qh));

    result = qCom.executePreparedQuery(qh, false, "testPrepQuery1");

    LOG.warn("XXXXXX Prepared query sync result is  " + result);
    Assert.assertTrue(result.contains("1\tfirst"));

    String handle = qCom.executePreparedQuery(qh, true, "testPrepQuery2");
    LOG.debug("Perpared query handle is   " + handle);
    while(!client.getQueryStatus(handle).isFinished()) {
      Thread.sleep(5000);
    }
    String status = qCom.getStatus(handle);
    LOG.debug("Prepared Query Status is  " + status);
    Assert.assertTrue(status.contains("Status : SUCCESSFUL"));

    result = qCom.getQueryResults(handle);
    LOG.debug("Prepared Query Result is  " + result);
    Assert.assertTrue(result.contains("1\tfirst"));

    result = qCom.destroyPreparedQuery(qh);

    LOG.debug("destroy result is " + result);
    Assert.assertEquals("Successfully destroyed " + qh, result);

    final String qh2 = qCom.explainAndPrepare(sql, "testPrepQuery3");
    Assert.assertTrue(qh2.contains(explainPlan));
    String handles = qCom.getAllPreparedQueries("all", "testPrepQuery3", -1, Long.MAX_VALUE);
    Assert.assertFalse(handles.contains("No prepared queries"), handles);

    String handles2 = qCom.getAllPreparedQueries("all", "testPrepQuery3", -1, submitTime - 1);
    Assert.assertFalse(handles2.contains(qh), handles2);
  }

  private void testExplainQuery(GrillQueryCommands qCom) throws Exception {
    String sql = "cube select id, name from test_dim";
    String result = qCom.explainQuery(sql, "");

    LOG.debug(result);
    Assert.assertTrue(result.contains(explainPlan));

  }

  private void testExecuteAsyncQuery(GrillQueryCommands qCom) throws Exception {
    System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
    String sql = "cube select id,name from test_dim";
    long submitTime = System.currentTimeMillis();
    String qh = qCom.executeQuery(sql, true, "testQuery1");
    String user =
      qCom.getClient().getGrillStatement(new QueryHandle(UUID.fromString(qh))).getQuery().getSubmittedUser();
    String result = qCom.getAllQueries("", "testQuery1", user, -1, Long.MAX_VALUE);
    //this is because previous query has run two query handle will be there
    Assert.assertTrue(result.contains(qh), result);

    // Check that query name searching is 'ilike'
    String result2 = qCom.getAllQueries("", "query", "all", -1, Long.MAX_VALUE);
    Assert.assertTrue(result2.contains(qh), result2);


    while(!client.getQueryStatus(qh).isFinished()) {
      Thread.sleep(5000);
    }

    Assert.assertTrue(qCom.getStatus(qh).contains("Status : SUCCESSFUL"));

    result = qCom.getQueryResults(qh);
    Assert.assertTrue(result.contains("1\tfirst"));
    //Kill query is not tested as there is no deterministic way of killing a query

    result = qCom.getAllQueries("SUCCESSFUL","", "all", -1, Long.MAX_VALUE);
    Assert.assertTrue(result.contains(qh), result);

    result = qCom.getAllQueries("FAILED","", "all", -1, Long.MAX_VALUE);
    if (!result.contains("No queries")) {
      // Make sure valid query handles are returned
      String[] handles = StringUtils.split(result, "\n");
      for (String handle : handles) {
        QueryHandle.fromString(handle.trim());
      }
    }

    String queryName =
      client.getGrillStatement(new QueryHandle(UUID.fromString(qh))).getQuery().getQueryName();
    Assert.assertTrue("testQuery1".equalsIgnoreCase(queryName), queryName);
    result = qCom.getAllQueries("", "", "", submitTime, System.currentTimeMillis());
    Assert.assertTrue(result.contains(qh), result);

    result = qCom.getAllQueries("","fooBar", "all", submitTime, System.currentTimeMillis());
    Assert.assertTrue(result.contains("No queries"), result);

    result = qCom.getAllQueries("SUCCESSFUL","", "all", submitTime, System.currentTimeMillis());
    Assert.assertTrue(result.contains(qh));

    result = qCom.getAllQueries("SUCCESSFUL","", "all", submitTime - 5000, submitTime - 1);
    // should not give query since its not in the range
    Assert.assertFalse(result.contains(qh));

    try {
      // Should fail with bad request since fromDate > toDate
      result = qCom.getAllQueries("SUCCESSFUL", "", "all", submitTime + 5000, submitTime);
      Assert.fail("Call should have failed with BadRequestException, instead got " + result);
    } catch (BadRequestException exc) {
      // pass
    }

    System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
  }


  public void setup(GrillClient client) throws Exception {
    GrillCubeCommands command = new GrillCubeCommands();
    command.setClient(client);

    LOG.debug("Starting to test cube commands");
    URL cubeSpec =
        TestGrillQueryCommands.class.getClassLoader().getResource("sample-cube.xml");
    command.createCube(new File(cubeSpec.toURI()).getAbsolutePath());
    TestGrillDimensionCommands.createDimension();
    TestGrillDimensionTableCommands.addDim1Table("dim_table",
        "dim_table.xml", "dim_table_storage.xml", "local");

    URL dataFile =
        TestGrillQueryCommands.class.getClassLoader().getResource("data.txt");

    QueryHandle qh = client.executeQueryAsynch("LOAD DATA LOCAL INPATH '"
        + new File(dataFile.toURI()).getAbsolutePath()+
        "' OVERWRITE INTO TABLE local_dim_table partition(dt='latest')", null);

    while(!client.getQueryStatus(qh).isFinished()) {
      Thread.sleep(5000);
    }

    Assert.assertEquals(client.getQueryStatus(qh).getStatus(),QueryStatus.Status.SUCCESSFUL);
  }

  private void testExecuteSyncQuery(GrillQueryCommands qCom) {
    String sql = "cube select id,name from test_dim";
    String result = qCom.executeQuery(sql, false, "testQuery2");
    Assert.assertTrue(result.contains("1\tfirst"), result);
  }

  private void testShowPersistentResultSet(GrillQueryCommands qCom) throws Exception {
    System.out.println("@@PERSISTENT_RESULT_TEST-------------");
    client.setConnectionParam("grill.persistent.resultset.indriver", "true");
    String query = "cube select id,name from test_dim";
    try {
      String result = qCom.executeQuery(query, false, "testQuery3");
      System.out.println("@@ RESULT " + result);
      Assert.assertNotNull(result);
      Assert.assertFalse(result.contains("Failed to get resultset"));
    } catch (Exception exc) {
      exc.printStackTrace();
      Assert.fail("Exception not expected: " + exc.getMessage());
    }
    System.out.println("@@END_PERSISTENT_RESULT_TEST-------------");
  }

  private void testPurgedFinishedResultSet(GrillQueryCommands qCom) {
    System.out.println("@@START_FINISHED_PURGED_RESULT_TEST-------------");
    client.setConnectionParam("grill.max.finished.queries", "0");
    client.setConnectionParam("grill.persistent.resultset","true");
    String query = "cube select id,name from test_dim";
    try {
      String qh = qCom.executeQuery(query, true, "testQuery");
      while(!client.getQueryStatus(qh).isFinished()) {
        Thread.sleep(5000);
      }
      Assert.assertTrue(qCom.getStatus(qh).contains("Status : SUCCESSFUL"));

      String result = qCom.getQueryResults(qh);
      System.out.println("@@ RESULT " + result);
      Assert.assertNotNull(result);

      //This is to check for positive processing time
      Assert.assertFalse(result.contains("(-"));
    } catch (Exception exc) {
      exc.printStackTrace();
      Assert.fail("Exception not expected: " + exc.getMessage());
    }
    System.out.println("@@END_FINISHED_PURGED_RESULT_TEST-------------");
  }

}
