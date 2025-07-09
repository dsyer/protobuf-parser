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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class DescriptorParser {

	public FileDescriptorProto parse(String name, String input) {
		CharStream stream = CharStreams.fromString(input);
		return parse(name, stream);
	}

	public FileDescriptorProto parse(String name, InputStream input) {
		try {
			return parse(name, CharStreams.fromStream(input));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input stream: " + input, e);
		}
	}

	public FileDescriptorSet parse(Path... inputs) {
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		for (Path input : inputs) {
			parse(input).getFileList().forEach(builder::addFile);
		}
		return builder.build();
	}

	public FileDescriptorSet parse(Path input) {
		if (!input.toFile().exists()) {
			throw new IllegalArgumentException("Input file does not exist: " + input);
		}
		if (!Files.isDirectory(input) && input.toString().endsWith(".proto")) {
			throw new IllegalArgumentException("Input file is not .proto: " + input);
		}
		try {
			FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
			if (input.toFile().isDirectory()) {
				Files.walk(input)
						.filter(file -> !Files.isDirectory(file) && file.toString().endsWith(".proto"))
						.forEach(file -> {
							try {
								builder.addFile(parse(input.toString(), CharStreams.fromPath(file)));
							} catch (IOException e) {
								throw new IllegalStateException("Failed to read file: " + file, e);
							}
						});
				return builder.build();
			}
			return builder.addFile(parse(input.toString(), CharStreams.fromPath(input))).build();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input file: " + input, e);
		}
	}

	private FileDescriptorProto parse(String name, CharStream stream) {
		ProtobufLexer lexer = new ProtobufLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ProtobufParser parser = new ProtobufParser(tokens);

		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		builder.setName(name);
		builder.setSyntax("proto3");

		parser.removeErrorListeners(); // Remove default error listeners
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
					int line, int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("Syntax error at line " + line + ": " + msg, e);
			}
		});

		Set<String> enumNames = parser.proto().accept(new EnumNamesVisitor());
		parser.reset();

		return parser.proto().accept(new ProtobufDescriptorVisitor(builder, enumNames));
	}
}
