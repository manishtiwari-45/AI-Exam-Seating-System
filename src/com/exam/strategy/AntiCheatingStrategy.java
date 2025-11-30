package com.exam.strategy;

import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;
import java.util.ArrayList;
import java.util.List;

public class AntiCheatingStrategy implements SeatingStrategy {

    @Override
    public List<Allocation> allocate(long examId, List<Student> students, List<Seat> seats) {
        List<Allocation> allocations = new ArrayList<>();
        System.out.println("--- Running Anti-Cheating Strategy ---");

        for (Student student : students) {
            boolean assigned = false;

            for (Seat seat : seats) {
                if (isSeatOccupied(seat, allocations)) continue;

                if (isSafe(seat, student, allocations, students, seats)) {
                    allocations.add(new Allocation(examId, student.getId(), seat.getId()));
                    System.out.println("Safe: " + student.getName() + " (" + student.getBranch() + ") -> Seat " + seat.getSeatNumber());
                    assigned = true;
                    break;
                }
            }
            if (!assigned) System.err.println("Warning: No safe seat for " + student.getName());
        }
        return allocations;
    }

    private boolean isSeatOccupied(Seat seat, List<Allocation> allocations) {
        for (Allocation a : allocations) {
            if (a.getSeatId() == seat.getId()) return true;
        }
        return false;
    }

    private boolean isSafe(Seat targetSeat, Student currentStudent, List<Allocation> existingAllocations, List<Student> allStudents, List<Seat> allSeats) {
        for (Allocation alloc : existingAllocations) {
            // 1. Get the Neighbor's Seat and Student details
            Seat neighborSeat = getSeatById(alloc.getSeatId(), allSeats);
            Student neighborStudent = getStudentById(alloc.getStudentId(), allStudents);

            if (neighborSeat == null || neighborStudent == null) continue;

            // 2. Check if they are physically adjacent
            if (isAdjacent(targetSeat, neighborSeat)) {
                // 3. Check if they are from the same branch
                if (neighborStudent.getBranch().equals(currentStudent.getBranch())) {
                    // CONFLICT FOUND: Adjacent AND Same Branch
                    return false;
                }
            }
        }
        return true; // No conflicts found
    }

    private boolean isAdjacent(Seat s1, Seat s2) {
        int rowDiff = Math.abs(s1.getRow() - s2.getRow());
        int colDiff = Math.abs(s1.getCol() - s2.getCol());

        // Adjacent if row diff <= 1 AND col diff <= 1
        // (excluding the case where they are the same seat, though isSeatOccupied handles that)
        return rowDiff <= 1 && colDiff <= 1;
    }

    private Seat getSeatById(long id, List<Seat> seats) {
        for (Seat s : seats) if (s.getId() == id) return s;
        return null;
    }

    private Student getStudentById(long id, List<Student> students) {
        for (Student s : students) if (s.getId() == id) return s;
        return null;
    }
}