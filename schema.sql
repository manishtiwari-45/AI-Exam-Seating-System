-- =============================================================
-- Exam Seating Allocation System — Database Schema
-- =============================================================
-- Run this once to set up your database from scratch.
-- Usage: mysql -u root -p < schema.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS exam_seating_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE exam_seating_db;

-- -------------------------------------------------------------
-- 1. courses
--    A course is what the exam is for (e.g., B.Tech, MCA).
--    We seed one default "General" course used by the web app.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS courses (
    id        BIGINT      AUTO_INCREMENT PRIMARY KEY,
    code      VARCHAR(10) NOT NULL UNIQUE,
    name      VARCHAR(100) NOT NULL
);

-- -------------------------------------------------------------
-- 2. exams
--    One row per exam session.
--    The web app always uses exam_id = 99 (the default session).
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exams (
    id             BIGINT      AUTO_INCREMENT PRIMARY KEY,
    course_id      BIGINT      NOT NULL,
    exam_date      DATE        NOT NULL,
    exam_time      TIME        NOT NULL,
    duration_mins  INT         NOT NULL DEFAULT 180,
    FOREIGN KEY (course_id) REFERENCES courses(id)
);

-- -------------------------------------------------------------
-- 3. students
--    roll_no must be unique across all students.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id       BIGINT       AUTO_INCREMENT PRIMARY KEY,
    roll_no  VARCHAR(20)  NOT NULL UNIQUE,
    name     VARCHAR(100) NOT NULL,
    email    VARCHAR(100),
    branch   VARCHAR(50)  NOT NULL  -- e.g. CSE, ECE, ME, CE
);

-- -------------------------------------------------------------
-- 4. exam_students  (junction table)
--    Maps which students appear in which exam.
--    BatchStudentHandler inserts here for exam_id = 99.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_students (
    exam_id    BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (exam_id, student_id),
    FOREIGN KEY (exam_id)    REFERENCES exams(id)    ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);

-- -------------------------------------------------------------
-- 5. rooms
--    Each room has a grid of seats defined by rows_count x cols_count.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id           BIGINT      AUTO_INCREMENT PRIMARY KEY,
    room_number  VARCHAR(20) NOT NULL UNIQUE,
    capacity     INT         NOT NULL,   -- rows_count * cols_count
    rows_count   INT         NOT NULL,
    cols_count   INT         NOT NULL
);

-- -------------------------------------------------------------
-- 6. seats
--    One row per physical seat in a room.
--    row_num and col_num are used by the Hill Climbing algorithm
--    to determine physical adjacency between students.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seats (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id      BIGINT NOT NULL,
    seat_number  INT    NOT NULL,   -- sequential: 1, 2, 3 ...
    row_num      INT    NOT NULL,
    col_num      INT    NOT NULL,
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

-- -------------------------------------------------------------
-- 7. allocations
--    The output of the algorithm: student -> seat mapping.
--    UNIQUE constraints prevent:
--      - Two students sharing a seat in the same exam
--      - One student being allocated two seats in the same exam
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS allocations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id     BIGINT NOT NULL,
    student_id  BIGINT NOT NULL,
    seat_id     BIGINT NOT NULL,
    UNIQUE KEY uq_seat    (exam_id, seat_id),
    UNIQUE KEY uq_student (exam_id, student_id),
    FOREIGN KEY (exam_id)    REFERENCES exams(id)    ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (seat_id)    REFERENCES seats(id)    ON DELETE CASCADE
);

-- =============================================================
-- SEED DATA
-- Default course and exam used by the web application.
-- The app always operates on exam_id = 99.
-- =============================================================
INSERT IGNORE INTO courses (id, code, name)
    VALUES (1, 'GEN', 'General');

INSERT IGNORE INTO exams (id, course_id, exam_date, exam_time, duration_mins)
    VALUES (99, 1, CURDATE(), '09:00:00', 180);