package xyz.rawmanoj.mrbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;

import java.util.List;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_number", columnList = "accountNumber")
})
@Setter
@Getter
public class Account extends BaseEntity {

    @Column(unique = true, nullable = false)
    private Integer accountNumber;

    @Column(nullable = false)
    private Double balance = 0.0;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany
    private List<KycDocument> kycDocuments;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus = UserStatus.ACTIVE;
}
