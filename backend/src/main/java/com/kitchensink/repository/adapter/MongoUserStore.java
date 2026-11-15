package com.kitchensink.repository.adapter;



import com.kitchensink.exception.domain.DuplicateUsernameException;
import com.kitchensink.model.User;
import com.kitchensink.repository.UserRepository;
import com.kitchensink.service.port.UserStore;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapter between the service-owned {@link UserStore} port and Spring Data MongoDB. */
@Repository
public class MongoUserStore implements UserStore {

    private final UserRepository repository;

    public MongoUserStore(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User insert(User user) {
        try {
            return repository.insert(user);
        } catch (DuplicateKeyException e) {
            throw new DuplicateUsernameException(e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    @Override
    public List<User> findAllOrderedByUsername() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "username"));
    }
}
