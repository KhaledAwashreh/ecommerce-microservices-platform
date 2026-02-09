package infastructure.http.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProductDto {

    private UUID id;

    private UUID ownerId;

    private String name;

    private String description;

    private List<CategoryDto> categories;

    private Instant createdAt;

    private Instant updatedAt;

    private String thumbnailUrl;
}