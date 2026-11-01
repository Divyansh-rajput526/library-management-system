package com.library;

import java.time.LocalDate;

public class Member {

    private int id;
    private String name;
    private String email;
    private String phone;
    private LocalDate joinedOn;

    public Member(int id, String name, String email, String phone, LocalDate joinedOn) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.joinedOn = joinedOn;
    }

    public Member(String name, String email, String phone) {
        this(0, name, email, phone, null);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getJoinedOn() { return joinedOn; }

    @Override
    public String toString() {
        String phoneDisplay = (phone != null && !phone.isBlank()) ? phone : "N/A";
        String dateDisplay = (joinedOn != null) ? joinedOn.toString() : "N/A";
        return String.format("[%d] %s  |  Email: %s  |  Phone: %s  |  Joined: %s",
                id, name, email, phoneDisplay, dateDisplay);
    }
}
