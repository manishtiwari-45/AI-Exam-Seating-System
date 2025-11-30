package com.exam.service;

import com.exam.dao.AllocationDAO;
import com.exam.dao.RoomDAO;
import com.exam.dao.StudentDAO;
import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;
import com.exam.strategy.SeatingStrategy;

import java.util.ArrayList;
import java.util.List;

public class AllocationService {

    private StudentDAO studentDAO = new StudentDAO();
    private RoomDAO roomDAO = new RoomDAO();
    private AllocationDAO allocationDAO = new AllocationDAO();

    public void generateSeating(long examId, long roomId, SeatingStrategy strategy) {
        System.out.println("--- Service: Generating Seating for Exam " + examId + " ---");

        // 1. Fetch Data
        List<Student> students = fetchStudentsForExam(examId);
        List<Seat> seats = roomDAO.getSeatsByRoomId(roomId);

        // Validation
        if (students.isEmpty()) {
            System.err.println("Service Error: No students found for this exam. (Did you add them to DB?)");
            return;
        }
        if (seats.isEmpty()) {
            System.err.println("Service Error: No seats found in Room " + roomId + ". (Check your Room ID)");
            return;
        }

        // 2. Execute Strategy (AI or Greedy)
        System.out.println("Running Strategy with " + students.size() + " students and " + seats.size() + " seats...");
        long start = System.currentTimeMillis();
        List<Allocation> allocations = strategy.allocate(examId, students, seats);
        long end = System.currentTimeMillis();

        System.out.println("Strategy executed in " + (end - start) + "ms. Generated " + allocations.size() + " seats.");

        if (allocations.isEmpty()) {
            System.err.println("Service Error: Strategy returned 0 allocations.");
            return;
        }

        // 3. Save to Database
        boolean success = allocationDAO.bulkSaveAllocations(allocations);

        if (success) {
            System.out.println("SUCCESS: Seating plan generated and saved to Database.");
        } else {
            System.err.println("FAILURE: Database save failed.");
        }
    }

    // Temporary helper to get our test data
    private List<Student> fetchStudentsForExam(long examId) {
        List<Student> list = new ArrayList<>();
        // Try to fetch the students we created in Phase 7
        Student s1 = studentDAO.getStudentByRollNo("CS101");
        Student s2 = studentDAO.getStudentByRollNo("CS102");
        Student s3 = studentDAO.getStudentByRollNo("EC101");

        if (s1 != null) list.add(s1);
        if (s2 != null) list.add(s2);
        if (s3 != null) list.add(s3);

        return list;
    }
}