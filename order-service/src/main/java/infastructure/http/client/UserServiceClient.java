package infastructure.http.client;

import infastructure.http.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "/api/v1/user")
public interface UserServiceClient {

    @GetMapping("/{userId}")
    UserDto retrieveUser(@PathVariable UUID userId);
}
