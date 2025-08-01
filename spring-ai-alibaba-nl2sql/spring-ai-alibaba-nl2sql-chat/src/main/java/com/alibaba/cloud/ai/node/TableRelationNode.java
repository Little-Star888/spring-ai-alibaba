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

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dto.SemanticModelDTO;
import com.alibaba.cloud.ai.enums.StreamResponseType;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.service.base.BaseNl2SqlService;
import com.alibaba.cloud.ai.service.base.BaseSchemaService;
import com.alibaba.cloud.ai.service.business.BusinessKnowledgeRecallService;
import com.alibaba.cloud.ai.service.semantic.SemanticModelRecallService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.StateUtils;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.BUSINESS_KNOWLEDGE;
import static com.alibaba.cloud.ai.constant.Constant.COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.DATA_SET_ID;
import static com.alibaba.cloud.ai.constant.Constant.EVIDENCES;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.constant.Constant.SEMANTIC_MODEL;
import static com.alibaba.cloud.ai.constant.Constant.SQL_GENERATE_SCHEMA_MISSING_ADVICE;
import static com.alibaba.cloud.ai.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.TABLE_RELATION_OUTPUT;

import static com.alibaba.cloud.ai.prompt.PromptHelper.buildBusinessKnowledgePrompt;
import static com.alibaba.cloud.ai.prompt.PromptHelper.buildSemanticModelPrompt;

/**
 * Table relationship inference node that automatically completes complex structures like
 * JOINs and foreign keys.
 *
 * This node is responsible for: - Inferring relationships between tables and fields -
 * Building initial schema from documents - Processing schema selection based on input and
 * evidence - Handling schema advice for missing information
 *
 * @author zhangshenghang
 */
public class TableRelationNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(TableRelationNode.class);

	private final BaseSchemaService baseSchemaService;

	private final BaseNl2SqlService baseNl2SqlService;

	private final BusinessKnowledgeRecallService businessKnowledgeRecallService;

	private final SemanticModelRecallService semanticModelRecallService;

	public TableRelationNode(BaseSchemaService baseSchemaService, BaseNl2SqlService baseNl2SqlService,
			BusinessKnowledgeRecallService businessKnowledgeRecallService,
			SemanticModelRecallService semanticModelRecallService) {
		this.baseSchemaService = baseSchemaService;
		this.baseNl2SqlService = baseNl2SqlService;
		this.businessKnowledgeRecallService = businessKnowledgeRecallService;
		this.semanticModelRecallService = semanticModelRecallService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("Entering {} node", this.getClass().getSimpleName());

		// Get necessary input parameters
		String input = StateUtils.getStringValue(state, INPUT_KEY);
		List<String> evidenceList = StateUtils.getListValue(state, EVIDENCES);
		List<Document> tableDocuments = StateUtils.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		List<List<Document>> columnDocumentsByKeywords = StateUtils.getDocumentListList(state,
				COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT);
		String dataSetId = StateUtils.getStringValue(state, DATA_SET_ID);
		String agentIdStr = StateUtils.getStringValue(state, AGENT_ID);
		long agentId = -1L;
		if (!agentIdStr.isEmpty()) {
			agentId = Long.parseLong(agentIdStr);
		}

		// Execute business logic first - get final result immediately
		SchemaDTO schemaDTO = buildInitialSchema(columnDocumentsByKeywords, tableDocuments);
		SchemaDTO result = processSchemaSelection(schemaDTO, input, evidenceList, state);

		// Extract business knowledge and semantic model
		List<BusinessKnowledgeDTO> businessKnowledges = businessKnowledgeRecallService.getFieldByDataSetId(dataSetId);
		List<SemanticModelDTO> semanticModel = semanticModelRecallService.getFieldByAgentId(agentId);
		// load prompt template
		String businessKnowledgePrompt = buildBusinessKnowledgePrompt(businessKnowledges);
		String semanticModelPrompt = buildSemanticModelPrompt(semanticModel);

		logger.info("[{}] Schema processing result: {}", this.getClass().getSimpleName(), result);

		// Create display stream for user experience only
		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createCustomStatusResponse("开始构建初始Schema..."));
			emitter.next(ChatResponseUtil.createCustomStatusResponse("初始Schema构建完成."));

			emitter.next(ChatResponseUtil.createCustomStatusResponse("开始处理Schema选择..."));
			emitter.next(ChatResponseUtil.createCustomStatusResponse("Schema选择处理完成."));
			emitter.complete();
		});

		// Use utility class to create generator, directly return business logic computed
		// result
		var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(
				this.getClass(), state, v -> Map.of(TABLE_RELATION_OUTPUT, result, BUSINESS_KNOWLEDGE,
						businessKnowledgePrompt, SEMANTIC_MODEL, semanticModelPrompt),
				displayFlux, StreamResponseType.SCHEMA_DEEP_RECALL);

		return Map.of(TABLE_RELATION_OUTPUT, generator, BUSINESS_KNOWLEDGE, businessKnowledgePrompt, SEMANTIC_MODEL,
				semanticModelPrompt);
	}

	/**
	 * Builds initial schema from column and table documents.
	 */
	private SchemaDTO buildInitialSchema(List<List<Document>> columnDocumentsByKeywords,
			List<Document> tableDocuments) {
		SchemaDTO schemaDTO = new SchemaDTO();
		baseSchemaService.extractDatabaseName(schemaDTO);
		baseSchemaService.buildSchemaFromDocuments(columnDocumentsByKeywords, tableDocuments, schemaDTO);
		return schemaDTO;
	}

	/**
	 * Processes schema selection based on input, evidence, and optional advice.
	 */
	private SchemaDTO processSchemaSelection(SchemaDTO schemaDTO, String input, List<String> evidenceList,
			OverAllState state) {
		String schemaAdvice = StateUtils.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);

		if (schemaAdvice != null) {
			logger.info("[{}] Processing with schema supplement advice: {}", this.getClass().getSimpleName(),
					schemaAdvice);
			return baseNl2SqlService.fineSelect(schemaDTO, input, evidenceList, schemaAdvice);
		}
		else {
			logger.info("[{}] Executing regular schema selection", this.getClass().getSimpleName());
			return baseNl2SqlService.fineSelect(schemaDTO, input, evidenceList);
		}
	}

}
