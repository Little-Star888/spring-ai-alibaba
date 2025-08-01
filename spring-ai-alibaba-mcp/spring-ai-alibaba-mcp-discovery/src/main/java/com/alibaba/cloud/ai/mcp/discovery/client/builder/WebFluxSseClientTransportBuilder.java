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

package com.alibaba.cloud.ai.mcp.discovery.client.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Sunrisea
 * @since 2025/7/4 11:16
 */
public class WebFluxSseClientTransportBuilder {

	public WebFluxSseClientTransport build(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
			String sseEndpoint) {
		return WebFluxSseClientTransport.builder(webClientBuilder)
			.sseEndpoint(sseEndpoint)
			.objectMapper(objectMapper)
			.build();
	}

	public WebFluxSseClientTransport build(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
			String sseEndpoint, ExchangeFilterFunction traceFilter) {
		if (traceFilter != null) {
			webClientBuilder.filter(traceFilter);
		}
		return WebFluxSseClientTransport.builder(webClientBuilder)
			.sseEndpoint(sseEndpoint)
			.objectMapper(objectMapper)
			.build();
	}

}
