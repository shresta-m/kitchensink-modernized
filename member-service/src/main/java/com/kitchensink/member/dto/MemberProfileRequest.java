package com.kitchensink.member.dto;

import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.validation.ValidCountryCode;
import com.kitchensink.member.validation.ValidEmail;
import com.kitchensink.member.validation.ValidName;
import com.kitchensink.member.validation.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Profile fields shared by register, admin create and update, so the rules cannot drift apart. */
@Getter
@Setter
@NoArgsConstructor
@ValidPhone
public abstract class MemberProfileRequest {

    @Schema(example = "Asha Rao")
    @ValidName
    private String name;

    @Schema(example = "asha@example.com")
    @ValidEmail
    private String email;

    @Schema(example = "+91")
    @ValidCountryCode
    private String countryCode;

    @Schema(example = "9825041763", description = "Digits only, without the country code")
    @NotBlank(message = "Enter the phone number")
    private String phone;

    public MemberDetails toDetails() {
        return new MemberDetails(name, email, countryCode, phone);
    }
}
