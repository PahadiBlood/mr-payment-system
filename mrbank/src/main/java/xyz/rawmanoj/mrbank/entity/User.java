package xyz.rawmanoj.mrbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email")
})
@Setter
@Getter
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;

    private boolean kycComplete = false;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus = UserStatus.ACTIVE;


}
