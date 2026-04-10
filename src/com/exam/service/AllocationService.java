package com.exam.service;

import com.exam.config.DBConnection;
import com.exam.dao.AllocationDAO;
import com.exam.dao.RoomDAO;
import com.exam.model.Allocation;
import com.exam.model.Room;
import com.exam.model.Seat;
import com.exam.model.Student;
import com.exam.strategy.SeatingStrategy;

import java.sql.*;
import java.util.*;

/**
 * AllocationService — orchestrates the full seating allocation pipeline.
 *
 * WHAT CHANGED FROM THE ORIGINAL (and why):
 *
 * BUG 1 — fetchStudentsForExam() was completely broken:
 *   Original code hardcoded roll numbers CS101/CS102/EC101 using
 *   studentDAO.getStudentByRollNo(). This ignored the exam_students table
 *   entirely. Every allocation via the web UI always used these 3 test
 *   students, regardless of what was registered through the UI.
 *   FIX: Query the database properly using exam_students JOIN students.
 *
 * BUG 2 — Only allocated into one room:
 *   Original AllocationHandler did: SELECT id FROM rooms LIMIT 1
 *   and passed that single roomId to generateSeating().
 *   FIX: Fetch ALL rooms, distribute students across them by capacity.
 *
 * ARCHITECTURE — Multi-room distribution logic:
 *   1. Fetch all students registered for examId (from exam_students table)
 *   2. Shuffle students randomly (gives the Hill Climber a varied starting point)
 *   3. Fetch all rooms with their seats
 *   4. For each room: take a slice of students up to room capacity
 *   5. Run the strategy on that slice against that room's seats
 *   6. Collect all allocations, save in one DB transaction
 *
 * This means if you have 60 students and 2 rooms of 35 seats each:
 *   Room A gets students 1–35, Room B gets students 36–60.
 *   Each room runs Hill Climbing independently.
 */
public class AllocationService {

    private final RoomDAO      roomDAO      = new RoomDAO();
    private final AllocationDAO allocationDAO = new AllocationDAO();

    /**
     * Main entry point called by AllocationHandler.
     *
     * @param examId   The exam to allocate (always 99 in the web app)
     * @param strategy The seating algorithm to use (HillClimbingStrategy)
     */
    public void generateSeating(long examId, SeatingStrategy strategy) {
        System.out.println("[AllocationService] Starting allocation for exam " + examId);

        // ── Step 1: Fetch all registered students from the database ──────────
        List<Student> students = fetchStudentsForExam(examId);
        if (students.isEmpty()) {
            System.err.println("[AllocationService] No students found for exam " + examId +
                    ". Register students first via the UI.");
            return;
        }
        System.out.println("[AllocationService] Students to seat: " + students.size());

        // Shuffle so Hill Climbing gets a different random starting point each run
        Collections.shuffle(students);

        // ── Step 2: Fetch all rooms ───────────────────────────────────────────
        List<Room> rooms = roomDAO.getAllRooms();
        if (rooms.isEmpty()) {
            System.err.println("[AllocationService] No rooms found. Add rooms first via the UI.");
            return;
        }

        // ── Step 3: Check total capacity ─────────────────────────────────────
        int totalCapacity = rooms.stream().mapToInt(Room::getCapacity).sum();
        if (totalCapacity < students.size()) {
            System.err.printf("[AllocationService] WARNING: Only %d seats for %d students. " +
                            "%d students will not be seated.%n",
                    totalCapacity, students.size(), students.size() - totalCapacity);
        }

        // ── Step 4: Distribute students across rooms and run strategy ─────────
        List<Allocation> allAllocations = new ArrayList<>();
        int studentOffset = 0;  // Tracks how far into the students list we are

        for (Room room : rooms) {
            if (studentOffset >= students.size()) break;  // All students seated

            List<Seat> seats = roomDAO.getSeatsByRoomId(room.getId());
            if (seats.isEmpty()) {
                System.err.println("[AllocationService] Room " + room.getRoomNumber() +
                        " has no seats in DB. Skipping.");
                continue;
            }

            // Take a slice of students for this room (up to its capacity)
            int sliceEnd = Math.min(studentOffset + seats.size(), students.size());
            List<Student> studentsForRoom = students.subList(studentOffset, sliceEnd);

            System.out.printf("[AllocationService] Room %-10s | %d seats | %d students assigned%n",
                    room.getRoomNumber(), seats.size(), studentsForRoom.size());

            // Run the algorithm for this room
            long start = System.currentTimeMillis();
            List<Allocation> roomAllocations = strategy.allocate(examId, studentsForRoom, seats);
            long elapsed = System.currentTimeMillis() - start;

            System.out.printf("[AllocationService] Room %-10s | Strategy completed in %dms | %d allocations%n",
                    room.getRoomNumber(), elapsed, roomAllocations.size());

            allAllocations.addAll(roomAllocations);
            studentOffset = sliceEnd;
        }

        if (allAllocations.isEmpty()) {
            System.err.println("[AllocationService] Strategy returned 0 allocations. Nothing saved.");
            return;
        }

        // ── Step 5: Save all allocations in one transaction ──────────────────
        boolean saved = allocationDAO.replaceAllocations(examId, allAllocations);

        if (saved) {
            System.out.println("[AllocationService] SUCCESS: " + allAllocations.size() +
                    " seats allocated across " + rooms.size() + " room(s).");
        } else {
            System.err.println("[AllocationService] FAILED: Could not save allocations to database.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG FIX: fetchStudentsForExam
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches all students registered for a given exam.
     *
     * Uses the exam_students junction table which is populated by
     * BatchStudentHandler when students are added via the web UI.
     *
     * ORIGINAL BUG: This method ignored the examId parameter entirely and
     * called studentDAO.getStudentByRollNo() with hardcoded roll numbers.
     * That made every allocation identical regardless of UI input.
     */
    private List<Student> fetchStudentsForExam(long examId) {
        List<Student> students = new ArrayList<>();

        // JOIN exam_students with students to get only students in THIS exam
        String sql = """
                SELECT s.id, s.roll_no, s.name, s.email, s.branch
                FROM students s
                JOIN exam_students es ON s.id = es.student_id
                WHERE es.exam_id = ?
                ORDER BY s.branch, s.roll_no
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, examId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                students.add(new Student(
                        rs.getLong("id"),
                        rs.getString("roll_no"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("branch")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[AllocationService] DB error fetching students: " + e.getMessage());
            e.printStackTrace();
        }

        return students;
    }
}