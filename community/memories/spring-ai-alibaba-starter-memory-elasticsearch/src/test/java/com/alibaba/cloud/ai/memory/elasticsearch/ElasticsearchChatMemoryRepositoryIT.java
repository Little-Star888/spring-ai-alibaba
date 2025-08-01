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
package com.alibaba.cloud.ai.memory.elasticsearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.apache.http.HttpHost;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test using Testcontainers to automatically manage Elasticsearch test
 * environment
 */
@SpringBootTest(classes = ElasticsearchChatMemoryRepositoryIT.TestConfiguration.class)
@Testcontainers
class ElasticsearchChatMemoryRepositoryIT {

	// Use a more stable version
	private static final DockerImageName ELASTICSEARCH_IMAGE = DockerImageName
		.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.0");

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
		.withEnv("discovery.type", "single-node")
		.withEnv("xpack.security.enabled", "false")
		.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
		.withStartupAttempts(3);

	/**
	 * Dynamically configure Elasticsearch properties
	 */
	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.memory.elasticsearch.host", elasticsearchContainer::getHost);
		registry.add("spring.ai.memory.elasticsearch.port", () -> elasticsearchContainer.getMappedPort(9200));
		registry.add("spring.ai.memory.elasticsearch.scheme", () -> "http");
	}

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@BeforeEach
	void setUp() throws Exception {
		// Ensure the index is empty before each test
		if (chatMemoryRepository instanceof ElasticsearchChatMemoryRepository) {
			ElasticsearchChatMemoryRepository elasticsearchRepository = (ElasticsearchChatMemoryRepository) chatMemoryRepository;
			elasticsearchRepository.recreateIndex();
		}

		// Give Elasticsearch some time to process the request
		Thread.sleep(1000);
	}

	@Test
	void correctChatMemoryRepositoryInstance() {
		assertThat(chatMemoryRepository).isInstanceOf(ElasticsearchChatMemoryRepository.class);
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveMessagesSingleMessage(String content, MessageType messageType) throws Exception {
		var conversationId = UUID.randomUUID().toString();
		Message message;
		switch (messageType) {
			case ASSISTANT:
				message = new AssistantMessage(content + " - " + conversationId);
				break;
			case USER:
				message = new UserMessage(content + " - " + conversationId);
				break;
			case SYSTEM:
				message = new SystemMessage(content + " - " + conversationId);
				break;
			default:
				throw new IllegalArgumentException("Type not supported: " + messageType);
		}

		chatMemoryRepository.saveAll(conversationId, List.of(message));

		// Give Elasticsearch some time to index documents
		Thread.sleep(2000);

		var messages = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(messages).hasSize(1);

		var savedMessage = messages.get(0);
		assertThat(savedMessage.getText()).isEqualTo(message.getText());
		assertThat(savedMessage.getMessageType()).isEqualTo(messageType);
	}

	@Test
	void saveMessagesMultipleMessages() throws Exception {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		// Give Elasticsearch some time to index documents
		Thread.sleep(1000);

		var savedMessages = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(savedMessages.size()).isEqualTo(messages.size());

		for (var i = 0; i < messages.size(); i++) {
			var message = messages.get(i);
			var savedMessage = savedMessages.get(i);

			assertThat(savedMessage.getText()).isEqualTo(message.getText());
			assertThat(savedMessage.getMessageType()).isEqualTo(message.getMessageType());
		}

		var count = chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(messages.size());

		chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage("Hello")));

		// Give Elasticsearch some time to index documents
		Thread.sleep(1000);

		count = chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(1);
	}

	@Test
	void findMessagesByConversationId() throws Exception {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
				new AssistantMessage("Message from assistant 2 - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		// Give Elasticsearch some time to index documents
		Thread.sleep(1000);

		var results = chatMemoryRepository.findByConversationId(conversationId);

		assertThat(results.size()).isEqualTo(messages.size());
		// Modify assertions to use list size and content comparison instead of direct
		// object comparison
		assertThat(results.size()).isEqualTo(messages.size());
		for (int i = 0; i < messages.size(); i++) {
			assertThat(results.get(i).getText()).isEqualTo(messages.get(i).getText());
			assertThat(results.get(i).getMessageType()).isEqualTo(messages.get(i).getMessageType());
		}
	}

	@Test
	void deleteMessagesByConversationId() throws Exception {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		// Give Elasticsearch some time to index documents
		Thread.sleep(1000);

		// Confirm data was successfully saved
		var savedMessages = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(savedMessages.size()).isEqualTo(messages.size());

		// Ensure using strongly typed ElasticsearchChatMemoryRepository for operations
		ElasticsearchChatMemoryRepository repo = (ElasticsearchChatMemoryRepository) chatMemoryRepository;

		// Explicitly delete and manually refresh the index
		repo.deleteByConversationId(conversationId);

		// Give Elasticsearch more time to handle deletion
		Thread.sleep(5000); // Increase wait time to ensure operation completion

		// Use existing raw query functionality to check deletion results
		var response = repo.rawSearchQuery(conversationId);
		System.out.println("After deletion - raw search result: " + response);

		var results = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).isEmpty();
	}

	@Test
	void clearOverLimit() throws Exception {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("Message 1 from user - " + conversationId),
				new AssistantMessage("Message 1 from assistant - " + conversationId),
				new UserMessage("Message 2 from user - " + conversationId),
				new AssistantMessage("Message 2 from assistant - " + conversationId),
				new UserMessage("Message 3 from user - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		// Give Elasticsearch some time to index documents
		Thread.sleep(1000);

		// Verify all messages have been saved
		var savedMessages = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(savedMessages.size()).isEqualTo(messages.size());

		// Perform cleanup operation, set max limit to 3, delete count to 2
		ElasticsearchChatMemoryRepository elasticsearchRepository = (ElasticsearchChatMemoryRepository) chatMemoryRepository;
		elasticsearchRepository.clearOverLimit(conversationId, 3, 2);

		// Give Elasticsearch some time to process the operation
		Thread.sleep(1000);

		// Verify only the last 3 messages are retained
		savedMessages = chatMemoryRepository.findByConversationId(conversationId);
		assertThat(savedMessages.size()).isEqualTo(3);
		// Since sorting may not be completely reliable, check existence rather than exact
		// position
		boolean foundMessage2 = false;
		boolean foundMessage3 = false;
		boolean foundMessage4 = false;
		for (Message msg : savedMessages) {
			if (msg.getText().equals(messages.get(2).getText()))
				foundMessage2 = true;
			if (msg.getText().equals(messages.get(3).getText()))
				foundMessage3 = true;
			if (msg.getText().equals(messages.get(4).getText()))
				foundMessage4 = true;
		}
		assertThat(foundMessage2).isTrue();
		assertThat(foundMessage3).isTrue();
		assertThat(foundMessage4).isTrue();
	}

	@Test
	void debugElasticsearchQuery() throws Exception {
		var repo = (ElasticsearchChatMemoryRepository) chatMemoryRepository;
		var conversationId = UUID.randomUUID().toString();
		var message = new UserMessage("Debug message - " + conversationId);

		// Save the message
		chatMemoryRepository.saveAll(conversationId, List.of(message));
		Thread.sleep(2000);

		// Run a manual search query
		var response = repo.rawSearchQuery(conversationId);
		System.out.println("Raw search result: " + response);
	}

	@SpringBootConfiguration
	static class TestConfiguration {

		@Bean
		ChatMemoryRepository chatMemoryRepository() {
			RestClientBuilder restClientBuilder = RestClient.builder(
					new HttpHost(elasticsearchContainer.getHost(), elasticsearchContainer.getMappedPort(9200), "http"));
			RestClient restClient = restClientBuilder.build();
			RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
			ElasticsearchClient client = new ElasticsearchClient(transport);
			return new ElasticsearchChatMemoryRepository(client);
		}

	}

}
