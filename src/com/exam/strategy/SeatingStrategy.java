package com.exam.strategy;

import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;
import java.util.List;

public interface SeatingStrategy {
    // The Input: An Exam ID, a list of Students, a list of Empty Seats
    // The Output: A list of Allocations
    List<Allocation> allocate(long examId, List<Student> students, List<Seat> seats);
}