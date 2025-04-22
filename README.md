# 💬 Chat Service - P2P Chat App

This is the real-time chat microservice for the P2P Chat application. It handles WebSocket-based messaging, message persistence in MongoDB, and live delivery between connected users.

---

## 🚀 Features

- Real-time messaging using WebSocket
- JWT-secured WebSocket handshake
- Offline fallback (messages saved to MongoDB)
- Retrieve chat history between two users
- Built using Spring WebFlux and Reactive MongoDB

---

## 🧰 Tech Stack

- Spring Boot 3
- Spring WebFlux
- Spring Security
- Reactive MongoDB
- WebSocket (Spring)
- Gson (for JSON parsing)
- Lombok

---

## 🔌 WebSocket Endpoint
- **Endpoint**: `ws://localhost:8083/chat?token=<jwt_token>`

---

### ✅ Connect Using a Valid JWT Token
Send the token as a query parameter: `?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

---

## 📩 WebSocket Message Format

Send this as JSON over the socket:

```json
{
  "receiver": "username",
  "content": "Hello, bro!"
}
```
- If the receiver is online: they receive the message instantly
- If offline: message is saved in MongoDB for later retrieval

---

### 🧠 Message Entity

```json
{
  "id": "66256a87e2c6d2156b3e6f47",
  "sender": "username1",
  "receiver": "username2",
  "content": "Hello, bro!",
  "timestamp": "2025-04-22T14:15:36.236Z"
}
```
---
### 📦 REST Endpoints

| Method | Endpoint           | Description                      |Auth Required |
|--------|--------------------|----------------------------------|---------------|
| GET    | `/chats/{withUser}` | Fetch message history with user | ✅ Yes         |

---
### 🛠️ Running Locally
- Make sure MongoDB is running locally or via Docker

- Ensure auth-service is running to provide JWT tokens

- Start this service:

```bash
./mvnw spring-boot:run
```
- Ensure jwt.secret matches the one used in auth-service
---

### 🔐 JWT Integration
- Token validation happens in WebSocket handshake

- Chat sessions are tracked in-memory for live delivery

- Token must be passed as query param in WebSocket URL

---
### 🔄 Dependencies
This service depends on:
- **auth-service**: for generating JWT tokens

- **user-service**: for resolving valid users and contacts
