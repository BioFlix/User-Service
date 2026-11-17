package bioflix.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auth0_sub", nullable = false, unique = true, length = 128)
    private String auth0Sub;

    // Unique (case-insensitive) among active users: see users_email_active in schema.sql
    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    @Column(name = "sms_opt_in", nullable = false)
    private boolean smsOptIn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // for JPA
    }

    public User(String auth0Sub, String email, String fullName) {
        this.auth0Sub = auth0Sub;
        this.email = email;
        this.fullName = fullName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /* Soft delete: the row stays (old bookings point at it), the personal data is anonymised. */
    public void markDeleted() {
        email = "deleted-" + id + "@deleted.invalid";
        fullName = "Deleted user";
        phone = null;
        smsOptIn = false;
        deletedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    public UUID getId() { return id; }
    public String getAuth0Sub() { return auth0Sub; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isSmsOptIn() { return smsOptIn; }
    public void setSmsOptIn(boolean smsOptIn) { this.smsOptIn = smsOptIn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
