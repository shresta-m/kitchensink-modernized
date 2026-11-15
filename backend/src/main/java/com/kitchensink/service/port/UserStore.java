package com.kitchensink.service.port;



import com.kitchensink.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for accounts, owned by the service layer.
 *
 * @throws com.kitchensink.exception.domain.DuplicateUsernameException on {@link #insert} when the username is taken
 */
public interface UserStore {

    User insert(User user);

    Optional<User> findByUsername(String username);

    List<User> findAllOrderedByUsername();
}
