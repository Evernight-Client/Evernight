package evernight.mixin.minecraft.gui;

import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Pins the handshake host name so the connector never performs a reverse DNS (PTR) lookup.
 *
 * <p>{@link ConnectScreen} builds the login handshake from {@link InetSocketAddress#getHostName()}
 * inside its connector thread. When the resolved address carries no cached host name,
 * {@code getHostName()} falls back to a PTR query; on networks where that query hangs or is filtered
 * the call blocks for seconds while the TCP connection is already open, so proxies with a short login
 * timeout close the socket before the client sends anything.
 *
 * <p>The redirect replaces the resolved address with one backed by
 * {@link InetAddress#getByAddress(String, byte[])}, which stores the host literal without any name
 * service call. The redirect targets {@code Optional#get} instead of {@code getHostName} so it does
 * not compete with ViaFabricPlus' own redirect on the same invocation.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenThreadMixin {
    private static String cachedHostName(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        if (inetAddress == null) {
            return address.getHostString();
        }
        String text = inetAddress.toString();
        int separator = text.indexOf('/');
        return separator > 0 ? text.substring(0, separator) : inetAddress.getHostAddress();
    }

    private static InetSocketAddress withoutReverseLookup(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        String host = cachedHostName(address);
        if (inetAddress == null || host.isEmpty() || inetAddress.getAddress() == null) {
            return address;
        }
        try {
            InetAddress pinned = InetAddress.getByAddress(host, inetAddress.getAddress());
            return new InetSocketAddress(pinned, address.getPort());
        } catch (UnknownHostException failure) {
            return address;
        }
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/util/Optional;get()Ljava/lang/Object;"))
    private Object cacheResolvedHostName(Optional<?> resolvedAddress) {
        Object value = resolvedAddress.get();
        if (value instanceof InetSocketAddress address) {
            return withoutReverseLookup(address);
        }
        return value;
    }
}
