/*
 * Copyright 2025-current the original author or authors.
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
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;

public class ClasspathDescriptorTests {

	@Test
	public void testClasspathDescriptor() {
		DescriptorParser parser = new DescriptorParser();
		// Comes with the protobuf-java library:
		FileDescriptorProto proto = parser.parse("descriptor.proto",
				getClass().getResourceAsStream("/google/protobuf/empty.proto"));
		assertThat(proto.getName()).isEqualTo("descriptor.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		assertThat(proto.getMessageType(0).getName()).isEqualTo("Empty");
		assertThat(proto.getMessageType(0).getFieldList()).isEmpty();
	}

}
