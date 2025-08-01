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
package com.alibaba.cloud.ai.graph.checkpoint;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class Checkpoint implements Serializable {

	private String id = UUID.randomUUID().toString();

	private Map<String, Object> state = null;

	private String nodeId = null;

	private String nextNodeId = null;

	private Checkpoint() {
	}

	public Checkpoint(Checkpoint checkpoint) {
		this.id = checkpoint.id;
		this.state = checkpoint.state;
		this.nodeId = checkpoint.nodeId;
		this.nextNodeId = checkpoint.nextNodeId;
	}

	public String getId() {
		return id;
	}

	public Checkpoint setId(String id) {
		this.id = id;
		return this;
	}

	public Map<String, Object> getState() {
		return state;
	}

	public Checkpoint setState(Map<String, Object> state) {
		this.state = state;
		return this;
	}

	public String getNodeId() {
		return nodeId;
	}

	public Checkpoint setNodeId(String nodeId) {
		this.nodeId = nodeId;
		return this;
	}

	public String getNextNodeId() {
		return nextNodeId;
	}

	public Checkpoint setNextNodeId(String nextNodeId) {
		this.nextNodeId = nextNodeId;
		return this;
	}

	@Override
	public String toString() {
		return "Checkpoint{" + "id='" + id + '\'' + ", state=" + state + ", nodeId='" + nodeId + '\'' + ", nextNodeId='"
				+ nextNodeId + '\'' + '}';
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final Checkpoint result = new Checkpoint();

		public Builder id(String id) {
			result.id = id;
			return this;
		}

		public Builder state(OverAllState state) {
			result.state = state.data();
			return this;
		}

		public Builder state(Map<String, Object> state) {
			result.state = state;
			return this;
		}

		public Builder nodeId(String nodeId) {
			result.nodeId = nodeId;
			return this;
		}

		public Builder nextNodeId(String nextNodeId) {
			result.nextNodeId = nextNodeId;
			return this;
		}

		public Checkpoint build() {
			return new Checkpoint(result);

		}

	}

	public Checkpoint updateState(Map<String, Object> values, Map<String, KeyStrategy> channels) {

		Checkpoint result = new Checkpoint(this);
		result.state = OverAllState.updateState(state, values, channels);
		return result;
	}

}
