# GD-CORS

基于 RAG（检索增强生成）技术的智能问答系统，支持文档上传、管理和基于文档的智能对话。

## 📋 项目简介

GD-CORS 是一个基于 Spring Boot 和 LangChain4j 构建的智能问答系统，集成了文件管理、会话管理和 AI 对话功能。 通过向量数据库存储和检索，实现基于知识库的智能问答。

## 🛠️ 技术栈

### 核心框架
- **后端框架**: Spring Boot 3.5.3
- **Java 版本**: Java 17
- **Web 框架**: Spring Web + Spring WebFlux（混合模式，支持流式响应 SSE）
- **构建工具**: Maven

### AI 框架
- **LangChain4j**: 1.8.0

### 数据存储
- **数据库**: MySQL 8.0.36
- **ORM**: MyBatis 3.0.3 + MyBatis Plus 3.5.5
- **缓存**: Redis 8.2.0
- **消息队列**: RabbitMQ 3.8-management
- **向量数据库**: Milvus 2.6.1

### 安全与工具
- **认证**: JWT（java-jwt 4.4.0）
- **安全框架**: Spring Security Core
- **AOP**: Spring AOP
- **工具库**: Lombok
- **序列化**: Protobuf Java 3.25.3

## ✨ 主要功能

### 1. 用户管理
- 用户注册/登录
- JWT Token 认证
- 个人信息管理
- 用户注销
- 管理员用户管理（查看用户列表、删除用户等）

### 2. 文件管理
- 文件上传
- 文件下载
- 文件夹创建
- 文件/文件夹重命名
- 文件移动
- 文件删除
- 文件列表查询

### 3. 会话管理
- 创建会话
- 查看所有会话
- 查询会话消息历史
- 更新会话标题
- 删除会话
- 查询会话消息历史

### 4. AI 智能对话
- 基于 RAG 的流式对话
- 支持会话记忆
- 支持本地文件（LOCAL）和在线（ONLINE）两种会话模式
- 实时流式响应（Server-Sent Events）