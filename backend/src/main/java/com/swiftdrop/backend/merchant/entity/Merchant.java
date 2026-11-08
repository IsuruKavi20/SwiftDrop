package com.swiftdrop.backend.merchant.entity;

import com.swiftdrop.backend.identity.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A merchant's business profile in SwiftDrop. Who the owner is (name, roles, active) comes from {@link AppUser};
 * approval and the legal business name stay in the auth service. {@code businessName} here is the trading name
 * shown in SwiftDrop. Created empty on the merchant's first visit, then completed by the merchant.
 */
@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_id")
    private Long id;

    /** The login account that owns this business. */
    @OneToOne(optional = false)
    @JoinColumn(name = "auth_user_id", nullable = false, unique = true, updatable = false)
    private AppUser owner;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "business_email", length = 150)
    private String businessEmail;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {
        // Required by JPA
    }

    /** An empty profile for a merchant's first visit; filled in with {@link #updateDetails}. */
    public Merchant(AppUser owner) {
        this.owner = owner;
    }

    public void updateDetails(String businessName, String businessEmail, String contactNumber, String addressLine1,
                              String addressLine2, String city, String province, String postalCode, String country) {
        this.businessName = businessName;
        this.businessEmail = businessEmail;
        this.contactNumber = contactNumber;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
        this.country = country;
    }

    /** True once the required business details are filled in (the frontend asks for them until then). */
    public boolean isComplete() {
        return businessName != null && businessEmail != null && contactNumber != null && addressLine1 != null
                && city != null && country != null;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getBusinessName() { return businessName; }
    public String getBusinessEmail() { return businessEmail; }
    public String getContactNumber() { return contactNumber; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getProvince() { return province; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
