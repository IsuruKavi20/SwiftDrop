package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.swiftdrop.backend.identity.AppUser;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's delivery profile: the name, email and phone they gave at registration, and their saved addresses.
 * The login account itself (photo, roles, status) is read through {@link AppUser}.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    /** The login account (auth service). Its id is the "sub" claim of the customer's access token. */
    @OneToOne(optional = false)
    @JoinColumn(name = "auth_user_id", nullable = false, unique = true, updatable = false)
    private AppUser user;

    /** Nullable in the database only because customers registered before these columns existed have no value yet. */
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 150)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerAddress> addresses = new ArrayList<>();

    protected Customer() {
        // for JPA
    }

    public Customer(AppUser user, String phoneNumber) {
        this.user = user;
        this.phoneNumber = phoneNumber;
    }

    public Customer(AppUser user, String firstName, String lastName, String email, String phoneNumber) {
        this(user, phoneNumber);
        setDetails(firstName, lastName, email);
    }

    public void setDetails(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public void addAddress(CustomerAddress address) {
        address.setCustomer(this);
        addresses.add(address);
    }

    public void removeAddress(CustomerAddress address) {
        addresses.remove(address);
        address.setCustomer(null);
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CustomerAddress> getAddresses() { return addresses; }
}
