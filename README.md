# SwiftDrop

SwiftDrop is a full-stack web application designed for fast, modern service delivery. The repository is organized as a monorepo containing both the Spring Boot backend and the React Vite frontend.

---

## 📁 Repository Structure

```text
SwiftDrop/
├── backend/            # Spring Boot REST API (Java 21, Maven)
│   ├── src/
│   ├── mvnw.cmd / mvnw # Maven Wrapper
│   └── pom.xml
├── frontend/           # Single Page Application (React 19, TypeScript, Vite)
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
└── README.md
```

---

## 📋 Prerequisites

Make sure the following tools are installed on your machine:

- **Java Development Kit (JDK)**: JDK 21
- **Node.js**: v18+ or v20+ (with `npm`)
- **Git**

---

## 🚀 Getting Started

Clone the repository to your local machine:
```bash
git clone <your-repository-url>
cd SwiftDrop
```

---

### 1. Running the Backend (Spring Boot)

#### Option A: Using the Terminal (Maven Wrapper)
Navigate into the `backend` folder:
```bash
cd backend
```

- **Windows (PowerShell / Command Prompt):**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **macOS / Linux:**
  ```bash
  ./mvnw spring-boot:run
  ```

#### Option B: Using IntelliJ IDEA
1. Open IntelliJ IDEA.
2. Open either the `backend` folder or the root `SwiftDrop` folder.
3. Ensure the project SDK is set to **JDK 21** (`File > Project Structure > Project > SDK`).
4. Locate `src/main/java/com/swiftdrop/backend/BackendApplication.java`.
5. Right-click and select **Run 'BackendApplication'**.

The backend server will start at: **`http://localhost:8080`**

---

### 2. Running the Frontend (React + Vite)

Open a new terminal window and navigate into the `frontend` folder:
```bash
cd frontend
```

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Start the development server:**
   ```bash
   npm run dev
   ```

The frontend application will start at: **`http://localhost:5173`** (or the port specified in terminal).

---

## ⚙️ Building for Production

### Backend Build
Inside `backend/`:
```bash
.\mvnw.cmd clean package
```
The executable JAR file will be generated in `backend/target/`.

### Frontend Build
Inside `frontend/`:
```bash
npm run build
```
Production assets will be generated in `frontend/dist/`.
