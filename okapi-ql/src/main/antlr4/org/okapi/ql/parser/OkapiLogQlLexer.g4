lexer grammar OkapiLogQlLexer;

LPAREN: '(';
RPAREN: ')';
LBRACK: '[';
RBRACK: ']';
COMMA: ',';
DOT: '.';
PIPE: '|';
PLUS: '+';
MINUS: '-';

GTE: '>=';
LTE: '<=';
NEQ: '!=';
NOT_REGEX: '!~';
NOT_CONTAINS: '!:';
REGEX: '=~' | '~=';
GT: '>';
LT: '<';
EQ: '=';
CONTAINS: ':';

WITH: W I T H;
AND: A N D;
OR: O R;
NOT: N O T;
IN: I N;
EXISTS: E X I S T S;
SELECT: S E L E C T;
REMOVE: R E M O V E;
SORT: S O R T;
ASC: A S C;
DESC: D E S C;
LIMIT: L I M I T;
COUNT: C O U N T;
BY: B Y;
NOW: N O W;

DURATION: DIGIT+ ('.' DIGIT+)? (N S | U S | M S | S | M | H | D | W);
ISO_TIMESTAMP: DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT
    (T DIGIT DIGIT ':' DIGIT DIGIT ':' DIGIT DIGIT ('.' DIGIT+)? (Z | (PLUS | MINUS) DIGIT DIGIT ':' DIGIT DIGIT)?)?;
DECIMAL: DIGIT+ '.' DIGIT+;
INTEGER: DIGIT+;

DOUBLE_QUOTED_STRING: '"' (ESC | ~["\\])* '"';
SINGLE_QUOTED_STRING: '\'' (ESC | ~['\\])* '\'';

IDENTIFIER: [A-Za-z_][A-Za-z0-9_-]*;

SL_COMMENT: '#' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;

fragment ESC: '\\' ["'\\/bfnrt];
fragment DIGIT: [0-9];

fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];
