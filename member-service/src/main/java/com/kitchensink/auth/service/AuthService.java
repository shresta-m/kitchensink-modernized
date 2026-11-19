package com.kitchensink.auth.service;

import com.kitchensink.auth.dto.ChangePasswordCommand;
import com.kitchensink.auth.dto.LoginCommand;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.auth.dto.RegisterCommand;
import com.kitchensink.member.model.Member;

public interface AuthService {

    Member register(RegisterCommand command);

    /** Checks the credentials and issues an access token. Wrong email and wrong password look the same. */
    LoginResult login(LoginCommand command);

    /** Signs the member out everywhere: every token issued so far stops working at once. */
    void logout(String memberId);

    /** The signed-in member. */
    Member me(String memberId);

    /** Replaces the password after checking the current one. Clears the temporary-password flag and signs the member out everywhere. */
    void changePassword(String memberId, ChangePasswordCommand command);
}
