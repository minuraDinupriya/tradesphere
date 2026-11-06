package com.tradesphere.catalog;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public catalog operation used by a surrounding inventory transaction. */
@Service
public class CatalogProductAccess {
    private final NamedParameterJdbcTemplate jdbc;
    public CatalogProductAccess(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveStatus(UUID organizationId, UUID productId) {
        return jdbc.query("SELECT active FROM tradesphere.products WHERE organization_id = :organizationId AND id = :id FOR SHARE",
                Map.of("organizationId", organizationId, "id", productId), (rs, row) -> rs.getBoolean("active"))
                .stream().findFirst().orElseThrow(ProductNotFoundException::new);
    }
}
