A [Protobuf](https://github.com/protocolbuffers/protobuf) parser generated from an [Antlr](https://github.com/antlr/antlr4) grammar. Originally based on the sample grammar at https://github.com/antlr/grammars-v4 (which has a couple of issues).

Manual testing example for simple proto file defining a single message with a string field:

```bash
$ mvn compile
$ ./grun.jsh com.example.Protobuf proto -tree 
syntax = "proto3";
message TestMessage {
        string value = 1;
}
^D
(proto (syntax syntax = "proto3" ;) (topLevelDef (messageDef message (messageName (ident TestMessage)) (messageBody { (messageElement (field (type string) (fieldName (ident value)) = (fieldNumber (intLit 1)) ;)) }))) <EOF>)
```