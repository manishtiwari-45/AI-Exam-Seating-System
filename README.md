# ExamAI - Intelligent Exam Seating & Management System

### About The Project
ExamAI is a full-stack application designed to automate the chaotic process of exam seating. Instead of manually arranging students, this system uses an **AI Algorithm** to intelligently place students in seats.

The main goal? **Prevent Cheating.** The system automatically detects students from the same branch (e.g., CSE) and ensures they are never seated next to each other, all while filling rooms efficiently.

---

We built this project entirely using **Plain Java (Core Java)** because I wanted to master the fundamentals before relying on magic frameworks. By avoiding libraries, I had to manually handle:
* **HTTP Requests:** I built my own API handling using Java's `HttpServer`.
* **Database interactions:** I wrote raw JDBC queries to understand how data actually moves.
* **State Management:** I handled the logic flow without dependency injection containers.

---

### Tech Stack & Skills Demonstrated

* **Language:** Core Java (JDK 24)
* **Database:** MySQL (Relational Schema Design)
* **Connectivity:** JDBC (Java Database Connectivity)
* **Frontend:** Vanilla JavaScript, HTML5, CSS3 (Modern Dashboard UI)
* **Architecture:** MVC (Model-View-Controller)

---

###  Engineering Highlights

Here are the key technical concepts We implemented in this project:

#### 1. Object-Oriented Design (OOP)
I structured the code using strict OOP principles to keep it clean and scalable:
* **DAO Pattern:** I separated all database logic (`StudentDAO`, `RoomDAO`) from the business logic.
* **Strategy Pattern:** The allocation logic is modular. I can swap between a "Greedy" algorithm or an "AI" algorithm without breaking the app.
* **Service Layer:** The `AllocationService` acts as the brain, coordinating between the database and the algorithms.

#### 2. The AI Logic
The seating isn't random. I implemented a local search algorithm that:
1.  Places students in seats.
2.  Calculates a "Risk Score" based on neighbors (e.g., Two CSE students sitting together = High Risk).
3.  Swaps students repeatedly to lower the risk score until it finds the optimal arrangement.

#### 3. Database & Transactions (ACID)
Data integrity was a priority.
* **Batch Processing:** When uploading 500 students, I use JDBC Batch updates for performance.
* **Transaction Management:** If an allocation fails halfway through, the system performs a `rollback()` to ensure we don't end up with half-filled rooms.
* **Foreign Keys:** Strict constraints ensure an allocation cannot exist without a valid student and room.

#### 4. Custom REST API
Since I didn't use Spring Web, I built a custom routing system using Java's `com.sun.net.httpserver`. I manually handle JSON parsing, CORS headers, and HTTP methods (GET/POST).

---

###  Future Improvements
* Add authentication tokens (JWT) for better security.
* Implement "Simulated Annealing" for even better optimization results.
* Add PDF export for the final seating plan.

---
