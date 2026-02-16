package infrastructure.http.client;

import infrastructure.http.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service", url = "/api/v1/product")
public interface ProductServiceClient {

    @GetMapping("/{productId}")
    ProductDto retrieveProduct(@PathVariable UUID productId);
}
