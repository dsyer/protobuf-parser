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
import java.nio.file.Path;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;

import com.example.ProtobufParser.FieldContext;
import com.example.ProtobufParser.TypeContext;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;

public class DescriptorParser {

	public FileDescriptorProto parse(String input) {
		CharStream stream = CharStreams.fromString(input);
		return parse(stream);
	}

	public FileDescriptorProto parse(InputStream input) {
		try {
			return parse(CharStreams.fromStream(input));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input stream: " + input, e);
		}
	}

	public FileDescriptorProto parse(Path input) {
		try {
			return parse(CharStreams.fromPath(input));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input file: " + input, e);
		}
	}

	private FileDescriptorProto parse(CharStream stream) {
		ProtobufLexer lexer = new ProtobufLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ProtobufParser parser = new ProtobufParser(tokens);

		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();

		parser.removeErrorListeners(); // Remove default error listeners
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
					int line, int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("Syntax error at line " + line + ": " + msg, e);
			}
		});

		return parser.proto().accept(new ProtobufBaseVisitor<FileDescriptorProto>() {
			private Builder type;

			@Override
			protected FileDescriptorProto defaultResult() {
				return builder.build();
			}

			@Override
			public FileDescriptorProto visitField(FieldContext ctx) {
				// TODO: handle field options if needed
				// TODO: handle field labels (optional, required, repeated)
				Type fieldType = findType(ctx.type());
				FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
						.setName(ctx.fieldName().getText())
						.setNumber(Integer.valueOf(ctx.fieldNumber().getText()))
						.setType(fieldType);
				if (fieldType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
					field.setTypeName(ctx.type().messageType().getText());
				}
				this.type.addField(field.build());
				return super.visitField(ctx);
			}

			private Type findType(TypeContext ctx) {
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
					return FieldDescriptorProto.Type.TYPE_MESSAGE;
				}
				if (ctx.enumType() != null) {
					return FieldDescriptorProto.Type.TYPE_ENUM;
				}
				throw new IllegalStateException("Unknown type: " + ctx.getText());
			}

			@Override
			public FileDescriptorProto visitMessageDef(ProtobufParser.MessageDefContext ctx) {
				System.err.println("Message: " + ctx.messageName().getText());
				this.type = DescriptorProto.newBuilder()
						.setName(ctx.messageName().getText());
				FileDescriptorProto result = super.visitMessageDef(ctx);
				builder.addMessageType(type);
				return result;
			}

			@Override
			public FileDescriptorProto visit(ParseTree tree) {
				FileDescriptorProto result = super.visit(tree);
				return result;
			}
		});
	}
}
