Aş# Contributing to ReportSystem

Thank you for your interest in contributing to ReportSystem! This document provides guidelines and instructions for contributing.

## 🤝 How to Contribute

### Reporting Bugs
1. **Check existing issues** - Search [GitHub Issues](https://github.com/yourusername/ReportSystem/issues) first
2. **Create detailed report** - Include:
   - Minecraft version
   - Plugin version
   - Server software (Paper/Spigot/Purpur)
   - Java version
   - Steps to reproduce
   - Error messages/logs
   - Screenshots if applicable

### Suggesting Features
1. **Check existing suggestions** - Avoid duplicates
2. **Explain the use case** - Why is this feature needed?
3. **Provide examples** - How would it work?
4. **Consider impact** - Performance, compatibility, complexity

### Pull Requests
1. **Fork the repository**
2. **Create a feature branch** - `git checkout -b feature/your-feature-name`
3. **Make your changes**
4. **Test thoroughly** - Ensure nothing breaks
5. **Follow code style** - Match existing code formatting
6. **Commit with clear messages** - Describe what and why
7. **Push and create PR** - Provide detailed description

## 🔧 Development Setup

### Prerequisites
- **Java 17** or higher
- **Maven 3.6+**
- **Git**
- **IDE** - IntelliJ IDEA recommended

### Building from Source
```bash
git clone https://github.com/yourusername/ReportSystem.git
cd ReportSystem
mvn clean package
```

Compiled JARs will be in:
- `spigot/target/ReportSystem-Spigot-*.jar`
- `bungeecord/target/ReportSystem-Bungee-*.jar`

### Project Structure
```
ReportSystem/
├── common/          # Shared code (models, database, services)
├── spigot/          # Spigot/Paper implementation
├── bungeecord/      # BungeeCord/Waterfall implementation
├── pom.xml          # Parent Maven configuration
└── README.md
```

## 📝 Code Style Guidelines

### Java Code
- **Indentation**: 4 spaces (no tabs)
- **Braces**: Opening brace on same line
- **Naming**:
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Comments**: JavaDoc for public methods
- **Imports**: No wildcard imports (`import java.util.*`)

### Example
```java
public class ExampleClass {
    private static final String CONSTANT_VALUE = "value";
    private String fieldName;

    /**
     * Method description
     * @param parameter Description
     * @return Description
     */
    public String methodName(String parameter) {
        if (parameter == null) {
            return null;
        }
        return parameter.toUpperCase();
    }
}
```

### Configuration Files
- **YAML**: 2 spaces indentation
- **Comments**: Explain non-obvious settings
- **Keys**: lowercase with dashes (`auto-delete-days`)

## 🧪 Testing

### Before Submitting PR
- [ ] Code compiles without errors
- [ ] No warnings in console
- [ ] Tested on fresh server install
- [ ] Tested with MySQL and SQLite
- [ ] Tested on Paper 1.21 (minimum)
- [ ] All features work as expected
- [ ] No performance regressions

### Test Server Setup
1. Download Paper 1.21
2. Install PacketEvents 2.0+
3. Install your compiled JAR
4. Test all features:
   - Creating reports
   - Viewing reports
   - Replays recording and playback
   - Overwatch system
   - Punishments
   - BungeeCord (if applicable)

## 🌍 Translation

### Adding a New Language
1. Copy `messages_en.yml` to `messages_XX.yml` (XX = language code)
2. Translate all messages
3. Test color codes work correctly
4. Submit PR with language file

### Language Code Examples
- English: `en`
- Turkish: `tr`
- German: `de`
- French: `fr`
- Spanish: `es`

## 🐛 Bug Fix Process

1. **Identify the bug** - Reproduce reliably
2. **Find the root cause** - Debug and trace
3. **Fix the issue** - Minimal changes
4. **Test thoroughly** - Ensure fix works
5. **Check for side effects** - No new bugs introduced
6. **Submit PR** - Reference issue number

## ✨ Feature Development Process

1. **Discuss first** - Open an issue or discussion
2. **Get feedback** - Ensure feature is desired
3. **Design API** - How will it work?
4. **Implement** - Write clean, documented code
5. **Test extensively** - All scenarios
6. **Update docs** - README, wiki, config comments
7. **Submit PR** - Detailed description

## 📚 Documentation

### What Needs Documentation
- New features
- Changed behavior
- Configuration options
- API methods
- Commands and permissions

### Where to Document
- **README.md** - Main plugin documentation
- **Code comments** - Explain complex logic
- **Config files** - Inline comments
- **Wiki** - Detailed guides (future)

## 🚫 What NOT to Contribute

- Malicious code or backdoors
- Code that violates Mojang EULA
- Copyrighted/proprietary code
- Unnecessary dependencies
- Breaking changes without discussion
- Code that significantly impacts performance

## 📜 Code of Conduct

### Be Respectful
- Treat everyone with respect
- Accept constructive criticism
- Focus on the issue, not the person

### Be Professional
- Use appropriate language
- Provide constructive feedback
- Help others learn

### Be Patient
- Remember maintainers are volunteers
- PRs may take time to review
- Not all suggestions will be accepted

## 🏆 Recognition

Contributors will be:
- Listed in README.md
- Mentioned in release notes
- Credited in plugin info

## 📞 Getting Help

- **Questions**: Open a discussion on GitHub
- **Issues**: Use GitHub Issues
- **Chat**: Join our Discord server
- **Email**: [Not yet available]

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to ReportSystem! 🎉
