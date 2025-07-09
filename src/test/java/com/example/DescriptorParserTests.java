/*
 * Copyright 2024-2024 the original author or authors.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;

public class DescriptorParserTests {

	@Test
	public void testParseDescriptorError() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string foo name = 1;
					int32 age = 2;
				}
				""";
		DescriptorParser parser = new DescriptorParser();
		assertThrows(IllegalStateException.class, () -> {
			parser.parse(input);
		});
	}

	@Test
	public void testParseDescriptor() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					int32 age = 2;
				}
				""";
		DescriptorParser parser = new DescriptorParser();
		FileDescriptorProto proto = parser.parse(input);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(0).getName()).isEqualTo("name");
		assertThat(type.getField(0).getNumber()).isEqualTo(1);
		assertThat(type.getField(1).getName()).isEqualTo("age");
		assertThat(type.getField(1).getNumber()).isEqualTo(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testParseMessageType() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					Foo foo = 2;
				}
				message Foo {
					string value = 1;
					int32 count = 2;
				}
				""";
		DescriptorParser parser = new DescriptorParser();
		FileDescriptorProto proto = parser.parse(input);
		assertThat(proto.getMessageTypeList()).hasSize(2);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(1).getName()).isEqualTo("foo");
		assertThat(type.getField(1).getNumber()).isEqualTo(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(type.getField(1).getTypeName()).isEqualTo("Foo");
	}
}
