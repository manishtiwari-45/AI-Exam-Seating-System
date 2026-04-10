package com.exam.strategy;

import com.exam.model.Allocation;
import com.exam.model.Seat;
import com.exam.model.Student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * HillClimbingStrategy — optimizes seating to minimize same-branch adjacency.
 *
 * HOW IT WORKS:
 *   1. Start with a random assignment of students to seats
 *   2. Score it: +100 penalty for every adjacent pair from the same branch
 *   3. Pick two random students, swap them
 *   4. If score improved → keep the swap. Otherwise → revert it.
 *   5. Repeat up to MAX_ITERATIONS or until score = 0 (perfect arrangement)
 *
 * TIME COMPLEXITY:
 *   O(MAX_ITERATIONS × N²) where N = number of students.
 *   For N=100: ~50M operations. For N=500: ~1.25B operations (slow).
 *   Acceptable for a demo with ≤ 300 students per room.
 *
 * TRADE-OFF vs SIMULATED ANNEALING:
 *   Hill Climbing can get stuck in local optima (score > 0 but no swap helps).
 *   Simulated Annealing occasionally accepts worse moves to escape local optima.
 *   For an interview, Hill Climbing is the right choice — simpler to explain.
 *
 * BUG FIXED:
 *   Original calculateTotalRisk() accessed seats.get(i) and seats.get(j)
 *   without checking if i or j exceeded seats.size(). When students.size() >
 *   seats.size() (which can happen if a room is overfull), this threw an
 *   IndexOutOfBoundsException silently caught upstream.
 *   FIX: Work only on the first min(students, seats) indices.
 */
public class HillClimbingStrategy implements SeatingStrategy {

    // How many swap attempts before we stop.
    // 5000 gives a good result for rooms up to ~150 seats in < 500ms.
    private static final int MAX_ITERATIONS = 5000;

    @Override
    public List<Allocation> allocate(long examId, List<Student> students, List<Seat> seats) {
        System.out.println("[HillClimbing] Starting. Students: " + students.size() +
                ", Seats: " + seats.size());

        // Guard: can't seat more students than there are seats
        // AllocationService already slices correctly, but this is a safety net.
        int count = Math.min(students.size(), seats.size());
        List<Student> placement = new ArrayList<>(students.subList(0, count));
        List<Seat>    usedSeats = seats.subList(0, count);

        // Step 1: Random initial arrangement
        Collections.shuffle(placement);
        int currentCost = calculateCost(placement, usedSeats);
        System.out.println("[HillClimbing] Initial risk score: " + currentCost);

        Random rng = new Random();

        // Step 2: Optimization loop
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (currentCost == 0) {
                System.out.println("[HillClimbing] Perfect arrangement found at iteration " + i);
                break;
            }

            // Pick two distinct random positions
            int a = rng.nextInt(count);
            int b = rng.nextInt(count);
            if (a == b) continue; // Skip — swapping with self changes nothing

            // Try the swap
            Collections.swap(placement, a, b);
            int newCost = calculateCost(placement, usedSeats);

            if (newCost < currentCost) {
                // Improvement: accept
                currentCost = newCost;
            } else {
                // No improvement: revert
                Collections.swap(placement, a, b);
            }
        }

        System.out.println("[HillClimbing] Final risk score: " + currentCost +
                (currentCost == 0 ? " ✓ (optimal)" : " (local optimum)"));

        // Step 3: Build allocations from final arrangement
        List<Allocation> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new Allocation(examId, placement.get(i).getId(), usedSeats.get(i).getId()));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cost Function
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scores how "bad" the current arrangement is.
     *
     * For every pair of students (i, j) where i < j:
     *   - If their seats are physically adjacent AND they share a branch → +100 penalty
     *
     * A score of 0 means no adjacent same-branch pairs exist (ideal).
     *
     * O(N²) — comparing every student against every other student.
     */
    private int calculateCost(List<Student> students, List<Seat> seats) {
        int score = 0;
        int n = students.size(); // seats.size() is guaranteed >= n here

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (isAdjacent(seats.get(i), seats.get(j))) {
                    if (students.get(i).getBranch().equals(students.get(j).getBranch())) {
                        score += 100;
                    }
                }
            }
        }
        return score;
    }

    /**
     * Two seats are adjacent if they are within 1 row and 1 column of each other.
     * This covers horizontal, vertical, and diagonal neighbours (8-directional).
     *
     * Seat grid example (row, col):
     *   [1,1] [1,2] [1,3]
     *   [2,1] [2,2] [2,3]
     *
     * Seat [1,1] is adjacent to: [1,2], [2,1], [2,2]
     * Seat [1,1] is NOT adjacent to: [1,3], [2,3] (colDiff = 2)
     */
    private boolean isAdjacent(Seat s1, Seat s2) {
        int rowDiff = Math.abs(s1.getRow() - s2.getRow());
        int colDiff = Math.abs(s1.getCol() - s2.getCol());
        return rowDiff <= 1 && colDiff <= 1;
    }
}