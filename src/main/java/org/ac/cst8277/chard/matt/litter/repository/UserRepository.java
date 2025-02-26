package org.ac.cst8277.chard.matt.litter.repository;


import org.ac.cst8277.chard.matt.litter.model.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for User entities.
 * Provides methods for CRUD operations on User documents in MongoDB.
 * Extends Spring's ReactiveMongoRepository for hands-off reactive MongoDB operations.
 * Spring's @Repository annotation indicates that it's a repository.
 */
@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    /**
     * Method to find a User by username.
     *
     * @param username username of the User to find.
     * @return Mono emitting the User if found, else Mono.empty().
     */
    Mono<User> findByUsername(String username);

    /**
     * Method to save a User entity.
     *
     * @param entity the User entity to save.
     * @return Mono of the saved User.
     */
    @NonNull
    <S extends User> Mono<S> save(@NonNull S entity);

    /**
     * Method to find all Users with a role.
     *
     * @param role the role to search for.
     * @return Flux emitting all Users with the role.
     */
    Flux<User> findByRolesContains(String role);
}
