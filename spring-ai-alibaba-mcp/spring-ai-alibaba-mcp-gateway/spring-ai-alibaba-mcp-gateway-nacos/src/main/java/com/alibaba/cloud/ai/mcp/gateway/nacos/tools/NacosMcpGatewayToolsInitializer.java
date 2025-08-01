/*
 * Copyright 2024-2025 the original author or authors.
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

package com.alibaba.cloud.ai.mcp.gateway.nacos.tools;

import com.alibaba.cloud.ai.mcp.gateway.core.McpGatewayToolsInitializer;
import com.alibaba.cloud.ai.mcp.gateway.nacos.callback.NacosMcpGatewayToolCallback;
import com.alibaba.cloud.ai.mcp.gateway.nacos.definition.NacosMcpGatewayToolDefinition;
import com.alibaba.cloud.ai.mcp.gateway.nacos.properties.NacosMcpGatewayProperties;
import com.alibaba.cloud.ai.mcp.nacos.service.NacosMcpOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolMeta;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NacosMcpGatewayToolsInitializer implements McpGatewayToolsInitializer {

	private static final Logger logger = LoggerFactory.getLogger(NacosMcpGatewayToolsInitializer.class);

	private final NacosMcpGatewayProperties nacosMcpGatewayProperties;

	private final NacosMcpOperationService nacosMcpOperationService;

	public NacosMcpGatewayToolsInitializer(NacosMcpOperationService nacosMcpOperationService,
			NacosMcpGatewayProperties nacosMcpGatewayProperties) {
		this.nacosMcpGatewayProperties = nacosMcpGatewayProperties;
		this.nacosMcpOperationService = nacosMcpOperationService;
	}

	@Override
	public List<ToolCallback> initializeTools() {
		List<String> serviceNames = nacosMcpGatewayProperties.getServiceNames();
		if (serviceNames == null || serviceNames.isEmpty()) {
			logger.warn("No service names configured, no tools will be initialized");
			return new ArrayList<>();
		}
		List<ToolCallback> allTools = new ArrayList<>();
		for (String serviceName : serviceNames) {
			try {
				McpServerDetailInfo serviceDetail = nacosMcpOperationService.getServerDetail(serviceName);
				if (serviceDetail == null) {
					logger.warn("No service detail info found for service: {}", serviceName);
					continue;
				}
				String protocol = serviceDetail.getProtocol();
				if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)
						|| "mcp-sse".equalsIgnoreCase(protocol) || "mcp-streamable".equalsIgnoreCase(protocol)) {
					List<ToolCallback> tools = parseToolsFromMcpServerDetailInfo(serviceDetail);
					if (CollectionUtils.isEmpty(tools)) {
						logger.warn("No tools defined for service: {}", serviceName);
						continue;
					}
					allTools.addAll(tools);
				}
				else {
					logger.error("protocol {} is not supported yet. Check your configuration for valid tool protocols",
							protocol);
				}

			}
			catch (Exception e) {
				logger.error("Failed to initialize tools for service: {}", serviceName, e);
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Initial dynamic tools loading completed - Found {} tools", allTools.size());
		}
		return allTools;
	}

	private List<ToolCallback> parseToolsFromMcpServerDetailInfo(McpServerDetailInfo mcpServerDetailInfo) {
		try {
			McpToolSpecification toolSpecification = mcpServerDetailInfo.getToolSpec();
			String protocol = mcpServerDetailInfo.getProtocol();
			McpServerRemoteServiceConfig mcpServerRemoteServiceConfig = mcpServerDetailInfo.getRemoteServerConfig();
			List<ToolCallback> toolCallbacks = new ArrayList<>();
			if (toolSpecification != null) {
				List<McpTool> toolsList = toolSpecification.getTools();
				Map<String, McpToolMeta> toolsMeta = toolSpecification.getToolsMeta();
				if (toolsList == null || toolsMeta == null) {
					return new ArrayList<>();
				}
				for (McpTool tool : toolsList) {
					String toolName = tool.getName();
					String toolDescription = tool.getDescription();
					Map<String, Object> inputSchema = tool.getInputSchema();
					McpToolMeta metaInfo = toolsMeta.get(toolName);
					boolean enabled = metaInfo == null || metaInfo.isEnabled();
					if (!enabled) {
						logger.info("Tool {} is disabled by metaInfo, skipping.", toolName);
						continue;
					}
					NacosMcpGatewayToolDefinition toolDefinition = NacosMcpGatewayToolDefinition.builder()
						.name(mcpServerDetailInfo.getName() + "_tools_" + toolName)
						.description(toolDescription)
						.inputSchema(inputSchema)
						.protocol(protocol)
						.remoteServerConfig(mcpServerRemoteServiceConfig)
						.toolsMeta(metaInfo)
						.build();
					toolCallbacks.add(new NacosMcpGatewayToolCallback(toolDefinition));
				}
			}
			return toolCallbacks;
		}
		catch (Exception e) {
			logger.warn("Failed to get or parse nacos mcp service tools info (mcpName {})",
					mcpServerDetailInfo.getName() + mcpServerDetailInfo.getVersionDetail().getVersion(), e);
		}
		return null;
	}

}
