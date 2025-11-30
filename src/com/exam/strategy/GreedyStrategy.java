package com.exam.strategy;

import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;
import java.util.ArrayList;
import java.util.List;

public class GreedyStrategy implements SeatingStrategy {

    @Override
    public List<Allocation> allocate(long examId, List<Student> students, List<Seat> seats) {
        List<Allocation> allocations = new ArrayList<>();

        int studentIndex = 0;
        int seatIndex = 0;

        System.out.println("--- Running Greedy Strategy ---");

        // Loop while we have both students and seats available
        while (studentIndex < students.size() && seatIndex < seats.size()) {
            Student student = students.get(studentIndex);
            Seat seat = seats.get(seatIndex);

            // Create the pair
            allocations.add(new Allocation(examId, student.getId(), seat.getId()));

            System.out.println("Assigned Student " + student.getName() + " to Seat " + seat.getSeatNumber());

            studentIndex++;
            seatIndex++;
        }

        return allocations;
    }
}