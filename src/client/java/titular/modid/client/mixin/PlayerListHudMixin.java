package titular.modid.client.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import titular.modid.format.DisplayAdapters;

/** Applies the shared formatter to Tab-list player names. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
	private void titular$formatTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> callback) {
		if (entry != null && entry.getProfile() != null) {
			Text raw = callback.getReturnValue();
			if (raw != null) callback.setReturnValue(DisplayAdapters.formatTabName(raw, entry.getProfile().getId()));
		}
	}
}
