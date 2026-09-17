parser grammar PromQLParser;

options {
    tokenVocab = PromQLLexer;
}

expression
    : vectorOperation EOF
    ;

// Binary operations are ordered by precedence

// Unary operations have the same precedence as multiplications

vectorOperation
    : <assoc = right> vectorOperation powOp vectorOperation # vecOpPow
    | <assoc = right> vectorOperation subqueryOp            # vecOpSubQuery
    | vectorOperation AT atValue offsetOp?                  # vecOpAt
    | vectorOperation SMOOTHED                              # vecOpSmoothed
    | unaryOp vectorOperation                               # vecOpUnary
    | vectorOperation multOp vectorOperation                # vecOpMult
    | vectorOperation addOp vectorOperation                 # vecOpAdd
    | vectorOperation compareOp vectorOperation             # vecOpCompare
    | vectorOperation andUnlessOp vectorOperation           # vecOpAddUnless
    | vectorOperation orOp vectorOperation                  # vecOpOr
    | vectorOperation vectorMatchOp vectorOperation         # vecOpMatch
    | vector                                                # vecOpvec
    ;

// Operators

unaryOp
    : (ADD | SUB)
    ;

powOp
    : POW grouping? fillModifier*
    ;

multOp
    : (MULT | DIV | MOD | ATAN2) grouping? fillModifier*
    ;

addOp
    : (ADD | SUB) grouping? fillModifier*
    ;

compareOp
    : (DEQ | NE | TRIM_LOWER | TRIM_UPPER | GT | LT | GE | LE) BOOL? grouping? fillModifier*
    ;

andUnlessOp
    : (AND | UNLESS) grouping?
    ;

orOp
    : OR grouping?
    ;

vectorMatchOp
    : (ON | UNLESS) grouping?
    ;

subqueryOp
    : subqueryRange offsetOp?
    ;

offsetOp
    : OFFSET offsetDurationExpr
    ;

vector
    : function_             # vecFunc
    | aggregation           # vecAgg
    | offset                # vecOffset
    | matrixSelector        # vecMatrix
    | instantSelector       # vecInstant
    | literal               # vecLiteral
    | parens                # vecParens
    ;

atValue
    : NUMBER
    | DURATION
    | METRIC_NAME LEFT_PAREN RIGHT_PAREN
    ;

parens
    : LEFT_PAREN vectorOperation RIGHT_PAREN
    ;

// Selectors

instantSelector
    : metricIdentifier (LEFT_BRACE labelMatcherList? RIGHT_BRACE)?
    | LEFT_BRACE labelMatcherList RIGHT_BRACE
    ;

metricIdentifier
    : METRIC_NAME
    | FUNCTION
    | AGGREGATION_OPERATOR
    ;

labelMatcher
    : labelName labelMatcherOperator STRING
    ;

labelMatcherOperator
    : EQ
    | NE
    | RE
    | NRE
    ;

labelMatcherList
    : labelMatcher (COMMA labelMatcher)* COMMA?
    ;

matrixSelector
    : instantSelector timeRange extendedVectorModifier?
    ;

extendedVectorModifier
    : ANCHORED
    | SMOOTHED
    ;

offset
    : instantSelector OFFSET offsetDurationExpr
    | matrixSelector OFFSET offsetDurationExpr
    ;

timeRange
    : LEFT_BRACKET durationExpr RIGHT_BRACKET
    ;

subqueryRange
    : LEFT_BRACKET durationExpr COLON durationExpr? RIGHT_BRACKET
    ;

// these are not fine-grained, certain functions are not allowed
// lexer and parsers need to be extended

function_
    : (FUNCTION | METRIC_NAME) LEFT_PAREN (parameter (COMMA parameter)*)? RIGHT_PAREN
    ;

parameter
    : literal
    | vectorOperation
    ;

parameterList
    : LEFT_PAREN (parameter (COMMA parameter)*)? RIGHT_PAREN
    ;

// Aggregations

aggregation
    : AGGREGATION_OPERATOR parameterList
    | AGGREGATION_OPERATOR (by | without) parameterList
    | AGGREGATION_OPERATOR parameterList ( by | without)
    ;

by
    : BY labelNameList
    ;

without
    : WITHOUT labelNameList
    ;

// Vector one-to-one/one-to-many joins

grouping
    : (on_ | ignoring) (groupLeft | groupRight)?
    ;

on_
    : ON labelNameList
    ;

ignoring
    : IGNORING labelNameList
    ;

groupLeft
    : GROUP_LEFT labelNameList?
    ;

groupRight
    : GROUP_RIGHT labelNameList?
    ;

fillModifier
    : METRIC_NAME LEFT_PAREN signedFillLiteral RIGHT_PAREN
    ;

signedFillLiteral
    : (ADD | SUB)? (NUMBER | NAN | INF)
    ;

// Label names

labelName
    : keyword
    | METRIC_NAME
    | LABEL_NAME
    ;

labelNameList
    : LEFT_PAREN (labelName (COMMA labelName)*)? RIGHT_PAREN
    ;

keyword
    : AND
    | OR
    | UNLESS
    | BY
    | WITHOUT
    | ON
    | IGNORING
    | GROUP_LEFT
    | GROUP_RIGHT
    | OFFSET
    | BOOL
    | AGGREGATION_OPERATOR
    | FUNCTION
    ;

literal
    : NUMBER
    | STRING
    | DURATION
    | NAN
    | INF
    ;

durationExpr
    : durationAddExpr
    ;

durationAddExpr
    : durationMultExpr ((ADD | SUB) durationMultExpr)*
    ;

durationMultExpr
    : durationUnaryExpr ((MULT | DIV | MOD) durationUnaryExpr)*
    ;

durationPowExpr
    : durationPrimaryExpr (POW durationUnaryExpr)?
    ;

durationUnaryExpr
    : (ADD | SUB) durationUnaryExpr
    | durationPowExpr
    ;

durationPrimaryExpr
    : DURATION
    | NUMBER
    | durationFunction
    | LEFT_PAREN durationExpr RIGHT_PAREN
    ;

durationFunction
    : (METRIC_NAME | AGGREGATION_OPERATOR) LEFT_PAREN (durationExpr (COMMA durationExpr)*)? RIGHT_PAREN
    ;

// Keep vector operators outside an unparenthesized offset operand. For example,
// "metric offset step() * 2" offsets first and then multiplies the vector.
offsetDurationExpr
    : (ADD | SUB)? durationPrimaryExpr
    ;
