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
import com.swiftdrop.backend.identity.entity.AppUser;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's delivery profile: the full name they entered, phone and saved addresses. Photo and login email
 * belong to the auth service and are read through {@link AppUser}.
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

    /** Entered by the customer when they create their profile, e.g. "Nimal Perera". Empty only for old rows. */
    @Column(name = "full_name", length = 150)
    private String fullName;

    /** The login email, copied from the access token (the auth service owns it). Empty until first copied. */
    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

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

    public Customer(AppUser user, String fullName, String phoneNumber) {
        this.user = user;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }

    /** Uses the login account's name as the full name (if it has one). */
    public Customer(AppUser user, String phoneNumber) {
        this(user, user.getName().orElse(null), phoneNumber);
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
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CustomerAddress> getAddresses() { return addresses; }
}
