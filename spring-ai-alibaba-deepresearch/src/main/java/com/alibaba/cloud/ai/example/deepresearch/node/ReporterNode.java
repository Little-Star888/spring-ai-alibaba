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

package com.alibaba.cloud.ai.example.deepresearch.node;

import com.alibaba.cloud.ai.example.deepresearch.enums.StreamNodePrefixEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.ParallelEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.service.ReportService;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingChatGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author yingzi
 * @since 2025/5/18 15:58
 */

public class ReporterNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ReporterNode.class);

	private final ChatClient reporterAgent;

	private final ReportService reportService;

	private static final String RESEARCH_FORMAT = "# Research Requirements\n\n## Task\n\n{0}\n\n## Description\n\n{1}";

	public ReporterNode(ChatClient reporterAgent, ReportService reportService) {
		this.reporterAgent = reporterAgent;
		this.reportService = reportService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("reporter node is running.");
		Plan currentPlan = state.value("current_plan", Plan.class)
			.orElseThrow(() -> new IllegalArgumentException("current_plan is missing"));

		// 从 OverAllState 中获取线程ID
		String threadId = state.value("thread_id", String.class)
			.orElseThrow(() -> new IllegalArgumentException("thread_id is missing from state"));
		logger.info("Thread ID from state: {}", threadId);
		// 1. 添加消息
		List<Message> messages = new ArrayList<>();
		// 1.1 研究报告格式消息
		messages.add(new UserMessage(
				MessageFormat.format(RESEARCH_FORMAT, currentPlan.getTitle(), currentPlan.getThought())));
		// 1.2 添加背景调查的消息
		if (state.value("enable_background_investigation", true)) {
			List<String> backgroundInvestigationResults = state.value("background_investigation_results",
					(List<String>) null);
			assert backgroundInvestigationResults != null && !backgroundInvestigationResults.isEmpty();
			for (String backgroundInvestigationResult : backgroundInvestigationResults) {
				if (StringUtils.hasText(backgroundInvestigationResult)) {
					messages.add(new UserMessage(backgroundInvestigationResult));
				}
			}
		}
		// 1.4 添加研究组节点返回的信息
		List<String> researcherTeam = List.of(ParallelEnum.RESEARCHER.getValue(), ParallelEnum.CODER.getValue());
		for (String content : StateUtil.getParallelMessages(state, researcherTeam, StateUtil.getMaxStepNum(state))) {
			logger.info("researcherTeam_content: {}", content);
			messages.add(new UserMessage(content));
		}
		// 1.5 添加专业知识库决策节点返回的信息
		if (state.value("use_professional_kb", false) && StringUtils.hasText(StateUtil.getRagContent(state))) {
			messages.add(new UserMessage(StateUtil.getRagContent(state)));
		}

		logger.debug("reporter node messages: {}", messages);

		String prefix = StreamNodePrefixEnum.REPORTER_LLM_STREAM.getPrefix();
		String stepTitleKey = prefix + "_step_title";
		state.registerKeyAndStrategy(stepTitleKey, new ReplaceStrategy());
		Map<String, Object> inputMap = new HashMap<>();
		inputMap.put(stepTitleKey, "[报告生成]");
		state.input(inputMap);

		var streamResult = reporterAgent.prompt().messages(messages).stream().chatResponse();

		var generator = StreamingChatGenerator.builder()
			.startingNode(prefix)
			.startingState(state)
			.mapResult(response -> {
				String finalReport = Objects.requireNonNull(response.getResult().getOutput().getText());
				try {
					reportService.saveReport(threadId, finalReport);
					logger.info("Report saved successfully, Thread ID: {}", threadId);
				}
				catch (Exception e) {
					logger.error("Failed to save report, Thread ID: {}", threadId, e);
				}
				return Map.of("final_report", finalReport, "thread_id", threadId);
			})
			.buildWithChatResponse(streamResult);
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("final_report", generator);
		resultMap.put("thread_id", threadId);
		return resultMap;
	}

}
