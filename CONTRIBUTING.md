# 贡献指南 / Contribution Guide

## 欢迎贡献 / Welcome Contributions

我们非常欢迎社区成员为LXCLUA项目做出贡献！无论是修复bug、添加新功能、改进文档还是其他方面的贡献，都将对项目的发展起到重要作用。

We greatly welcome community members to contribute to the LXCLUA project! Whether it's fixing bugs, adding new features, improving documentation, or other contributions, they will all play an important role in the project's development.

## 贡献方式 / Ways to Contribute

### 1. 提交问题 / Submit Issues

- **Bug报告**：如果您发现了bug，请提交详细的bug报告，包括复现步骤、预期行为和实际行为
- **功能请求**：如果您有新功能的想法，请提交功能请求，详细描述您希望添加的功能
- **文档改进**：如果您发现文档有问题或可以改进，请提交文档改进请求

- **Bug Reports**: If you find a bug, please submit a detailed bug report, including reproduction steps, expected behavior, and actual behavior
- **Feature Requests**: If you have ideas for new features, please submit feature requests, describing in detail the features you would like to add
- **Documentation Improvements**: If you find issues with the documentation or ways to improve it, please submit documentation improvement requests

### 2. 提交代码 / Submit Code

- **修复bug**：修复已报告的bug
- **添加新功能**：实现新的功能
- **改进代码**：优化现有代码，提高性能或可读性
- **更新依赖**：更新项目依赖库

- **Fix Bugs**: Fix reported bugs
- **Add New Features**: Implement new features
- **Improve Code**: Optimize existing code, improve performance or readability
- **Update Dependencies**: Update project dependency libraries

### 3. 改进文档 / Improve Documentation

- **更新README**：更新项目的README.md文件
- **添加API文档**：为新功能添加API文档
- **翻译文档**：将文档翻译成其他语言

- **Update README**: Update the project's README.md file
- **Add API Documentation**: Add API documentation for new features
- **Translate Documentation**: Translate documentation into other languages

## 贡献流程 / Contribution Process

### 1. 准备工作 / Preparation

1. **Fork仓库**：在GitHub上fork LXCLUA仓库到您自己的账号
2. **克隆仓库**：将fork后的仓库克隆到本地
3. **创建分支**：在本地仓库中创建一个新的分支，用于您的贡献

1. **Fork the Repository**: Fork the LXCLUA repository on GitHub to your own account
2. **Clone the Repository**: Clone the forked repository to your local machine
3. **Create a Branch**: Create a new branch in your local repository for your contribution

```bash
# 克隆仓库
# Clone the repository
git clone https://github.com/OxenFxc/LXCLUA-CORE-CODE.git

# 进入仓库目录
# Enter the repository directory
cd LXCLUA-CORE-CODE

# 创建并切换到新分支
# Create and switch to a new branch
git checkout -b feature/your-feature-name
# 或 for bug fixes
# or for bug fixes
git checkout -b fix/your-bug-fix
```

### 2. 开发 / Development

1. **编写代码**：实现您的功能或修复bug
2. **遵循编码规范**：确保您的代码遵循项目的编码规范（详见[STYLE_GUIDE.md](STYLE_GUIDE.md)）
3. **测试代码**：确保您的代码能够正常工作，没有引入新的bug
4. **更新文档**：如果您的更改需要更新文档，请一并更新

1. **Write Code**: Implement your feature or fix the bug
2. **Follow Coding Standards**: Ensure your code follows the project's coding standards (see [STYLE_GUIDE.md](STYLE_GUIDE.md) for details)
3. **Test Code**: Ensure your code works correctly and doesn't introduce new bugs
4. **Update Documentation**: If your changes require documentation updates, please update them together

### 3. 提交代码 / Commit Code

1. **添加更改**：将您的更改添加到暂存区
2. **提交更改**：提交您的更改，使用清晰的提交信息
3. **推送到远程**：将您的更改推送到您fork的远程仓库

1. **Add Changes**: Add your changes to the staging area
2. **Commit Changes**: Commit your changes with a clear commit message
3. **Push to Remote**: Push your changes to your forked remote repository

```bash
# 添加更改
# Add changes
git add .

# 提交更改
# Commit changes
git commit -m "Clear commit message describing your changes"

# 推送到远程
# Push to remote
git push origin feature/your-feature-name
```

### 4. 创建PR / Create Pull Request

1. **创建PR**：在GitHub上创建一个新的Pull Request，从您的分支到LXCLUA的主分支
2. **填写PR描述**：详细描述您的更改，包括解决的问题、实现的功能等
3. **等待审查**：等待项目维护者审查您的PR
4. **响应反馈**：根据审查者的反馈进行修改

1. **Create PR**: Create a new Pull Request on GitHub from your branch to the LXCLUA main branch
2. **Fill in PR Description**: Describe your changes in detail, including the problems solved, features implemented, etc.
3. **Wait for Review**: Wait for the project maintainers to review your PR
4. **Respond to Feedback**: Make changes based on the reviewers' feedback

## 代码审查标准 / Code Review Standards

### 1. 代码质量 / Code Quality

- **可读性**：代码应该易于阅读和理解
- **可维护性**：代码应该易于维护和扩展
- **性能**：代码应该具有良好的性能
- **安全性**：代码应该安全，没有安全漏洞

- **Readability**: Code should be easy to read and understand
- **Maintainability**: Code should be easy to maintain and extend
- **Performance**: Code should have good performance
- **Security**: Code should be secure, with no security vulnerabilities

### 2. 代码风格 / Code Style

- **遵循编码规范**：代码应该遵循项目的编码规范（详见[STYLE_GUIDE.md](STYLE_GUIDE.md)）
- **一致的风格**：代码风格应该与项目现有代码保持一致
- **适当的注释**：代码应该有适当的注释，解释复杂的逻辑

- **Follow Coding Standards**: Code should follow the project's coding standards (see [STYLE_GUIDE.md](STYLE_GUIDE.md) for details)
- **Consistent Style**: Code style should be consistent with the project's existing code
- **Appropriate Comments**: Code should have appropriate comments explaining complex logic

### 3. 测试 / Testing

- **功能测试**：确保代码能够正常工作
- **边界测试**：测试边界情况
- **回归测试**：确保没有引入新的bug

- **Functional Testing**: Ensure code works correctly
- **Boundary Testing**: Test boundary cases
- **Regression Testing**: Ensure no new bugs are introduced

### 4. 文档 / Documentation

- **更新文档**：如果更改了功能，应该更新相关文档
- **API文档**：为新功能添加API文档
- **示例代码**：为新功能添加示例代码

- **Update Documentation**: If functionality is changed, relevant documentation should be updated
- **API Documentation**: Add API documentation for new features
- **Example Code**: Add example code for new features

## 行为准则 / Code of Conduct

参与LXCLUA项目的所有贡献者都应该遵守我们的行为准则（详见[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)）。

All contributors participating in the LXCLUA project should adhere to our code of conduct (see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details).

## 常见问题 / Frequently Asked Questions

### 1. 如何获取帮助？ / How to Get Help?

- **GitHub Issues**：在GitHub上提交issue
- **邮件**：发送邮件到difierline@yeah.net
- **社区**：参与项目的社区讨论

- **GitHub Issues**: Submit an issue on GitHub
- **Email**: Send an email to difierline@yeah.net
- **Community**: Participate in the project's community discussions

### 2. 如何报告安全问题？ / How to Report Security Issues?

如果您发现了安全问题，请通过邮件直接联系我们，不要在GitHub上公开报告，以避免安全漏洞被利用。

If you find a security issue, please contact us directly via email, do not report it publicly on GitHub to avoid security vulnerabilities being exploited.

### 3. 贡献被接受的标准是什么？ / What are the Criteria for Contributions to be Accepted?

- **符合项目目标**：贡献应该符合项目的整体目标和方向
- **代码质量**：代码应该具有良好的质量和风格
- **测试通过**：代码应该通过测试
- **文档完整**：应该有完整的文档

- **Align with Project Goals**: Contributions should align with the project's overall goals and direction
- **Code Quality**: Code should have good quality and style
- **Pass Tests**: Code should pass tests
- **Complete Documentation**: Documentation should be complete

## 致谢 / Acknowledgments

感谢所有为LXCLUA项目做出贡献的开发者和社区成员！您的贡献是项目成功的关键。

Thank you to all developers and community members who have contributed to the LXCLUA project! Your contributions are key to the project's success.

---

© 2026 DifierLine. All rights reserved.
