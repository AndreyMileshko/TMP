package com.tmp.production.application;

import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Preview-only command for Release preparation. Does not carry confirmed actual usage or cell
 * allocations — those belong to {@link ReleaseProductsCommand} at confirm time.
 */
public record PrepareReleaseCommand(UUID sourceOrderId, List<ItemRelease> itemReleases) {

    public PrepareReleaseCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        if (itemReleases.isEmpty()) {
            throw new IllegalArgumentException("At least one item release is required");
        }
        itemReleases = List.copyOf(itemReleases);
    }
}
