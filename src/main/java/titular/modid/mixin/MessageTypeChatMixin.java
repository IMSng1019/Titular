package titular.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.message.MessageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import titular.modid.format.TitularFormatter;
import titular.modid.server.TitularServerRuntime;

/** Adds a title only to the signed chat display-name parameter. */
@Mixin(MessageType.class)
public abstract class MessageTypeChatMixin {
	@Inject(method = "params(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/entity/Entity;)Lnet/minecraft/network/message/MessageType$Parameters;",
		at = @At("RETURN"), cancellable = true)
	private static void titular$formatChatName(RegistryKey<MessageType> type, Entity sender,
			CallbackInfoReturnable<MessageType.Parameters> callback) {
		if (!MessageType.CHAT.equals(type) || !(sender instanceof ServerPlayerEntity player)) return;
		TitularServerRuntime runtime = TitularServerRuntime.active();
		if (runtime == null || runtime.service() == null) return;
		MessageType.Parameters parameters = callback.getReturnValue();
		Text formatted = TitularFormatter.format(parameters.name(),
			runtime.service().resolveActiveTitle(player.getUuid()).orElse(null),
			runtime.service().data().settings().displayMode());
		callback.setReturnValue(new MessageType.Parameters(parameters.type(), formatted, parameters.targetName()));
	}
}
