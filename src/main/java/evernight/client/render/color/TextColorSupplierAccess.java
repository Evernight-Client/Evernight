package evernight.client.render.color;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface TextColorSupplierAccess {
    @Nullable
    Supplier getSupplier();

    void setSupplier(@Nullable Supplier supplier);
}