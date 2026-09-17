Okapi QL testdata files contain parser corpus cases.

Each case starts with `parse <name>` or `fail <name>`, followed by a `query`
block and a terminating `end` line.

Example:

```
parse simple equality
query
  level = info
end

fail malformed comparison
query
  level = | limit 10
end
```

`parse` cases must be accepted by `OkapiQlParser.parse`.
`fail` cases must throw `IllegalArgumentException`.
