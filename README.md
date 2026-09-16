<p align="center">
  <img src="assets/sketchware-Plus.jpg" style="width: 30%;" />
</p>

# Sketchware Plus

[![GitHub contributors](https://img.shields.io/github/contributors/Sketchware-Pro/Sketchware-Pro)](https://github.com/Sketchware-Pro/Sketchware-Pro/graphs/contributors)
[![GitHub last commit](https://img.shields.io/github/last-commit/Sketchware-Pro/Sketchware-Pro)](https://github.com/Sketchware-Pro/Sketchware-Pro/commits/)
[![Discord server stats](https://img.shields.io/discord/790686719753846785)](http://discord.gg/kq39yhT4rX)
[![Total downloads](https://img.shields.io/github/downloads/Sketchware-Pro/Sketchware-Pro/total)](https://github.com/Sketchware-Pro/Sketchware-Pro/releases)

Sketchware Plus is a community-driven enhancement of the original Sketchware, built upon the foundations of [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro). This project focuses on providing modern development tools and advanced AI integration to the Sketchware ecosystem.

## Key Features

### AI Assistant (SK Assistant)
Sketchware Plus integrates an advanced AI assistant to streamline the development process:
- Smart Code Generation: Create Java and Kotlin logic using natural language descriptions.
- Manifest and Security Specialist: Automated handling of AndroidManifest.xml injections and security auditing.
- Automated UI Refactoring: Modify layout XML files with safety through the SketchwareXmlBridge.
- Intelligent Debugging: Receive detailed analysis and suggested resolutions for compilation errors.

### Advanced Development Tools
- Modern SDK Support: Support for higher API levels and updated Android libraries.
- Kotlin Integration: Full support for Kotlin source files and compilation within projects.
- Enhanced Library Management: Integrated support for Jetpack and third-party dependencies.

## Architecture and Source Map

| Component | Responsibility |
| :--- | :--- |
| `a.a.a.ProjectBuilder` | Orchestrates the build and compilation pipeline |
| `a.a.a.Ix` | Core logic for AndroidManifest.xml generation |
| `a.a.a.Jx` | Java source code generator for Activities |
| `a.a.a.Lx` | Component and event listener logic generator |
| `a.a.a.Ox` | Layout XML generator and manager |
| `sketchware.plus.ai.AiClient` | JNI interface for AI backend communication |
| `sketchware.plus.ai.SkAssistantFragment` | Primary UI and logic for the SK Assistant |
| `sketchware.plus.ai.SketchwareXmlBridge` | Synchronizes AI-driven changes with the layout engine |

> [!TIP]
> For a full breakdown of parameter specifiers, component inventories, and code generation mappings, see the [Blocks Analysis Guide](app/src/main/assets/Blocks_Analysis.md).

## Building and Contributing

### Building the Project
Building the application requires Gradle. For the optimal development experience, the use of Android Studio is recommended.

### Contribution Guidelines
1. Fork the repository and create a feature branch.
2. Implement changes, preferably within the `sketchware.plus` package.
3. Verify changes through build and runtime testing.
4. Submit a pull request with a detailed description of the modifications.

Note: Contributions should adhere to the existing codebase structure. The use of Java is preferred for core modifications unless Kotlin is required for specific functionality.

## Credits
This project is a mod of [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro). We are grateful to the Sketchware Pro team and the original Sketchware developers for their foundational work in the mobile development space.

## Disclaimer
Sketchware Plus is a community-led project and is not affiliated with the original Sketchware developers. It is provided for educational and community preservation purposes. We do not authorize the publication of Sketchware Plus on commercial application stores. 

For those wishing to support the original developers, please visit their [Patreon page](https://www.patreon.com/sketchware).
