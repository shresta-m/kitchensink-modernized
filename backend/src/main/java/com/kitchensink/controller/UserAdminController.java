package com.kitchensink.controller;



import com.kitchensink.controller.contract.UserAdminControllerInterface;
import com.kitchensink.dto.user.CreateUserRequest;
import com.kitchensink.dto.user.UserResponse;
import com.kitchensink.service.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserAdminController implements UserAdminControllerInterface {

    private final UserService users;

    public UserAdminController(UserService users) {
        this.users = users;
    }

    @Override
    public ResponseEntity<UserResponse> create(CreateUserRequest body) {
        UserResponse created = UserResponse.from(users.create(body.username(), body.password(), body.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::from).toList();
    }
}
