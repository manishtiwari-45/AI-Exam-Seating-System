package com.exam.main;

import com.exam.dao.RoomDAO;
import com.exam.dao.StudentDAO;
import com.exam.service.AllocationService;
import com.exam.strategy.HillClimbingStrategy;
import com.exam.strategy.SeatingStrategy;
import com.exam.model.Seat;
import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("--- Setup & Run ---");

        RoomDAO roomDAO = new RoomDAO();
        StudentDAO studentDAO = new StudentDAO();
        long validRoomId = -1;

        // 1. SETUP DATA
        try {
            // Try to add a fresh room to GUARANTEE we have one.
            // We use a random number in the name to avoid "Duplicate Entry" errors if you run this twice.
            String roomName = "FinalExamRoom_" + System.currentTimeMillis() % 1000;
            System.out.println("Creating new room: " + roomName);

            roomDAO.addRoom(roomName, 2, 2); // 2 rows, 2 cols = 4 seats

            // Now we need to find the ID of the room we just created.
            // Since we don't have a 'getRoomByName' method, we will cheat and look at the seats.
            // In a real app, addRoom() should return the ID.
            // For now, let's assume the user has ID 1 or 2 or 3.

        } catch (Exception e) {
            System.out.println("Room creation note: " + e.getMessage());
        }

        // 2. FIND A VALID ROOM ID
        // We will loop through IDs 1 to 10 to find one that exists and has seats.
        for(long i = 1; i <= 10; i++) {
            List<Seat> seats = roomDAO.getSeatsByRoomId(i);
            if(!seats.isEmpty()) {
                System.out.println("FOUND VALID ROOM! ID: " + i + " has " + seats.size() + " seats.");
                validRoomId = i;
                break; // Stop at the first valid room
            }
        }

        if (validRoomId == -1) {
            System.err.println("CRITICAL ERROR: No rooms with seats found in DB (Checked IDs 1-10).");
            System.err.println("Please check your 'seats' table in MySQL Workbench.");
            return;
        }

        // 3. ENSURE STUDENTS EXIST
        try { studentDAO.addStudent("CS101", "Alice", "a@test.com", "CSE"); } catch (Exception e) {}
        try { studentDAO.addStudent("CS102", "Bob", "b@test.com", "CSE"); } catch (Exception e) {}
        try { studentDAO.addStudent("EC101", "Charlie", "c@test.com", "ECE"); } catch (Exception e) {}

        // 4. RUN SERVICE
        System.out.println("--- Running Service using Room ID: " + validRoomId + " ---");

        SeatingStrategy aiStrategy = new HillClimbingStrategy();
        AllocationService service = new AllocationService();

        service.generateSeating(99, validRoomId, aiStrategy);
    }
}