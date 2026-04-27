# Generic Context

## Kotlin Styling Practices
- Follow the official [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html).
- Use `val` by default. Only use `var` when mutability is strictly required.
- Prefer expression bodies for single-expression functions.
- Leverage Kotlin's null safety effectively; avoid `!!` where possible, prefer `?:` (elvis operator) or `safe calls`.
- Use `PascalCase` for classes, interfaces, and objects.
- Use `camelCase` for functions, variables, and properties.
- Use `UPPER_SNAKE_CASE` for top-level or object constants.
- Prefer string templates over concatenation.

## Python Styling Practices
- When Python code exists, follow PEP 8, prefer the standard library, and keep nesting minimal (<= 4 indents).
