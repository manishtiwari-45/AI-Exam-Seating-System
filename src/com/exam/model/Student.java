package com.exam.model;

public class Student {
    private long id;
    private String rollNo;
    private String name;
    private String email;
    private String branch;

    public Student(long id, String rollNo, String name, String email, String branch) {
        this.id = id;
        this.rollNo = rollNo;
        this.name = name;
        this.email = email;
        this.branch = branch;
    }

    // Getters
    public long getId() { return id; }
    public String getRollNo() { return rollNo; }
    public String getName() { return name; }
    public String getBranch() { return branch; } // Crucial for Anti-Cheating strategy

    @Override
    public String toString() {
        return "Student[" + rollNo + " - " + branch + "]";
    }
}