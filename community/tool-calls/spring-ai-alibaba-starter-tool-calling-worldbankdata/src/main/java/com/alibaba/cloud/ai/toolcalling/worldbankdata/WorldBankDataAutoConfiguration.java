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
package com.alibaba.cloud.ai.toolcalling.worldbankdata;

import com.alibaba.cloud.ai.toolcalling.common.JsonParseTool;
import com.alibaba.cloud.ai.toolcalling.common.WebClientTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

@Configuration
@EnableConfigurationProperties(WorldBankDataProperties.class)
@ConditionalOnProperty(prefix = WorldBankDataConstants.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class WorldBankDataAutoConfiguration {

	@Bean(name = WorldBankDataConstants.TOOL_NAME)
	@ConditionalOnMissingBean
	@Description("World Bank Development Data Search Service")
	public WorldBankDataService worldBankDataService(JsonParseTool jsonParseTool, WorldBankDataProperties properties) {
		return new WorldBankDataService(
				WebClientTool.builder(jsonParseTool, properties).httpHeadersConsumer(httpHeaders -> {
					httpHeaders.add("Accept", "application/json");
					httpHeaders.add("User-Agent", "Spring-AI-Alibaba-WorldBankData/1.0");
				}).build(), jsonParseTool, properties);
	}

}
