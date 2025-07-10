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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;

import com.example.ProtobufParser.EnumDefContext;
import com.example.ProtobufParser.EnumFieldContext;
import com.example.ProtobufParser.FieldContext;
import com.example.ProtobufParser.FieldLabelContext;
import com.example.ProtobufParser.ImportStatementContext;
import com.example.ProtobufParser.TypeContext;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
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
		Set<String> enumNames = new HashSet<>();
		parser.addParseListener(new ProtobufBaseListener() {
			@Override
			public void exitEnumDef(EnumDefContext ctx) {
				enumNames.add(ctx.enumName().getText());
			}
		});

		return parser.proto().accept(new ProtobufDescriptorVisitor(builder, enumNames));
	}

	static class ProtobufDescriptorVisitor extends ProtobufBaseVisitor<FileDescriptorProto> {
		private final FileDescriptorProto.Builder builder;
		private Stack<DescriptorProto.Builder> type = new Stack<>();
		private Stack<EnumDescriptorProto.Builder> enumType = new Stack<>();
		private Stack<FieldDescriptorProto.Builder> field = new Stack<>();
		private Set<String> enumNames;
	
		public ProtobufDescriptorVisitor(FileDescriptorProto.Builder builder) {
			this(builder, Set.of());
		}
	
		public ProtobufDescriptorVisitor(FileDescriptorProto.Builder builder, Set<String> enumNames) {
			this.builder = builder;
			this.enumNames = enumNames;
		}
	
		@Override
		protected FileDescriptorProto defaultResult() {
			return builder.build();
		}
	
		@Override
		public FileDescriptorProto visitFieldLabel(FieldLabelContext ctx) {
			this.field.peek().setLabel(findLabel(ctx));
			return super.visitFieldLabel(ctx);
		}
	
		private FieldDescriptorProto.Label findLabel(FieldLabelContext ctx) {
			if (ctx.OPTIONAL() != null) {
				return FieldDescriptorProto.Label.LABEL_OPTIONAL;
			}
			if (ctx.REQUIRED() != null) {
				return FieldDescriptorProto.Label.LABEL_REQUIRED;
			}
			if (ctx.REPEATED() != null) {
				return FieldDescriptorProto.Label.LABEL_REPEATED;
			}
			throw new IllegalStateException("Unknown field label: " + ctx.getText());
		}
	
		@Override
		public FileDescriptorProto visitField(FieldContext ctx) {
			// TODO: handle field options if needed
			FieldDescriptorProto.Type fieldType = findType(ctx.type());
			FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
					.setName(ctx.fieldName().getText())
					.setNumber(Integer.valueOf(ctx.fieldNumber().getText()))
					.setType(fieldType);
			this.field.push(field);
			if (fieldType == FieldDescriptorProto.Type.TYPE_MESSAGE || fieldType == FieldDescriptorProto.Type.TYPE_ENUM) {
				field.setTypeName(ctx.type().messageType().getText());
			}
			FileDescriptorProto result = super.visitField(ctx);
			this.type.peek().addField(field.build());
			this.field.pop();
			return result;
		}
	
		private FieldDescriptorProto.Type findType(TypeContext ctx) {
			if (ctx.STRING() != null) {
				return FieldDescriptorProto.Type.TYPE_STRING;
			}
			if (ctx.INT32() != null) {
				return FieldDescriptorProto.Type.TYPE_INT32;
			}
			if (ctx.INT64() != null) {
				return FieldDescriptorProto.Type.TYPE_INT64;
			}
			if (ctx.BOOL() != null) {
				return FieldDescriptorProto.Type.TYPE_BOOL;
			}
			if (ctx.FLOAT() != null) {
				return FieldDescriptorProto.Type.TYPE_FLOAT;
			}
			if (ctx.DOUBLE() != null) {
				return FieldDescriptorProto.Type.TYPE_DOUBLE;
			}
			if (ctx.BYTES() != null) {
				return FieldDescriptorProto.Type.TYPE_BYTES;
			}
			if (ctx.FIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED32;
			}
			if (ctx.FIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED64;
			}
			if (ctx.SFIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED32;
			}
			if (ctx.SFIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED64;
			}
			if (ctx.UINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT32;
			}
			if (ctx.UINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT64;
			}
			if (ctx.SINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT32;
			}
			if (ctx.SINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT64;
			}
			if (ctx.messageType() != null) {
				if (this.enumNames.contains(ctx.messageType().getText())) {
					return FieldDescriptorProto.Type.TYPE_ENUM;
				}
				return FieldDescriptorProto.Type.TYPE_MESSAGE;
			}
			if (ctx.enumType() != null) {
				// Doesn't happen
				return FieldDescriptorProto.Type.TYPE_ENUM;
			}
			throw new IllegalStateException("Unknown type: " + ctx.getText());
		}
	
		@Override
		public FileDescriptorProto visitEnumDef(EnumDefContext ctx) {
			EnumDescriptorProto.Builder enumType = EnumDescriptorProto.newBuilder()
					.setName(ctx.enumName().getText());
			this.enumType.push(enumType);
			FileDescriptorProto result = super.visitEnumDef(ctx);
			builder.addEnumType(enumType.build());
			this.enumType.pop();
			return result;
		}
	
		@Override
		public FileDescriptorProto visitEnumField(EnumFieldContext ctx) {
			// System.err.println("Enum field: " + ctx.enumFieldName().getText());
			EnumValueDescriptorProto.Builder field = EnumValueDescriptorProto.newBuilder()
					.setName(ctx.ident().IDENTIFIER().getText())
					.setNumber(Integer.valueOf(ctx.intLit().INT_LIT().getText()));
			this.enumType.peek().addValue(field.build());
			return super.visitEnumField(ctx);
		}
	
		@Override
		public FileDescriptorProto visitMessageDef(ProtobufParser.MessageDefContext ctx) {
			// System.err.println("Message: " + ctx.messageName().getText());
			DescriptorProto.Builder type = DescriptorProto.newBuilder()
					.setName(ctx.messageName().getText());
			this.type.push(type);
			FileDescriptorProto result = super.visitMessageDef(ctx);
			builder.addMessageType(type);
			this.type.pop();
			return result;
		}

		@Override
		public FileDescriptorProto visitImportStatement(ImportStatementContext ctx) {
			String path = ctx.strLit().getText();
			path = path.replace("\"", "").replace("'", "");
			FileDescriptorProto importedFile = new DescriptorParser().parse(path, findImport(path));
			builder.addDependency(path);
			return super.visitImportStatement(ctx);
		}

		private InputStream findImport(String path) {
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
			if (stream == null) {
				throw new IllegalArgumentException("Import not found: " + path);
			}
			return stream;
		}

	}
	
}
