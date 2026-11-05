package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An address a customer saves to reuse. When booking, its values are COPIED into a {@link DeliveryAddress},
 * so editing or deleting a saved address never changes past deliveries.
 */
@Entity
@Table(name = "customer_address")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "postal_code", length = 15)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected CustomerAddress() {
        // for JPA
    }

    public CustomerAddress(String label, String street, String city, String district, String postalCode) {
        this.label = label;
        this.street = street;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    void setCustomer(Customer customer) { this.customer = customer; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
