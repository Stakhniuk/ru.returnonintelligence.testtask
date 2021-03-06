package ru.returnonintelligence.testtask.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.returnonintelligence.testtask.model.User;
import ru.returnonintelligence.testtask.service.GroupService;
import ru.returnonintelligence.testtask.service.UserService;
import ru.returnonintelligence.testtask.util.CustomErrorType;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


@RestController
public class UserController {

    protected Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserService userService;
    @Autowired
    private GroupService groupService;




    //-------------------Retrieve All Users--------------------------------------------------------

    @RequestMapping( method = RequestMethod.GET, value= "/user/all")
    public ResponseEntity<List<User>> getUsers() {
        LOGGER.debug("Received request to get all User");
        List<User> users = userService.getAll();
        if (users.isEmpty()) {
            return new ResponseEntity(
                    new CustomErrorType("Bad request, NO_CONTENT"),
                    HttpStatus.NO_CONTENT);
            // many decide to return HttpStatus.NOT_FOUND
        }
        return new ResponseEntity<List<User>>(users, HttpStatus.OK);
    }
    //http://localhost:1234/user?username=adm&birthday=2005-09-20
    @RequestMapping( method = RequestMethod.GET, value= "/user")
    public ResponseEntity<List<User>> getUsers(
            @RequestParam(name = "username", required=false) String username,
            @RequestParam(name = "birthday", required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthday,
            @RequestParam(name = "email", required=false) String email,
            @RequestParam(name = "reactive", required=false) Boolean reActive
            ) {
        if (username!=null){
            LOGGER.debug("Received request to get User with username"+ username);
            List<User> users = userService.getByUsernameContaining(username);
            if (users.isEmpty()) {
                return new ResponseEntity(new CustomErrorType(" User with username"+ username +"NOT_FOUND"),HttpStatus.NOT_FOUND);
                // many decide to return HttpStatus.NOT_FOUND
            }
            if (reActive!=null){
                users.forEach((user -> {
                    userService.reActivateUserByUsername(user.getUsername(),reActive);
                }));
            }
            return new ResponseEntity<List<User>>(users, HttpStatus.OK);
        }
        if (birthday!=null){
            LOGGER.debug("Fetching User with birthday " + birthday);
            List<User> users = userService.getAllByBirthday(birthday);
            if (users.isEmpty()) {
                return new ResponseEntity(new CustomErrorType("User with birthday "+ birthday+" NOT_FOUND"),HttpStatus.NOT_FOUND);
                // many decide to return HttpStatus.NOT_FOUND
            }
            return new ResponseEntity<List<User>>(users, HttpStatus.OK);
        }
        if (email!=null) {
            LOGGER.debug("Fetching User with email " + email);
            Optional<User> user = userService.getByEmail(email);
            if (!user.isPresent()) {
                return new ResponseEntity(new CustomErrorType("User with email " + email + " NOT_FOUND"), HttpStatus.NOT_FOUND);
                // many decide to return HttpStatus.NOT_FOUND
            }
            return new ResponseEntity<List<User>>(Arrays.asList(user.get()), HttpStatus.OK);
        }
        return new ResponseEntity(new CustomErrorType("Bad RequestParams"),HttpStatus.NOT_FOUND);
    }
    /*
     *  We are not using userService.findByUsername here(we could),
     *  so it is good that we are making sure that the user has role "ROLE_USER"
     *  to access this endpoint.
     */
    @RequestMapping("/whoami")
    @PreAuthorize("hasRole('USER')")
    public User user() {
        return (User)SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }


    //-------------------Retrieve Single User--------------------------------------------------------

    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> getUser(
            @PathVariable("id") long id) {
        LOGGER.debug("Fetching User with id " + id);
        Optional<User> user = userService.getById(id);
        if (!user.isPresent()) {
            LOGGER.debug("User with id " + id + " not found");
            return new ResponseEntity<User>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<User>(user.get(), HttpStatus.OK);
    }



    //-------------------Create a User--------------------------------------------------------

    @RequestMapping(value = "/user/", method = RequestMethod.POST)
    public ResponseEntity<Void> createUser(@RequestBody User user, UriComponentsBuilder ucBuilder) {
        LOGGER.debug("Creating User " + user.getUsername());

        if (userService.isUserExist(user)) {
            LOGGER.debug("A User with name " + user.getUsername() + " already exist");
            return new ResponseEntity<Void>(HttpStatus.CONFLICT);
        }

        userService.saveUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/user/{id}").buildAndExpand(user.getId()).toUri());
        return new ResponseEntity<Void>(headers, HttpStatus.CREATED);
    }


    //------------------- Update a User --------------------------------------------------------

    @RequestMapping(value = "/user/{id}", method = RequestMethod.PUT)
    public ResponseEntity<User> updateUser(@PathVariable("id") long id, @RequestBody User user) {
        LOGGER.debug("Updating User " + id);

        Optional<User> currentUserO = userService.getById(id);

        if (!currentUserO.isPresent()) {
            LOGGER.debug("User with id " + id + " not found");
            return new ResponseEntity<User>(HttpStatus.NOT_FOUND);
        }
        User currentUser = currentUserO.get();

        currentUser.setAddress(user.getAddress());

        userService.updateUser(currentUser);
        return new ResponseEntity<User>(currentUser, HttpStatus.OK);
    }

    //------------------- Delete a User --------------------------------------------------------

    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<User> deleteUser(@PathVariable("id") long id) {
        LOGGER.debug("Fetching & Deleting User with id " + id);

        Optional<User> user = userService.getById(id);
        if (!user.isPresent()) {
            LOGGER.debug("Unable to delete. User with id " + id + " not found");
            return new ResponseEntity<User>(HttpStatus.NOT_FOUND);
        }
        if (groupService.countAdmins()<=1) {
            final Boolean[] isAdmin = new Boolean[1];
            user.get().getAuthorities().forEach((authority)->{
                isAdmin[0] = authority.getAuthority().equals("ROLE_ADMIN");
            });
            if (isAdmin[0]) {
                LOGGER.debug("Unable to delete. User count <=1");
                return new ResponseEntity<User>(HttpStatus.NOT_FOUND);
            }
        }

        userService.deleteUserById(id);
        return new ResponseEntity<User>(HttpStatus.NO_CONTENT);
    }

}
