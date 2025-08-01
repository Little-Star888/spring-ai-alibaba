/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.example.manus.tool.database;

import com.alibaba.cloud.ai.example.manus.config.ManusProperties;
import com.alibaba.cloud.ai.example.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.example.manus.tool.ToolPromptManager;
import com.alibaba.cloud.ai.example.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.example.manus.tool.database.action.ExecuteSqlAction;
import com.alibaba.cloud.ai.example.manus.tool.database.action.GetDatasourceInfoAction;
import com.alibaba.cloud.ai.example.manus.tool.database.action.GetTableIndexAction;
import com.alibaba.cloud.ai.example.manus.tool.database.action.GetTableMetaAction;
import com.alibaba.cloud.ai.example.manus.tool.database.action.GetTableNameAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DatabaseUseTool extends AbstractBaseTool<DatabaseRequest> {

	private static final Logger log = LoggerFactory.getLogger(DatabaseUseTool.class);

	private final ManusProperties manusProperties;

	private final DataSourceService dataSourceService;

	private final ObjectMapper objectMapper;

	private final ToolPromptManager toolPromptManager;

	public DatabaseUseTool(ManusProperties manusProperties, DataSourceService dataSourceService,
			ObjectMapper objectMapper, ToolPromptManager toolPromptManager) {
		this.manusProperties = manusProperties;
		this.dataSourceService = dataSourceService;
		this.objectMapper = objectMapper;
		this.toolPromptManager = toolPromptManager;
	}

	public DataSourceService getDataSourceService() {
		return dataSourceService;
	}

	private final String name = "database_use";

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return toolPromptManager.getToolDescription("database_use");
	}

	@Override
	public String getParameters() {
		return toolPromptManager.getToolParameters("database_use");
	}

	@Override
	public Class<DatabaseRequest> getInputType() {
		return DatabaseRequest.class;
	}

	@Override
	public ToolExecuteResult run(DatabaseRequest request) {
		String action = request.getAction();
		log.info("DatabaseUseTool request: action={}", action);
		try {
			if (action == null) {
				return new ToolExecuteResult("Action parameter is required");
			}
			switch (action) {
				case "execute_sql":
					return new ExecuteSqlAction().execute(request, dataSourceService);
				case "get_table_name":
					return new GetTableNameAction(objectMapper).execute(request, dataSourceService);
				case "get_table_index":
					return new GetTableIndexAction(objectMapper).execute(request, dataSourceService);
				case "get_table_meta": {
					// First search with text, if not found then search all
					GetTableMetaAction metaAction = new GetTableMetaAction(objectMapper);
					ToolExecuteResult result = metaAction.execute(request, dataSourceService);
					if (result == null || result.getOutput() == null || result.getOutput().trim().isEmpty()
							|| result.getOutput().equals("[]")
							|| result.getOutput().contains("No matching tables found")) {
						DatabaseRequest allReq = new DatabaseRequest();
						allReq.setAction("get_table_meta");
						allReq.setText(null);
						result = metaAction.execute(allReq, dataSourceService);
					}
					return result;
				}
				case "get_datasource_info":
					return new GetDatasourceInfoAction(objectMapper).execute(request, dataSourceService);
				default:
					return new ToolExecuteResult("Unknown action: " + action);
			}
		}
		catch (Exception e) {
			log.error("Database action '" + action + "' failed", e);
			return new ToolExecuteResult("Database action '" + action + "' failed: " + e.getMessage());
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up database resources for plan: {}", planId);
			try {
				// Close all data source connections
				dataSourceService.closeAllConnections();
				log.info("Successfully cleaned up database connections for plan: {}", planId);
			}
			catch (Exception e) {
				log.error("Failed to cleanup database resources for plan: {}", planId, e);
			}
		}
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			// Get all data source information
			Map<String, String> datasourceInfo = dataSourceService.getAllDatasourceInfo();

			// Build data source status information
			StringBuilder stateBuilder = new StringBuilder();
			stateBuilder.append("\n=== Database Tool Current State ===\n");

			if (datasourceInfo.isEmpty()) {
				stateBuilder.append("No datasources configured or available.\n");
			}
			else {
				stateBuilder.append("Available datasources:\n");
				for (Map.Entry<String, String> entry : datasourceInfo.entrySet()) {
					String datasourceName = entry.getKey();
					String datasourceType = entry.getValue();
					stateBuilder.append(String.format("  - %s (%s)\n", datasourceName, datasourceType));
				}

				// Get default data source information
				try {
					String defaultType = dataSourceService.getDataSourceType();
					stateBuilder.append(String.format("\nDefault datasource type: %s\n", defaultType));
				}
				catch (Exception e) {
					stateBuilder.append("\nDefault datasource: Not available\n");
				}

				// Test connection status
				stateBuilder.append("\nConnection status:\n");
				for (String datasourceName : datasourceInfo.keySet()) {
					try {
						dataSourceService.getConnection(datasourceName);
						stateBuilder.append(String.format("  - %s: Connected ✓\n", datasourceName));
					}
					catch (Exception e) {
						stateBuilder.append(
								String.format("  - %s: Connection failed ✗ (%s)\n", datasourceName, e.getMessage()));
					}
				}
			}

			stateBuilder.append("\n=== End Database Tool State ===\n");
			return stateBuilder.toString();

		}
		catch (Exception e) {
			log.error("Failed to get database tool state", e);
			return String.format("Database tool state error: %s", e.getMessage());
		}
	}

	public static DatabaseUseTool getInstance(DataSourceService dataSourceService, ObjectMapper objectMapper,
			ToolPromptManager toolPromptManager) {
		return new DatabaseUseTool(null, dataSourceService, objectMapper, toolPromptManager);
	}

}
