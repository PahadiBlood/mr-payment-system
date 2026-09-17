package xyz.rawmanoj.mrbank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import xyz.rawmanoj.mrbank.enumeration.DocumentType;
import xyz.rawmanoj.mrbank.enumeration.KycStatus;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;

@Entity
@Table(name = "kyc_documents", indexes = {
        @Index(name = "idx_account_id", columnList = "account_id")
})
@Setter
@Getter
public class KycDocument extends BaseEntity{

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DocumentType documentType;

    @Column(nullable = false, unique = true)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus = UserStatus.ACTIVE;
}
