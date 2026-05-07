# How do I contribute?

Thank you for your interest in contributing to this project! I welcome any and all contributions, whether that be a simple bug report or a full new feature implemented, any help is much appreciated!

There are a few ways one can contribute to the project, those are:

- Creating a bug report/feature request on the [Issue Tracker](https://github.com/SakuraSedaia/PycharmBlenderWiki/issues)
- Assisting in i18n localization
- Forking and assisting in development of the source

## Contributing to Source Code or Localization

### Prerequisites 
- **JDK 21** (or later)
- **IntelliJ IDEA** – [Download](https://www.jetbrains.com/idea/)
- **DevKit Plugin** – Installed within IntelliJ IDEA to provide plugin development support.

## Dev Environment Setup

1. **Fork and Clone** the repository:
   ```bash
   git clone https://codeberg.org/SakuraSedaia/blender_pycharm.git
   cd blender_pycharm
   ```
2. **Open the project** in IntelliJ IDEA.
3. **Configure the JDK**: Go to `File > Project Structure > Project` and ensure the Project SDK is set to JDK 21.
4. **Import Gradle**: IntelliJ should automatically detect the `build.gradle.kts` file and import the project. If not, open the **Gradle** tool window and click the **Refresh** icon.

### Make changes

For Localization, all relevant source files are in 