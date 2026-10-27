# BaaS Platform

A scalable, multi-tenant Backend-as-a-Service (BaaS) platform built with Java Spring Boot and PostgreSQL.

BaaS Platform provides developers with production-ready backend infrastructure including authentication, project isolation, API key management, and dynamic JSON document storage.

The platform is designed around modern backend engineering principles such as stateless authentication, layered architecture, dynamic schema handling, and multi-tenant system design.

---

# Let an AI agent build your backend (MCP)

The API also speaks [MCP](https://modelcontextprotocol.io), so Claude (or any MCP client) can set up a backend for you: tell it *"create a backend for my todo app with a few sample todos and a test user"* and it creates the project, collections, data and users.

**Try it on the live demo server**, `https://baas-demo.onrender.com` (free tier: the first request after a quiet spell takes up to a minute while it wakes up). Running your own copy? Use `http://localhost:8080` instead.

**1. Get a developer token** (sign up once, then log in):

```bash
curl -X POST https://baas-demo.onrender.com/auth/signup -H 'Content-Type: application/json' \
  -d '{"name":"You","email":"you@example.com","password":"your-password"}'
curl -X POST https://baas-demo.onrender.com/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}'
```

**2. Connect Claude Code** with the token from the response:

```bash
claude mcp add --transport http baas https://baas-demo.onrender.com/mcp --header "Authorization: Bearer <token>"
```

**3. Ask Claude** in an empty folder: *"Create a backend for my book club app with sample data and a test user."*

Tokens last 24 hours; log in again and re-add the server when one expires. The demo server is for trying things out: don't store anything private there.

| Tool | What it does |
|---|---|
| `list_projects` | Your projects |
| `create_project` | New project + API key. Safe to retry: an existing project with the same name is returned, not duplicated |
| `get_api_key` | A project's API key and how to call the data API with it |
| `list_collections` | Collections, document count and app-user count for a project |
| `insert_document` | Store a JSON document (collections are created on first insert) |
| `query_documents` | Read a collection, newest first, with an optional exact-match filter |
| `create_app_user` | Register an end user of your app |

Every tool runs as the developer who owns the token, and only works on that developer's projects. `scripts/mcp-smoke.sh` checks the whole flow end to end.

---

# Table of Contents

* Let an AI agent build your backend (MCP)
* Overview
* Core Features
* System Architecture
* Technology Stack
* Authentication Architecture
* Multi-Tenant Design
* Dynamic Data Engine
* Security Model
* API Reference
* Project Structure
* Database Design
* Installation & Setup
* Configuration
* Running the Application
* Example Workflow
* Future Roadmap
* Contributing
* License

---

# Overview

BaaS Platform enables developers to integrate backend functionality into their applications without building backend infrastructure from scratch.

The platform exposes APIs for:

* Authentication
* Project management
* Dynamic document storage
* App user management
* API key-based access

The architecture is inspired by platforms such as Firebase, Supabase, and Auth0.

---

# Core Features

## Authentication as a Service

The platform provides JWT-based authentication for:

* Platform developers
* Application end users

Features include:

* User registration
* Secure login
* Password hashing using BCrypt
* JWT token generation
* Stateless authentication

---

## Multi-Tenant Project Management

Each developer account can create and manage multiple isolated projects.

Every project includes:

* Unique Project ID
* Dedicated API key
* Isolated application users
* Isolated collections and documents

Example:

```text
Developer
 ├── ChatApp
 ├── EcommercePlatform
 └── NotesApplication
```

---

## Dynamic JSON Document Storage

The platform supports schema-less JSON document storage using PostgreSQL JSONB.

Developers can store any JSON structure dynamically without defining database entities.

Example:

```json
{
  "title": "Hello World",
  "views": 120,
  "published": true
}
```

Supported use cases:

* Posts
* Products
* Orders
* Messages
* Activity feeds
* Custom application data

---

## API Key Security

Application-level APIs are secured using project API keys.

Example:

```http
x-api-key: sk_xxxxxxxxxxxxxxxxx
```

---

## Centralized Exception Handling

The platform uses a global exception handling mechanism for:

* Validation errors
* Authentication failures
* Business logic exceptions
* API consistency

Standardized API responses improve frontend integration and SDK development.

---

# System Architecture

The platform follows a layered architecture.

```text
Controller Layer
        ↓
Service Layer
        ↓
Repository Layer
        ↓
PostgreSQL Database
```

## Architectural Principles

* Stateless authentication
* Separation of concerns
* DTO-driven API contracts
* Multi-tenant isolation
* API-first backend design
* Centralized security

---

# Technology Stack

## Backend Framework

* Java 21
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate

## Database

* PostgreSQL
* PostgreSQL JSONB

## Authentication & Security

* JWT (JSON Web Tokens)
* BCrypt Password Encoder
* API Key Authentication

## Build Tools

* Maven
* Lombok
* Jackson

---

# Authentication Architecture

The platform contains two independent authentication systems.

---

## 1. Platform Authentication

Used by developers who manage projects on the platform.

### Endpoints

```http
POST /auth/signup
POST /auth/login
```

Returns JWT access token.

---

## 2. App User Authentication

Used by client applications to authenticate their own users.

### Endpoints

```http
POST /sdk/auth/signup
POST /sdk/auth/login
```

Protected using:

```http
x-api-key
```

This enables developers to use the platform as an external authentication provider.

---

# Multi-Tenant Design

The platform uses project-level tenant isolation.

Each project has:

* Independent application users
* Independent collections
* Independent document storage
* Dedicated API credentials

Example:

```text
ChatApp Users ≠ Ecommerce Users
```

This ensures proper tenant separation and secure project boundaries.

---

# Dynamic Data Engine

The platform stores dynamic application data using PostgreSQL JSONB.

## Collection-Based API Design

```http
POST /api/{projectId}/{collection}
GET  /api/{projectId}/{collection}
```

Example:

```http
POST /api/project-id/posts
```

The system dynamically stores JSON documents without requiring schema migrations.

---

# Security Model

## JWT Authentication

Used for:

* Platform authentication
* App user authentication

Features:

* Stateless authentication
* Token expiration
* Secure signature verification

---

## Password Encryption

Passwords are encrypted using BCrypt before storage.

The platform never stores plain-text passwords.

---

## API Key Protection

Dynamic collection APIs are protected using project-specific API keys.

---

## Validation & Exception Handling

The platform includes:

* Request validation
* Centralized exception handling
* Standardized API responses

---

# API Reference

## Platform Authentication

### Register Platform User

```http
POST /auth/signup
```

Request:

```json
{
  "name": "Vishal",
  "email": "vishal@gmail.com",
  "password": "123456"
}
```

---

### Login Platform User

```http
POST /auth/login
```

Response:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "jwt-token"
  }
}
```

---

## Project APIs

### Create Project

```http
POST /projects
Authorization: Bearer JWT_TOKEN
```

Response:

```json
{
  "success": true,
  "message": "Project created successfully",
  "data": {
    "id": "project-id",
    "name": "ChatApp",
    "apiKey": "sk_xxxxx"
  }
}
```

---

## Dynamic Data APIs

### Insert Document

```http
POST /api/{projectId}/{collection}
```

Headers:

```http
x-api-key: PROJECT_API_KEY
```

Request:

```json
{
  "title": "Hello World",
  "likes": 120
}
```

---

### Fetch Documents

```http
GET /api/{projectId}/{collection}
```

---

## App User Authentication APIs

### Register App User

```http
POST /sdk/auth/signup
```

### Login App User

```http
POST /sdk/auth/login
```

---

# Project Structure

```text
src/main/java/com/vishal/baas_platform/
│
├── config/
├── controller/
├── dto/
├── entity/
├── exception/
├── repository/
├── security/
├── service/
├── util/
│
└── BaasPlatformApplication.java
```

---

# Database Design

## users

Stores platform developer accounts.

---

## projects

Stores:

* Project metadata
* API keys
* Ownership information

---

## app_users

Stores application users for client projects.

---

## data_records

Stores dynamic JSON documents using PostgreSQL JSONB.

---

# Installation & Setup

## Clone Repository

```bash
git clone https://github.com/your-username/baas-platform.git
```

---

## Navigate to Project

```bash
cd baas-platform
```

---

## Configure PostgreSQL

Create database:

```sql
CREATE DATABASE baas_db;
```

---

# Configuration

Configure application properties:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/baas_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

jwt.secret=yourSuperSecretKeyForJwtTokenGeneration123456
```

---

# Running the Application

Run the Spring Boot application:

```bash
mvn spring-boot:run
```

Application will start on:

```text
http://localhost:8080
```

---

# Example Workflow

## Step 1 — Register Developer

```http
POST /auth/signup
```

---

## Step 2 — Login

```http
POST /auth/login
```

Receive JWT token.

---

## Step 3 — Create Project

```http
POST /projects
```

Receive:

* Project ID
* API Key

---

## Step 4 — Store Dynamic Data

```http
POST /api/{projectId}/posts
```

---

## Step 5 — Authenticate App Users

```http
POST /sdk/auth/signup
```

---

# Future Roadmap

Planned improvements:

* Swagger/OpenAPI Documentation
* Query Filtering
* Pagination
* Refresh Tokens
* Realtime WebSockets
* File Storage Service
* Rate Limiting
* SDK Generation
* Role-Based Access Control
* Dynamic Schema Validation
* React Admin Dashboard
* Analytics & Monitoring

---

# Contributing

Contributions, improvements, and suggestions are welcome.

To contribute:

1. Fork repository
2. Create feature branch
3. Commit changes
4. Open pull request

---

# License

This project is currently intended for educational, learning, and portfolio purposes.
