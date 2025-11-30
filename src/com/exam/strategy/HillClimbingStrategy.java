package com.exam.strategy;

import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HillClimbingStrategy implements SeatingStrategy {

    private static final int MAX_ITERATIONS = 5000; // How many times we try to swap

    @Override
    public List<Allocation> allocate(long examId, List<Student> students, List<Seat> seats) {
        System.out.println("--- Running AI Hill Climbing Strategy ---");

        // 1. INITIAL STATE: Randomly assign students to seats
        // We act as if we are just shuffling the students into the available seats
        List<Student> currentPlacement = new ArrayList<>(students);

        // If there are more seats than students, we need to handle "Empty" seats.
        // For simplicity in this tutorial, we assume we just shuffle students into the first N seats.
        // (A robust system would handle empty slots as nulls in the list)
        Collections.shuffle(currentPlacement);

        // 2. Calculate Initial Cost
        int currentCost = calculateTotalRisk(currentPlacement, seats);
        System.out.println("Initial Risk Score: " + currentCost);

        Random random = new Random();

        // 3. OPTIMIZATION LOOP
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // If cost is 0, we are perfect. Stop.
            if (currentCost == 0) break;

            // A. Make a "Move": Pick two random students and swap their seats
            int idx1 = random.nextInt(currentPlacement.size());
            int idx2 = random.nextInt(currentPlacement.size());

            // Swap in memory
            Collections.swap(currentPlacement, idx1, idx2);

            // B. Calculate New Cost
            int newCost = calculateTotalRisk(currentPlacement, seats);

            // C. Decision: Better or Worse?
            if (newCost < currentCost) {
                // Improvement! Keep the change.
                currentCost = newCost;
                // System.out.println("Iteration " + i + ": Improved Score to " + currentCost);
            } else {
                // No improvement (or worse). Revert the swap.
                // Swapping again restores the original order.
                Collections.swap(currentPlacement, idx1, idx2);
            }
        }

        System.out.println("Final Risk Score: " + currentCost);

        // 4. Convert the final optimization into Allocations
        List<Allocation> finalAllocations = new ArrayList<>();
        for (int i = 0; i < currentPlacement.size(); i++) {
            // Student at index i gets Seat at index i
            if (i < seats.size()) {
                finalAllocations.add(new Allocation(examId, currentPlacement.get(i).getId(), seats.get(i).getId()));
            }
        }
        return finalAllocations;
    }

    // --- HELPER: The Cost Function ---
    // Calculates how "bad" the current arrangement is.
    private int calculateTotalRisk(List<Student> students, List<Seat> seats) {
        int score = 0;

        // Compare every student with every other student
        // (O(N^2) - slow for huge datasets, but fine for <1000 students)
        for (int i = 0; i < students.size(); i++) {
            for (int j = i + 1; j < students.size(); j++) {

                // Ensure we don't go out of bounds of available seats
                if (i >= seats.size() || j >= seats.size()) continue;

                Student s1 = students.get(i);
                Student s2 = students.get(j);
                Seat seat1 = seats.get(i);
                Seat seat2 = seats.get(j);

                // Check physical adjacency
                if (isAdjacent(seat1, seat2)) {
                    // Check logic adjacency (Same Branch)
                    if (s1.getBranch().equals(s2.getBranch())) {
                        score += 100; // Penalty for cheating risk
                    }
                }
            }
        }
        return score;
    }

    private boolean isAdjacent(Seat s1, Seat s2) {
        int rowDiff = Math.abs(s1.getRow() - s2.getRow());
        int colDiff = Math.abs(s1.getCol() - s2.getCol());
        return rowDiff <= 1 && colDiff <= 1;
    }
}