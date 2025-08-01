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

package com.alibaba.cloud.ai.service.code;

import com.alibaba.cloud.ai.config.CodeExecutorProperties;
import com.alibaba.cloud.ai.service.code.impl.AiSimulationCodeExecutorService;
import com.alibaba.cloud.ai.service.code.impl.DockerCodePoolExecutorService;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 运行Python任务的容器池（工厂）
 *
 * @author vlsmb
 * @since 2025/7/28
 */
public final class CodePoolExecutorServiceFactory {

	private CodePoolExecutorServiceFactory() {

	}

	public static CodePoolExecutorService newInstance(CodeExecutorProperties properties,
			ChatClient.Builder chatClientBuilder) {
		if (properties.getCodePoolExecutor().equals(CodePoolExecutorEnum.DOCKER)) {
			return new DockerCodePoolExecutorService(properties);
		}
		else if (properties.getCodePoolExecutor().equals(CodePoolExecutorEnum.AI_SIMULATION)) {
			return new AiSimulationCodeExecutorService(chatClientBuilder);
		}
		else {
			throw new IllegalArgumentException("Unknown container impl: " + properties.getCodePoolExecutor());
		}
	}

}
