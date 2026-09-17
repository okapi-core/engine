parser grammar OkapiLogQlParser;

options {
    tokenVocab = OkapiLogQlLexer;
}

query
    : withClause? booleanExpr? pipelineOp* EOF
    ;

withClause
    : WITH LPAREN booleanExpr RPAREN
    ;

booleanExpr
    : orExpr
    ;

orExpr
    : andExpr (OR andExpr)*
    ;

andExpr
    : unaryExpr ((AND? unaryExpr))*
    ;

unaryExpr
    : NOT unaryExpr
    | primaryExpr
    ;

primaryExpr
    : comparison
    | freeText
    | LPAREN booleanExpr RPAREN
    ;

comparison
    : fieldRef comparisonOp value
    | fieldRef IN LPAREN valueList RPAREN
    | fieldRef NOT IN LPAREN valueList RPAREN
    | fieldRef EXISTS
    | fieldRef NOT EXISTS
    ;

comparisonOp
    : EQ
    | NEQ
    | GT
    | GTE
    | LT
    | LTE
    | CONTAINS
    | NOT_CONTAINS
    | REGEX
    | NOT_REGEX
    ;

freeText
    : value
    ;

valueList
    : value (COMMA value)*
    ;

value
    : stringLiteral
    | numberLiteral
    | durationLiteral
    | timestampExpr
    | IDENTIFIER
    ;

timestampExpr
    : NOW LPAREN RPAREN ((PLUS | MINUS) durationLiteral)?
    | ISO_TIMESTAMP
    ;

durationLiteral
    : DURATION
    ;

numberLiteral
    : INTEGER
    | DECIMAL
    ;

pipelineOp
    : PIPE selectOp
    | PIPE removeOp
    | PIPE sortOp
    | PIPE limitOp
    | PIPE countByOp
    ;

selectOp
    : SELECT LPAREN fieldList RPAREN
    ;

removeOp
    : REMOVE LPAREN fieldList RPAREN
    ;

sortOp
    : SORT fieldRef sortDirection?
    ;

sortDirection
    : ASC
    | DESC
    ;

limitOp
    : LIMIT INTEGER
    ;

countByOp
    : COUNT BY LPAREN fieldList RPAREN
    ;

fieldList
    : fieldRef (COMMA fieldRef)*
    ;

fieldRef
    : fieldPath (LBRACK stringLiteral RBRACK)?
    ;

fieldPath
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

stringLiteral
    : SINGLE_QUOTED_STRING
    | DOUBLE_QUOTED_STRING
    ;
