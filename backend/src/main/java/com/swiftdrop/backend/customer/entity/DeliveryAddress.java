package com.swiftdrop.backend.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The pickup (sender) or destination (receiver) details of one delivery, exactly as entered at booking.
 * A delivery has at most one of each type.
 */
@Entity
@Table(name = "delivery_address",
        uniqueConstraints = @UniqueConstraint(name = "uq_delivery_address_type", columnNames = {"delivery_id", "address_type"}))
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_address_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "postal_code", length = 15)
    private String postalCode;

    protected DeliveryAddress() {
        // for JPA
    }

    public DeliveryAddress(AddressType addressType, String contactName, String contactPhone,
                           String street, String city, String district, String postalCode) {
        this.addressType = addressType;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.street = street;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
    }

    /** Copies a saved address into a booking; later edits to the saved address do not affect it. */
    public static DeliveryAddress from(CustomerAddress saved, AddressType addressType, String contactName, String contactPhone) {
        return new DeliveryAddress(addressType, contactName, contactPhone,
                saved.getStreet(), saved.getCity(), saved.getDistrict(), saved.getPostalCode());
    }

    public Long getId() { return id; }
    public Delivery getDelivery() { return delivery; }
    void setDelivery(Delivery delivery) { this.delivery = delivery; }
    public AddressType getAddressType() { return addressType; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getPostalCode() { return postalCode; }
}
